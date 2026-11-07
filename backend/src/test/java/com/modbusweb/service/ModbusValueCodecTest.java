package com.modbusweb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.modbusweb.domain.ByteOrder;
import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointAccess;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.StringCharset;
import com.modbusweb.domain.WordOrder;
import com.modbusweb.domain.entity.ModbusPoint;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ModbusValueCodecTest {

    @Test
    void decodesFloat32InAllFourLayouts() {
        assertThat(decodeFloat(point("BIG_ENDIAN", "BIG_ENDIAN"), bytes(0x3F, 0x80, 0x00, 0x00)))
                .isEqualTo("1");
        assertThat(decodeFloat(point("LITTLE_ENDIAN", "BIG_ENDIAN"), bytes(0x80, 0x3F, 0x00, 0x00)))
                .isEqualTo("1");
        assertThat(decodeFloat(point("BIG_ENDIAN", "LITTLE_ENDIAN"), bytes(0x00, 0x00, 0x3F, 0x80)))
                .isEqualTo("1");
        assertThat(decodeFloat(point("LITTLE_ENDIAN", "LITTLE_ENDIAN"), bytes(0x00, 0x00, 0x80, 0x3F)))
                .isEqualTo("1");
    }

    @Test
    void encodesFloat32InAllFourLayouts() {
        assertThat(encodeFloat(point("BIG_ENDIAN", "BIG_ENDIAN"), "1.0"))
                .containsExactly(0x3F, 0x80, 0x00, 0x00);
        assertThat(encodeFloat(point("LITTLE_ENDIAN", "BIG_ENDIAN"), "1.0"))
                .containsExactly(0x80, 0x3F, 0x00, 0x00);
        assertThat(encodeFloat(point("BIG_ENDIAN", "LITTLE_ENDIAN"), "1.0"))
                .containsExactly(0x00, 0x00, 0x3F, 0x80);
        assertThat(encodeFloat(point("LITTLE_ENDIAN", "LITTLE_ENDIAN"), "1.0"))
                .containsExactly(0x00, 0x00, 0x80, 0x3F);
    }

    @Test
    void appliesScaleAndOffsetForReadAndWrite() {
        ModbusPoint point = point("BIG_ENDIAN", "BIG_ENDIAN");
        point.setDataType(ModbusDataType.UINT16);
        point.setArea(PointArea.HOLDING_REGISTER);
        point.setScale(0.1);
        point.setOffset(5.0);

        var decoded = ModbusValueCodec.decodeRegisters(point, bytes(0x00, 0xEA));
        assertThat(decoded.text()).isEqualTo("28.4");
        assertThat(decoded.numericValue()).isEqualTo(28.4);

        var encoded = ModbusValueCodec.encodeRegisters(point, "28.4");
        assertThat(encoded.wireBytes()).containsExactly(0x00, 0xEA);
    }

    @Test
    void roundTripsSignedAndUnsignedIntegers() {
        ModbusPoint int16 = point("BIG_ENDIAN", "BIG_ENDIAN");
        int16.setDataType(ModbusDataType.INT16);
        assertThat(ModbusValueCodec.decodeRegisters(int16, bytes(0xFF, 0x85)).text())
                .isEqualTo("-123");
        assertThat(ModbusValueCodec.encodeRegisters(int16, "-123").wireBytes())
                .containsExactly(0xFF, 0x85);

        ModbusPoint uint32 = point("BIG_ENDIAN", "BIG_ENDIAN");
        uint32.setDataType(ModbusDataType.UINT32);
        assertThat(
                        ModbusValueCodec.decodeRegisters(
                                        uint32, bytes(0xFF, 0xFF, 0xFF, 0xFF))
                                .text())
                .isEqualTo("4294967295");
    }

    @Test
    void formatsUint64WithoutPrecisionLoss() {
        ModbusPoint point = point("BIG_ENDIAN", "BIG_ENDIAN");
        point.setDataType(ModbusDataType.UINT64);
        var decoded =
                ModbusValueCodec.decodeRegisters(
                        point, bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF));
        assertThat(decoded.text()).isEqualTo("18446744073709551615");
        assertThat(decoded.numericValue()).isNull();
        assertThat(ModbusValueCodec.encodeRegisters(point, "18446744073709551615").wireBytes())
                .containsExactly(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
    }

    @Test
    void decodesAndEncodesStringWithTrailingNulls() {
        ModbusPoint point = point("BIG_ENDIAN", "BIG_ENDIAN");
        point.setDataType(ModbusDataType.STRING);
        point.setRegisterLength(4);
        point.setStringCharset(StringCharset.UTF_8);

        byte[] data = new byte[8];
        System.arraycopy("TEST".getBytes(StandardCharsets.UTF_8), 0, data, 0, 4);
        assertThat(ModbusValueCodec.decodeRegisters(point, data).text()).isEqualTo("TEST");
        assertThat(ModbusValueCodec.encodeRegisters(point, "AB").wireBytes())
                .containsExactly('A', 'B', 0, 0, 0, 0, 0, 0);
        assertThatThrownBy(() -> ModbusValueCodec.encodeRegisters(point, "TOO-LONG-VALUE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractsAndPreservesRegisterBit() {
        ModbusPoint point = point("BIG_ENDIAN", "BIG_ENDIAN");
        point.setDataType(ModbusDataType.BOOL);
        point.setBitIndex(3);
        point.setArea(PointArea.HOLDING_REGISTER);

        assertThat(ModbusValueCodec.decodeRegisters(point, bytes(0x00, 0x08)).booleanValue())
                .isTrue();
        assertThat(ModbusValueCodec.decodeRegisters(point, bytes(0x00, 0x04)).booleanValue())
                .isFalse();

        byte[] updated =
                ModbusValueCodec.encodeRegisterBit(point, true, bytes(0x12, 0x34));
        assertThat(updated).containsExactly(0x12, 0x3C);
        assertThat(ModbusValueCodec.encodeRegisterBit(point, false, updated))
                .containsExactly(0x12, 0x34);
    }

    @Test
    void parsesBooleanAliases() {
        assertThat(ModbusValueCodec.parseBoolean("开")).isTrue();
        assertThat(ModbusValueCodec.parseBoolean("OFF")).isFalse();
        assertThat(ModbusValueCodec.parseBoolean("1")).isTrue();
        assertThatThrownBy(() -> ModbusValueCodec.parseBoolean("maybe"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutOfRangeValues() {
        ModbusPoint point = point("BIG_ENDIAN", "BIG_ENDIAN");
        point.setDataType(ModbusDataType.INT16);
        assertThatThrownBy(() -> ModbusValueCodec.encodeRegisters(point, "40000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超出");
    }

    private static String decodeFloat(ModbusPoint point, byte[] data) {
        return ModbusValueCodec.decodeRegisters(point, data).text();
    }

    private static byte[] encodeFloat(ModbusPoint point, String value) {
        return ModbusValueCodec.encodeRegisters(point, value).wireBytes();
    }

    private static ModbusPoint point(String byteOrder, String wordOrder) {
        ModbusPoint point = new ModbusPoint();
        point.setDataType(ModbusDataType.FLOAT32);
        point.setByteOrder(ByteOrder.valueOf(byteOrder));
        point.setWordOrder(WordOrder.valueOf(wordOrder));
        point.setArea(PointArea.HOLDING_REGISTER);
        point.setAccess(PointAccess.READ_WRITE);
        point.setRegisterLength(2);
        point.setDecimals(null);
        return point;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
