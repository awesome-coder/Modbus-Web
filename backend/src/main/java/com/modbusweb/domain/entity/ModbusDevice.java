package com.modbusweb.domain.entity;

import com.modbusweb.domain.ProtocolType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "modbus_device",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_name", columnNames = "name"))
public class ModbusDevice extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProtocolType protocol = ProtocolType.MODBUS_TCP;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private int port = 502;

    @Column(name = "unit_id", nullable = false)
    private int unitId = 1;

    @Column(name = "connect_timeout_ms", nullable = false)
    private int connectTimeoutMs = 3000;

    @Column(name = "request_timeout_ms", nullable = false)
    private int requestTimeoutMs = 3000;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "tls_client_cert_path", length = 1024)
    private String tlsClientCertPath;

    @Column(name = "tls_client_key_path", length = 1024)
    private String tlsClientKeyPath;

    @Column(name = "tls_key_password", length = 512)
    private String tlsKeyPassword;

    @Column(name = "tls_ca_cert_path", length = 1024)
    private String tlsCaCertPath;

    @Column(name = "tls_verify_hostname", nullable = false)
    private boolean tlsVerifyHostname = true;

    @Column(length = 1000)
    private String description;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ModbusPoint> points = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProtocolType getProtocol() {
        return protocol;
    }

    public void setProtocol(ProtocolType protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getUnitId() {
        return unitId;
    }

    public void setUnitId(int unitId) {
        this.unitId = unitId;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTlsClientCertPath() {
        return tlsClientCertPath;
    }

    public void setTlsClientCertPath(String tlsClientCertPath) {
        this.tlsClientCertPath = tlsClientCertPath;
    }

    public String getTlsClientKeyPath() {
        return tlsClientKeyPath;
    }

    public void setTlsClientKeyPath(String tlsClientKeyPath) {
        this.tlsClientKeyPath = tlsClientKeyPath;
    }

    public String getTlsKeyPassword() {
        return tlsKeyPassword;
    }

    public void setTlsKeyPassword(String tlsKeyPassword) {
        this.tlsKeyPassword = tlsKeyPassword;
    }

    public String getTlsCaCertPath() {
        return tlsCaCertPath;
    }

    public void setTlsCaCertPath(String tlsCaCertPath) {
        this.tlsCaCertPath = tlsCaCertPath;
    }

    public boolean isTlsVerifyHostname() {
        return tlsVerifyHostname;
    }

    public void setTlsVerifyHostname(boolean tlsVerifyHostname) {
        this.tlsVerifyHostname = tlsVerifyHostname;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ModbusPoint> getPoints() {
        return points;
    }
}
