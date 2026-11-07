package com.modbusweb.service;

import com.modbusweb.domain.ProtocolType;
import com.modbusweb.domain.entity.ModbusDevice;
import com.modbusweb.repository.ModbusDeviceRepository;
import com.modbusweb.repository.ModbusPointRepository;
import com.modbusweb.web.BadRequestException;
import com.modbusweb.web.ResourceNotFoundException;
import com.modbusweb.web.dto.ConnectionTestResponse;
import com.modbusweb.web.dto.DeviceRequest;
import com.modbusweb.web.dto.DeviceResponse;
import com.modbusweb.web.dto.DeviceStatusResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DeviceService {

    private final ModbusDeviceRepository deviceRepository;
    private final ModbusPointRepository pointRepository;
    private final ModbusClientManager clientManager;

    public DeviceService(
            ModbusDeviceRepository deviceRepository,
            ModbusPointRepository pointRepository,
            ModbusClientManager clientManager) {
        this.deviceRepository = deviceRepository;
        this.pointRepository = pointRepository;
        this.clientManager = clientManager;
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list() {
        return deviceRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public ModbusDevice require(Long id) {
        return deviceRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("设备不存在: " + id));
    }

    @Transactional
    public DeviceResponse create(DeviceRequest request) {
        validateForImport(request, null);
        ModbusDevice device = new ModbusDevice();
        apply(device, request, true);
        device = deviceRepository.save(device);
        return toResponse(device);
    }

    @Transactional
    public DeviceResponse update(Long id, DeviceRequest request) {
        ModbusDevice device = require(id);
        validateForImport(request, id);
        apply(device, request, false);
        device = deviceRepository.save(device);
        clientManager.invalidate(id);
        return toResponse(device);
    }

    /** Validate a device request without writing it. Used by Excel import for all-or-nothing checks. */
    @Transactional(readOnly = true)
    public void validateForImport(DeviceRequest request, Long existingId) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("设备名称不能为空");
        }
        boolean duplicate =
                existingId == null
                        ? deviceRepository.existsByNameIgnoreCase(name)
                        : deviceRepository.existsByNameIgnoreCaseAndIdNot(name, existingId);
        if (duplicate) {
            throw new BadRequestException("设备名称已存在: " + name);
        }
        ModbusDevice probe = new ModbusDevice();
        apply(probe, request, existingId == null);
    }

    @Transactional
    public void delete(Long id) {
        ModbusDevice device = require(id);
        clientManager.forget(id);
        deviceRepository.delete(device);
    }

    public ConnectionTestResponse test(Long id) {
        return clientManager.testConnection(require(id));
    }

    public void connect(Long id) {
        try {
            clientManager.connect(require(id));
        } catch (RuntimeException e) {
            throw new com.modbusweb.web.ModbusOperationException(e.getMessage(), e);
        }
    }

    public void disconnect(Long id) {
        require(id);
        clientManager.disconnect(id);
    }

    @Transactional(readOnly = true)
    public List<DeviceStatusResponse> statuses() {
        return clientManager.statuses(deviceRepository.findAllByOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public DeviceStatusResponse status(Long id) {
        return clientManager.status(require(id));
    }

    private void apply(ModbusDevice device, DeviceRequest request, boolean creating) {
        String name = request.name().trim();
        String host = request.host().trim();
        if (name.isEmpty() || host.isEmpty()) {
            throw new BadRequestException("设备名称和主机不能为空");
        }
        ProtocolType protocol = request.protocol();
        device.setName(name);
        device.setProtocol(protocol);
        device.setHost(host);
        device.setPort(request.port());
        device.setUnitId(request.unitId());
        device.setConnectTimeoutMs(
                request.connectTimeoutMs() == null ? 3000 : request.connectTimeoutMs());
        device.setRequestTimeoutMs(
                request.requestTimeoutMs() == null ? 3000 : request.requestTimeoutMs());
        device.setEnabled(request.enabled() == null || request.enabled());
        device.setDescription(trimToNull(request.description()));
        device.setTlsVerifyHostname(
                request.tlsVerifyHostname() == null || request.tlsVerifyHostname());

        device.setTlsClientCertPath(trimToNull(request.tlsClientCertPath()));
        device.setTlsClientKeyPath(trimToNull(request.tlsClientKeyPath()));
        device.setTlsCaCertPath(trimToNull(request.tlsCaCertPath()));
        String password = request.tlsKeyPassword();
        if (StringUtils.hasText(password)) {
            device.setTlsKeyPassword(password);
        } else if (creating) {
            device.setTlsKeyPassword(null);
        }

        if (protocol == ProtocolType.MODBUS_TCP_SECURE) {
            requireReadable(device.getTlsClientCertPath(), "tlsClientCertPath", "TLS 客户端证书");
            requireReadable(device.getTlsClientKeyPath(), "tlsClientKeyPath", "TLS 客户端私钥");
            requireReadable(device.getTlsCaCertPath(), "tlsCaCertPath", "TLS CA 证书");
        }
    }

    private static void requireReadable(String path, String field, String label) {
        if (!StringUtils.hasText(path)) {
            throw BadRequestException.withField(field, label + "路径不能为空");
        }
        Path file = Path.of(path);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw BadRequestException.withField(field, label + "文件不存在或不可读: " + file);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DeviceResponse toResponse(ModbusDevice device) {
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getProtocol(),
                device.getHost(),
                device.getPort(),
                device.getUnitId(),
                device.getConnectTimeoutMs(),
                device.getRequestTimeoutMs(),
                device.isEnabled(),
                device.getTlsClientCertPath(),
                device.getTlsClientKeyPath(),
                StringUtils.hasText(device.getTlsKeyPassword()),
                device.getTlsCaCertPath(),
                device.isTlsVerifyHostname(),
                device.getDescription(),
                pointRepository.countByDeviceId(device.getId()),
                device.getCreatedAt(),
                device.getUpdatedAt(),
                device.getVersion());
    }
}
