package com.modbusweb.domain.entity;

import com.modbusweb.domain.BacnetObjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "bacnet_mapping",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_bacnet_point", columnNames = "point_id"),
            @UniqueConstraint(
                    name = "uk_bacnet_object",
                    columnNames = {"object_type", "object_instance"})
        })
public class BacnetMapping extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_id", nullable = false)
    private ModbusPoint point;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 32)
    private BacnetObjectType objectType;

    @Column(name = "object_instance", nullable = false)
    private int objectInstance;

    @Column(name = "object_name", nullable = false, length = 160)
    private String objectName;

    @Column(length = 64)
    private String units;

    @Column(name = "cov_increment")
    private Double covIncrement;

    @Column(length = 1000)
    private String description;

    public ModbusPoint getPoint() {
        return point;
    }

    public void setPoint(ModbusPoint point) {
        this.point = point;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BacnetObjectType getObjectType() {
        return objectType;
    }

    public void setObjectType(BacnetObjectType objectType) {
        this.objectType = objectType;
    }

    public int getObjectInstance() {
        return objectInstance;
    }

    public void setObjectInstance(int objectInstance) {
        this.objectInstance = objectInstance;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public Double getCovIncrement() {
        return covIncrement;
    }

    public void setCovIncrement(Double covIncrement) {
        this.covIncrement = covIncrement;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String objectId() {
        return objectType.getObjectId(objectInstance);
    }
}
