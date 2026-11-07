package com.modbusweb.service;

import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.tcp.client.NettyClientTransportConfig;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.modbusweb.domain.ProtocolType;
import com.modbusweb.domain.entity.ModbusDevice;
import com.modbusweb.web.dto.ConnectionTestResponse;
import com.modbusweb.web.dto.DeviceStatusResponse;
import com.modbusweb.web.ModbusOperationException;
import io.netty.handler.ssl.SslHandler;
import jakarta.annotation.PreDestroy;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Owns one digitalpetri {@link ModbusTcpClient} per configured device.
 *
 * <p>Clients are created lazily, kept connected between web requests, and invalidated whenever a
 * device configuration changes. Modbus TCP Security connections use mutual TLS as required by the
 * Modbus Security protocol; certificates are loaded from PEM files.
 */
@Service
public class ModbusClientManager {

    private static final Logger log = LoggerFactory.getLogger(ModbusClientManager.class);
    private static final Duration MIN_TIMEOUT = Duration.ofMillis(100);

    private final Map<Long, ClientHandle> clients = new ConcurrentHashMap<>();
    private final Map<Long, Diagnostics> diagnostics = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();
    private final TlsMaterialLoader tlsMaterialLoader;

    public ModbusClientManager(TlsMaterialLoader tlsMaterialLoader) {
        this.tlsMaterialLoader = tlsMaterialLoader;
    }

    public <T> T execute(ModbusDevice device, CheckedOperation<T> operation) {
        Objects.requireNonNull(device, "device");
        if (!device.isEnabled()) {
            throw new IllegalStateException("设备 [" + device.getName() + "] 已禁用");
        }
        Diagnostics diag = diagnostics.computeIfAbsent(device.getId(), ignored -> new Diagnostics());
        ClientHandle handle;
        try {
            handle = getOrConnect(device);
        } catch (RuntimeException e) {
            diag.lastError = messageOf(e);
            throw e;
        }
        diag.requests.incrementAndGet();
        try {
            return operation.apply(handle.client);
        } catch (RuntimeException e) {
            diag.errors.incrementAndGet();
            diag.lastError = messageOf(e);
            if (!handle.client.isConnected()) {
                invalidate(device.getId());
            }
            throw e;
        } catch (Exception e) {
            diag.errors.incrementAndGet();
            diag.lastError = messageOf(e);
            if (!handle.client.isConnected()) {
                invalidate(device.getId());
            }
            throw new ModbusOperationException(messageOf(e), e);
        }
    }

    @FunctionalInterface
    public interface CheckedOperation<T> {
        T apply(ModbusTcpClient client) throws Exception;
    }

    public ClientHandle getOrConnect(ModbusDevice device) {
        ClientHandle existing = clients.get(device.getId());
        String fingerprint = fingerprint(device);
        if (existing != null && existing.fingerprint.equals(fingerprint) && existing.client.isConnected()) {
            return existing;
        }

        Object lock = locks.computeIfAbsent(device.getId(), ignored -> new Object());
        synchronized (lock) {
            existing = clients.get(device.getId());
            if (existing != null
                    && existing.fingerprint.equals(fingerprint)
                    && existing.client.isConnected()) {
                return existing;
            }
            if (existing != null) {
                closeQuietly(existing);
                clients.remove(device.getId());
            }

            Diagnostics diag =
                    diagnostics.computeIfAbsent(device.getId(), ignored -> new Diagnostics());
            long start = System.nanoTime();
            ClientHandle created = null;
            try {
                created = create(device, fingerprint);
                created.client.connect();
                created.connectedAt = Instant.now();
                clients.put(device.getId(), created);
                diag.lastError = null;
                if (diag.connections.incrementAndGet() > 1) {
                    diag.reconnects.incrementAndGet();
                }
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                log.info(
                        "Connected to Modbus device '{}' at {}:{} ({}) in {} ms",
                        device.getName(),
                        device.getHost(),
                        device.getPort(),
                        device.getProtocol(),
                        elapsedMs);
                return created;
            } catch (Exception e) {
                if (created != null) {
                    closeQuietly(created);
                }
                diag.lastError = messageOf(e);
                diag.errors.incrementAndGet();
                throw new IllegalStateException(
                        "连接设备 [" + device.getName() + "] 失败: " + messageOf(e), e);
            }
        }
    }

    public void connect(ModbusDevice device) {
        getOrConnect(device);
    }

    public void disconnect(Long deviceId) {
        ClientHandle handle = clients.remove(deviceId);
        if (handle != null) {
            closeQuietly(handle);
        }
    }

    public void invalidate(Long deviceId) {
        disconnect(deviceId);
        locks.remove(deviceId);
    }

    public void forget(Long deviceId) {
        disconnect(deviceId);
        diagnostics.remove(deviceId);
        locks.remove(deviceId);
    }

    public ConnectionTestResponse testConnection(ModbusDevice device) {
        long start = System.nanoTime();
        ClientHandle handle = null;
        try {
            handle = create(device, fingerprint(device));
            handle.client.connect();
            long latency = (System.nanoTime() - start) / 1_000_000;
            return new ConnectionTestResponse(
                    true,
                    "连接成功"
                            + (device.getProtocol().isSecure() ? "（双向 TLS 握手完成）" : ""),
                    latency,
                    device.getProtocol().isSecure(),
                    device.getHost(),
                    device.getPort());
        } catch (Exception e) {
            long latency = (System.nanoTime() - start) / 1_000_000;
            return new ConnectionTestResponse(
                    false,
                    "连接失败: " + messageOf(e),
                    latency,
                    device.getProtocol().isSecure(),
                    device.getHost(),
                    device.getPort());
        } finally {
            if (handle != null) {
                closeQuietly(handle);
            }
        }
    }

