package com.modbusweb.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadOnlyModbusServices;
import com.digitalpetri.modbus.tcp.security.SecurityUtil;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.modbusweb.domain.ProtocolType;
import com.modbusweb.domain.entity.ModbusDevice;
import com.modbusweb.web.dto.ConnectionTestResponse;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsModbusIntegrationTest {

    private static final BouncyCastleProvider BC = new BouncyCastleProvider();

    @TempDir Path tempDir;

    @Test
    void connectsToMutualTlsModbusServerAndVerifiesHostname() throws Exception {
        Certificates certs = createCertificates(tempDir);
        int port = freePort();
        ProcessImage image = new ProcessImage();
        image.with(true, tx -> tx.writeHoldingRegisters(map -> map.put(0, new byte[] {0x12, 0x34})));

        var services =
                new ReadOnlyModbusServices() {
                    @Override
                    protected Optional<ProcessImage> getProcessImage(int unitId) {
                        return unitId == 1 ? Optional.of(image) : Optional.empty();
                    }
                };
        var serverKeyManagerFactory =
                SecurityUtil.createKeyManagerFactory(
                        certs.serverKey(), certs.serverCert(), certs.caCert());
        var serverTrustManagerFactory =
                SecurityUtil.createTrustManagerFactory(certs.caCert());
        NettyTcpServerTransport transport =
                NettyTcpServerTransport.create(
                        builder ->
                                builder
                                        .setBindAddress("127.0.0.1")
                                        .setPort(port)
                                        .setTlsEnabled(true)
                                        .setKeyManagerFactory(serverKeyManagerFactory)
                                        .setTrustManagerFactory(serverTrustManagerFactory));
        ModbusTcpServer server = ModbusTcpServer.create(transport, services);
        server.start();
        try {
            ModbusDevice device = new ModbusDevice();
            device.setName("TLS test device");
            device.setProtocol(ProtocolType.MODBUS_TCP_SECURE);
            device.setHost("127.0.0.1");
            device.setPort(port);
            device.setUnitId(1);
            device.setConnectTimeoutMs(3000);
            device.setRequestTimeoutMs(3000);
            device.setEnabled(true);
            device.setTlsClientCertPath(certs.clientCertPath().toString());
            device.setTlsClientKeyPath(certs.clientKeyPath().toString());
            device.setTlsCaCertPath(certs.caCertPath().toString());
            device.setTlsVerifyHostname(true);

            ModbusClientManager manager = new ModbusClientManager(new TlsMaterialLoader());
            ConnectionTestResponse result = manager.testConnection(device);

            assertThat(result.success()).as(result.message()).isTrue();
            assertThat(result.secure()).isTrue();
            assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
            manager.shutdown();
        } finally {
            server.stop();
        }
    }

    private Certificates createCertificates(Path directory) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        KeyPair caPair = generator.generateKeyPair();
        KeyPair serverPair = generator.generateKeyPair();
        KeyPair clientPair = generator.generateKeyPair();

        X500Name caName = new X500Name("CN=Modbus Test CA");
        X509Certificate caCert =
                certificate(
                        caName,
                        caName,
                        caPair.getPublic(),
                        caPair.getPrivate(),
                        true,
                        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign),
                        null,
                        null);
        X509Certificate serverCert =
                certificate(
                        caName,
                        new X500Name("CN=127.0.0.1"),
                        serverPair.getPublic(),
                        caPair.getPrivate(),
                        false,
                        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment),
                        KeyPurposeId.id_kp_serverAuth,
                        new GeneralNames(
                                new GeneralName[] {
                                    new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                                    new GeneralName(GeneralName.dNSName, "localhost")
                                }));
        X509Certificate clientCert =
                certificate(
                        caName,
                        new X500Name("CN=modbus-web-client"),
                        clientPair.getPublic(),
                        caPair.getPrivate(),
                        false,
                        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment),
                        KeyPurposeId.id_kp_clientAuth,
                        null);

        Path caCertPath = directory.resolve("ca.crt");
        Path serverCertPath = directory.resolve("server.crt");
        Path serverKeyPath = directory.resolve("server.key");
        Path clientCertPath = directory.resolve("client.crt");
        Path clientKeyPath = directory.resolve("client.key");
        Files.writeString(caCertPath, pem(caCert), StandardCharsets.US_ASCII);
        Files.writeString(serverCertPath, pem(serverCert), StandardCharsets.US_ASCII);
        Files.writeString(clientCertPath, pem(clientCert), StandardCharsets.US_ASCII);
        writePrivateKey(serverKeyPath, serverPair);
        writePrivateKey(clientKeyPath, clientPair);

        return new Certificates(
                caCert,
                serverCert,
                serverPair.getPrivate(),
                clientCertPath,
                clientKeyPath,
                caCertPath);
    }

    private X509Certificate certificate(
            X500Name issuer,
            X500Name subject,
            java.security.PublicKey publicKey,
            java.security.PrivateKey signingKey,
            boolean ca,
            KeyUsage keyUsage,
            KeyPurposeId purpose,
            GeneralNames subjectAlternativeNames)
            throws Exception {
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        issuer,
                        new BigInteger(64, new SecureRandom()).abs(),
                        Date.from(now.minus(1, ChronoUnit.DAYS)),
                        Date.from(now.plus(365, ChronoUnit.DAYS)),
                        subject,
                        publicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(Extension.keyUsage, true, keyUsage);
        if (purpose != null) {
            builder.addExtension(
                    Extension.extendedKeyUsage, false, new ExtendedKeyUsage(purpose));
        }
        if (subjectAlternativeNames != null) {
            builder.addExtension(
                    Extension.subjectAlternativeName, false, subjectAlternativeNames);
        }
        var signer =
                new JcaContentSignerBuilder("SHA256withRSA").setProvider(BC).build(signingKey);
        return new JcaX509CertificateConverter().setProvider(BC).getCertificate(builder.build(signer));
    }

    private static String pem(X509Certificate certificate) throws Exception {
        String base64 =
                Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n"
                + base64
                + "\n-----END CERTIFICATE-----\n";
    }

    private static void writePrivateKey(Path path, KeyPair keyPair) throws Exception {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter writer = new PemWriter(stringWriter)) {
            writer.writeObject(new JcaPKCS8Generator(keyPair.getPrivate(), null));
        }
        Files.writeString(path, stringWriter.toString(), StandardCharsets.US_ASCII);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record Certificates(
            X509Certificate caCert,
            X509Certificate serverCert,
            java.security.PrivateKey serverKey,
            Path clientCertPath,
            Path clientKeyPath,
            Path caCertPath) {}
}
