package com.modbusweb.domain.entity;

import com.modbusweb.domain.ByteOrder;
import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointAccess;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.StringCharset;
import com.modbusweb.domain.WordOrder;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "modbus_point",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_point_device_code",
                        columnNames = {"device_id", "code"}),
        indexes = {
            @Index(name = "idx_point_device", columnList = "device_id"),
            @Index(name = "idx_point_device_area_address", columnList = "device_id,area,address")
        })
public class ModbusPoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private ModbusDevice device;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PointArea area;

    /** Zero-based Modbus protocol address (0 .. 65535). */
    @Column(nullable = false)
    private int address;

    /** Optional per-point unit id override. */
    @Column(name = "unit_id")
    private Integer unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 24)
    private ModbusDataType dataType = ModbusDataType.UINT16;

    @Enumerated(EnumType.STRING)
    @Column(name = "byte_order", nullable = false, length = 24)
    private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;

    @Enumerated(EnumType.STRING)
    @Column(name = "word_order", nullable = false, length = 24)
    private WordOrder wordOrder = WordOrder.BIG_ENDIAN;

    @Column(name = "bit_index")
    private Integer bitIndex;

    /** Number of registers for STRING / HEX points. */
    @Column(name = "register_length")
    private Integer registerLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "string_charset", nullable = false, length = 16)
    private StringCharset stringCharset = StringCharset.UTF_8;

    @Column(name = "scale_factor", nullable = false)
    private double scale = 1.0;

    @Column(name = "offset_value", nullable = false)
    private double offset = 0.0;

    private Integer decimals;

    @Column(name = "unit_label", length = 32)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_mode", nullable = false, length = 24)
    private PointAccess access = PointAccess.READ_ONLY;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 1000)
    private String description;

    @OneToOne(mappedBy = "point", cascade = CascadeType.ALL, orphanRemoval = true)
    private BacnetMapping bacnetMapping;

    public ModbusDevice getDevice() {
        return device;
    }

    public void setDevice(ModbusDevice device) {
        this.device = device;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PointArea getArea() {
        return area;
    }

    public void setArea(PointArea area) {
        this.area = area;
    }

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public Integer getUnitId() {
        return unitId;
    }

    public void setUnitId(Integer unitId) {
        this.unitId = unitId;
    }

    public int effectiveUnitId() {
        return unitId != null ? unitId : device.getUnitId();
    }

    public ModbusDataType getDataType() {
        return dataType;
    }

    public void setDataType(ModbusDataType dataType) {
        this.dataType = dataType;
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public void setByteOrder(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    public WordOrder getWordOrder() {
        return wordOrder;
    }

    public void setWordOrder(WordOrder wordOrder) {
        this.wordOrder = wordOrder;
    }

    public Integer getBitIndex() {
        return bitIndex;
    }

    public void setBitIndex(Integer bitIndex) {
        this.bitIndex = bitIndex;
    }

    public Integer getRegisterLength() {
        return registerLength;
    }

    public void setRegisterLength(Integer registerLength) {
        this.registerLength = registerLength;
    }

    public StringCharset getStringCharset() {
        return stringCharset;
    }

    public void setStringCharset(StringCharset stringCharset) {
        this.stringCharset = stringCharset;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public double getOffset() {
        return offset;
    }

    public void setOffset(double offset) {
        this.offset = offset;
    }

    public Integer getDecimals() {
        return decimals;
    }

    public void setDecimals(Integer decimals) {
        this.decimals = decimals;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public PointAccess getAccess() {
        return access;
    }

    public void setAccess(PointAccess access) {
        this.access = access;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BacnetMapping getBacnetMapping() {
        return bacnetMapping;
    }

    public void setBacnetMapping(BacnetMapping bacnetMapping) {
        this.bacnetMapping = bacnetMapping;
    }

    /** Number of 16-bit registers occupied by this point. Coils always occupy one bit. */
    public int registerCount() {
        if (!area.isRegister()) {
            return 1;
        }
        if (dataType.isVariableLength()) {
            return registerLength == null ? 1 : registerLength;
        }
        return Math.max(1, dataType.getFixedRegisterCount());
    }

    public int byteCount() {
        return registerCount() * 2;
    }

    public String addressReference() {
        return area.toReference(address);
    }

    public boolean supportsRead() {
        return enabled && access.canRead();
    }

    public boolean supportsWrite() {
        return enabled && access.canWrite() && area.isWritable();
    }
}