    public DeviceStatusResponse status(ModbusDevice device) {
        ClientHandle handle = clients.get(device.getId());
        Diagnostics diag = diagnostics.get(device.getId());
        boolean connected = handle != null && handle.client.isConnected();
        return new DeviceStatusResponse(
                device.getId(),
                device.getName(),
                device.getProtocol(),
                connected,
                connected ? handle.connectedAt : null,
                diag == null ? null : diag.lastError,
                diag == null ? 0 : diag.reconnects.get(),
                diag == null ? 0 : diag.requests.get(),
                diag == null ? 0 : diag.errors.get());
    }

    public List<DeviceStatusResponse> statuses(List<ModbusDevice> devices) {
        return devices.stream().map(this::status).toList();
    }

    public boolean isConnected(Long deviceId) {
        ClientHandle handle = clients.get(deviceId);
        return handle != null && handle.client.isConnected();
    }

    private ClientHandle create(ModbusDevice device, String fingerprint) throws Exception {
        boolean secure = device.getProtocol() == ProtocolType.MODBUS_TCP_SECURE;
        final TlsMaterialLoader.TlsMaterial tls = secure ? tlsMaterialLoader.load(device) : null;

        NettyTcpClientTransport transport =
                NettyTcpClientTransport.create(
                        builder -> {
                            builder
                                    .setHostname(device.getHost())
                                    .setPort(device.getPort())
                                    .setConnectTimeout(timeout(device.getConnectTimeoutMs()))
                                    .setConnectPersistent(true)
                                    .setReconnectLazy(false)
                                    .setTlsEnabled(secure);
                            if (secure) {
                                builder
                                        .setKeyManagerFactory(tls.keyManagerFactory())
                                        .setTrustManagerFactory(tls.trustManagerFactory())
                                        .setPipelineCustomizer(
                                                pipeline -> {
                                                    if (!device.isTlsVerifyHostname()) {
                                                        return;
                                                    }
                                                    SslHandler ssl = pipeline.get(SslHandler.class);
                                                    if (ssl != null) {
                                                        SSLParameters parameters =
                                                                ssl.engine().getSSLParameters();
                                                        parameters.setEndpointIdentificationAlgorithm(
                                                                "HTTPS");
                                                        ssl.engine().setSSLParameters(parameters);
                                                    }
                                                });
                            }
                        });

        ModbusTcpClient client =
                ModbusTcpClient.create(
                        transport,
                        config ->
                                config.setRequestTimeout(
                                        timeout(device.getRequestTimeoutMs())));

        return new ClientHandle(
                fingerprint, transport, client, device.getProtocol(), device.getHost(), device.getPort());
    }

    private static Duration timeout(int millis) {
        return Duration.ofMillis(Math.max(MIN_TIMEOUT.toMillis(), millis));
    }

    private static String fingerprint(ModbusDevice device) {
        String passwordHash =
                device.getTlsKeyPassword() == null
                        ? ""
                        : Integer.toHexString(device.getTlsKeyPassword().hashCode());
        return String.join(
                "|",
                String.valueOf(device.getProtocol()),
                device.getHost(),
                String.valueOf(device.getPort()),
                String.valueOf(device.getConnectTimeoutMs()),
                String.valueOf(device.getRequestTimeoutMs()),
                nullSafe(device.getTlsClientCertPath()),
                nullSafe(device.getTlsClientKeyPath()),
                nullSafe(device.getTlsCaCertPath()),
                passwordHash,
                String.valueOf(device.isTlsVerifyHostname()));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private void closeQuietly(ClientHandle handle) {
        try {
            if (handle.client.isConnected()) {
                handle.client.disconnect();
            }
        } catch (Exception e) {
            log.debug("Error disconnecting Modbus client: {}", e.getMessage());
        }
    }

    private static String messageOf(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            if (current instanceof CertificateException) {
                break;
            }
            current = current.getCause();
        }
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    @PreDestroy
    public void shutdown() {
        clients.values().forEach(this::closeQuietly);
        clients.clear();
    }

    public static final class ClientHandle {
        private final String fingerprint;
        @SuppressWarnings("unused")
        private final NettyTcpClientTransport transport;

        private final ModbusTcpClient client;
        @SuppressWarnings("unused")
        private final ProtocolType protocol;
        @SuppressWarnings("unused")
        private final String host;
        @SuppressWarnings("unused")
        private final int port;
        private volatile Instant connectedAt;

        private ClientHandle(
                String fingerprint,
                NettyTcpClientTransport transport,
                ModbusTcpClient client,
                ProtocolType protocol,
                String host,
                int port) {
            this.fingerprint = fingerprint;
            this.transport = transport;
            this.client = client;
            this.protocol = protocol;
            this.host = host;
            this.port = port;
        }

        public ModbusTcpClient client() {
            return client;
        }

        public Instant connectedAt() {
            return connectedAt;
        }
    }

    private static final class Diagnostics {
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private final AtomicLong connections = new AtomicLong();
        private final AtomicLong reconnects = new AtomicLong();
        private volatile String lastError;
    }
}
