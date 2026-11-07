package com.modbusweb.service;

import com.digitalpetri.modbus.tcp.security.SecurityUtil;
import com.modbusweb.domain.entity.ModbusDevice;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.springframework.stereotype.Component;

/**
 * Loads PEM certificates and private keys for Modbus/TCP Security (mutual TLS).
 *
 * <p>Supported private key formats: PKCS#8 ({@code BEGIN PRIVATE KEY}), encrypted PKCS#8 ({@code
 * BEGIN ENCRYPTED PRIVATE KEY}), PKCS#1 RSA/EC ({@code BEGIN RSA PRIVATE KEY}, {@code BEGIN EC
 * PRIVATE KEY}) and encrypted traditional PEM key pairs.
 */
@Component
public class TlsMaterialLoader {

    private static final Provider BC = new BouncyCastleProvider();

    public record TlsMaterial(KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory) {}

    public TlsMaterial load(ModbusDevice device) throws IOException, GeneralSecurityException {
        Path certPath = requireFile(device.getTlsClientCertPath(), "TLS 客户端证书");
        Path keyPath = requireFile(device.getTlsClientKeyPath(), "TLS 客户端私钥");
        Path caPath = requireFile(device.getTlsCaCertPath(), "TLS CA 证书");

        List<X509Certificate> clientCertificates = readCertificates(certPath);
        if (clientCertificates.isEmpty()) {
            throw new GeneralSecurityException("TLS 客户端证书文件中没有证书: " + certPath);
        }
        List<X509Certificate> caCertificates = readCertificates(caPath);
        if (caCertificates.isEmpty()) {
            throw new GeneralSecurityException("TLS CA 证书文件中没有证书: " + caPath);
        }

        PrivateKey privateKey = readPrivateKey(keyPath, device.getTlsKeyPassword());
        KeyManagerFactory kmf =
                SecurityUtil.createKeyManagerFactory(
                        privateKey, clientCertificates.toArray(X509Certificate[]::new));
        TrustManagerFactory tmf =
                SecurityUtil.createTrustManagerFactory(caCertificates.toArray(X509Certificate[]::new));
        return new TlsMaterial(kmf, tmf);
    }

    private Path requireFile(String path, String label) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException(label + "路径不能为空（Modbus TCP Security 使用双向 TLS）");
        }
        Path file = Path.of(path.trim());
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IOException(label + "不存在或不可读: " + file.toAbsolutePath());
        }
        return file;
    }

    private List<X509Certificate> readCertificates(Path path)
            throws IOException, GeneralSecurityException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();
        try (var in = Files.newInputStream(path)) {
            Collection<? extends Certificate> parsed = factory.generateCertificates(in);
            for (Certificate certificate : parsed) {
                if (certificate instanceof X509Certificate x509) {
                    certificates.add(x509);
                }
            }
        }
        return certificates;
    }

    private PrivateKey readPrivateKey(Path path, String password)
            throws IOException, GeneralSecurityException {
        char[] secret = password == null || password.isEmpty() ? null : password.toCharArray();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII);
                PEMParser parser = new PEMParser(reader)) {
            Object object = parser.readObject();
            if (object == null) {
                throw new GeneralSecurityException("无法识别私钥文件: " + path);
            }
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BC);

            if (object instanceof PEMKeyPair keyPair) {
                return converter.getKeyPair(keyPair).getPrivate();
            }
            if (object instanceof PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }
            if (object instanceof PKCS8EncryptedPrivateKeyInfo encrypted) {
                if (secret == null || secret.length == 0) {
                    throw new GeneralSecurityException("私钥已加密，请填写私钥密码");
                }
                try {
                    var decryptor =
                            new JceOpenSSLPKCS8DecryptorProviderBuilder().setProvider(BC).build(secret);
                    return converter.getPrivateKey(encrypted.decryptPrivateKeyInfo(decryptor));
                } catch (org.bouncycastle.operator.OperatorCreationException
                        | org.bouncycastle.pkcs.PKCSException e) {
                    throw new GeneralSecurityException("解密 PKCS#8 私钥失败，请检查私钥密码", e);
                }
            }
            if (object instanceof PEMEncryptedKeyPair encryptedKeyPair) {
                if (secret == null || secret.length == 0) {
                    throw new GeneralSecurityException("私钥已加密，请填写私钥密码");
                }
                PEMDecryptorProvider decryptor =
                        new JcePEMDecryptorProviderBuilder().setProvider(BC).build(secret);
                return converter
                        .getKeyPair(encryptedKeyPair.decryptKeyPair(decryptor))
                        .getPrivate();
            }
            if (object instanceof X509CertificateHolder) {
                throw new GeneralSecurityException("私钥文件中只包含证书，请选择正确的私钥文件");
            }
            throw new GeneralSecurityException(
                    "不支持的私钥格式: " + object.getClass().getSimpleName()
                            + "（支持 PKCS#8 / PKCS#1 / EC PEM）");
        }
    }
}
