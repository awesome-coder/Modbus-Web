package com.modbusweb.service;

import com.modbusweb.domain.ByteOrder;
import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.StringCharset;
import com.modbusweb.domain.WordOrder;
import com.modbusweb.domain.entity.ModbusPoint;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Encodes and decodes Modbus register/coil values.
 *
 * <p>Internally all numeric values are represented in canonical big-endian, high-word-first order.
 * The configured byte order (byte swapping inside a 16-bit register) and word order (register
 * order) are applied to/from the wire representation. This covers the four common layouts:
 * ABCD, BADC, CDAB and DCBA.
 */
public final class ModbusValueCodec {

    public static final int MAX_READ_REGISTERS = 125;
    public static final int MAX_WRITE_REGISTERS = 123;

    private ModbusValueCodec() {}

    public record DecodedValue(
            String text, Double numericValue, Boolean booleanValue, byte[] rawBytes) {}

    public record EncodedValue(byte[] wireBytes, int registerCount, String normalizedValue) {}

    public static DecodedValue decodeCoil(ModbusPoint point, boolean value) {
        return new DecodedValue(
                value ? "ON" : "OFF", value ? 1.0 : 0.0, value, new byte[] {(byte) (value ? 1 : 0)});
    }

    public static DecodedValue decodeRegisters(ModbusPoint point, byte[] wireBytes) {
        int expected = point.byteCount();
        if (wireBytes == null || wireBytes.length < expected) {
            throw new IllegalArgumentException(
                    "寄存器数据长度不足，期望 " + expected + " 字节，实际 "
                            + (wireBytes == null ? 0 : wireBytes.length));
        }
        byte[] ordered = toCanonical(wireBytes, point.getByteOrder(), point.getWordOrder());
        byte[] raw = new byte[expected];
        System.arraycopy(ordered, 0, raw, 0, expected);

        if (point.getDataType() == ModbusDataType.BOOL) {
            int bitIndex = point.getBitIndex() == null ? 0 : point.getBitIndex();
            if (bitIndex < 0 || bitIndex > 15) {
                throw new IllegalArgumentException("位索引必须在 0..15 之间");
            }
            int register = ((ordered[0] & 0xFF) << 8) | (ordered[1] & 0xFF);
            boolean value = (register & (1 << bitIndex)) != 0;
            return new DecodedValue(value ? "ON" : "OFF", value ? 1.0 : 0.0, value, raw);
        }

        if (point.getDataType() == ModbusDataType.STRING) {
            Charset charset = Charset.forName(point.getStringCharset().name().replace('_', '-'));
            String value = new String(raw, charset);
            int end = value.length();
            while (end > 0 && (value.charAt(end - 1) == '\u0000' || value.charAt(end - 1) == '\uFFFF')) {
                end--;
            }
            String trimmed = value.substring(0, end);
            return new DecodedValue(trimmed, null, null, raw);
        }

        if (point.getDataType() == ModbusDataType.HEX) {
            return new DecodedValue(toHex(raw), null, null, raw);
        }

        BigDecimal rawNumber = readNumber(point.getDataType(), raw);
        BigDecimal engineering =
                rawNumber
                        .multiply(BigDecimal.valueOf(point.getScale()))
                        .add(BigDecimal.valueOf(point.getOffset()));
        String text = format(engineering, point.getDecimals());
        Double numeric =
                engineering.abs().compareTo(BigDecimal.valueOf(9.007199254740992E15)) <= 0
                        ? engineering.doubleValue()
                        : null;
        return new DecodedValue(text, numeric, null, raw);
    }

    public static EncodedValue encodeRegisters(ModbusPoint point, String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("写入值不能为空");
        }
        if (point.getDataType() == ModbusDataType.STRING) {
            byte[] bytes = encodeString(point, input);
            return new EncodedValue(
                    fromCanonical(bytes, point.getByteOrder(), point.getWordOrder()),
                    point.registerCount(),
                    input);
        }
        if (point.getDataType() == ModbusDataType.HEX) {
            byte[] bytes = parseHexToLength(input, point.byteCount());
            return new EncodedValue(
                    fromCanonical(bytes, point.getByteOrder(), point.getWordOrder()),
                    point.registerCount(),
                    toHex(bytes));
        }
        if (point.getDataType() == ModbusDataType.BOOL) {
            boolean value = parseBoolean(input);
            return new EncodedValue(
                    new byte[] {(byte) (value ? 1 : 0)}, 1, value ? "ON" : "OFF");
        }

        BigDecimal engineering;
        try {
            engineering = new BigDecimal(input.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析数字：" + input);
        }
        if (point.getScale() == 0.0d) {
            throw new IllegalArgumentException("缩放系数不能为 0");
        }
        BigDecimal rawNumber =
                engineering
                        .subtract(BigDecimal.valueOf(point.getOffset()))
                        .divide(BigDecimal.valueOf(point.getScale()), 12, RoundingMode.HALF_UP);

        byte[] canonical = writeNumber(point.getDataType(), rawNumber);
        return new EncodedValue(
                fromCanonical(canonical, point.getByteOrder(), point.getWordOrder()),
                point.registerCount(),
                format(engineering, point.getDecimals()));
    }

    /**
     * Set a single bit inside a holding register. The current register bytes are supplied so the
     * other 15 bits are preserved.
     */
    public static byte[] encodeRegisterBit(
            ModbusPoint point, boolean value, byte[] currentWireBytes) {
        if (point.getDataType() != ModbusDataType.BOOL || !point.getArea().isRegister()) {
            throw new IllegalArgumentException("该点位不是寄存器位类型");
        }
        if (currentWireBytes == null || currentWireBytes.length < 2) {
            throw new IllegalArgumentException("读取当前寄存器失败，无法执行位写入");
        }
        int bitIndex = point.getBitIndex() == null ? 0 : point.getBitIndex();
        byte[] canonical = toCanonical(currentWireBytes, point.getByteOrder(), point.getWordOrder());
        int register = ((canonical[0] & 0xFF) << 8) | (canonical[1] & 0xFF);
        if (value) {
            register |= (1 << bitIndex);
        } else {
            register &= ~(1 << bitIndex);
        }
        byte[] updated = new byte[] {(byte) (register >> 8), (byte) register};
        return fromCanonical(updated, point.getByteOrder(), point.getWordOrder());
    }

    public static boolean parseBoolean(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "true", "on", "yes", "y", "开", "合", "是" -> true;
            case "0", "false", "off", "no", "n", "关", "分", "否" -> false;
            default -> throw new IllegalArgumentException("无法解析布尔值：" + input);
        };
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Convert wire bytes (big-endian registers from the Modbus PDU) into canonical ordering. This
     * transform is its own inverse.
     */
    public static byte[] toCanonical(byte[] wire, ByteOrder byteOrder, WordOrder wordOrder) {
        if (wire.length % 2 != 0) {
            throw new IllegalArgumentException("寄存器字节数必须为偶数");
        }
        int words = wire.length / 2;
        byte[] out = new byte[wire.length];
        for (int i = 0; i < words; i++) {
            int targetWord = wordOrder == WordOrder.BIG_ENDIAN ? i : words - 1 - i;
            int src = i * 2;
            int dst = targetWord * 2;
            if (byteOrder == ByteOrder.BIG_ENDIAN) {
                out[dst] = wire[src];
                out[dst + 1] = wire[src + 1];
            } else {
                out[dst] = wire[src + 1];
                out[dst + 1] = wire[src];
            }
        }
        return out;
    }

    private static byte[] fromCanonical(byte[] canonical, ByteOrder byteOrder, WordOrder wordOrder) {
        return toCanonical(canonical, byteOrder, wordOrder);
    }

    private static BigDecimal readNumber(ModbusDataType type, byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.BIG_ENDIAN);
        return switch (type) {
            case INT16 -> BigDecimal.valueOf(buffer.getShort());
            case UINT16 -> BigDecimal.valueOf(buffer.getShort() & 0xFFFF);
            case INT32 -> BigDecimal.valueOf(buffer.getInt());
            case UINT32 -> BigDecimal.valueOf(Integer.toUnsignedLong(buffer.getInt()));
            case INT64 -> BigDecimal.valueOf(buffer.getLong());
            case UINT64 -> new BigDecimal(new BigInteger(1, bytes));
            case FLOAT32 -> {
                float value = buffer.getFloat();
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException("设备返回了非有限浮点数：" + value);
                }
                yield BigDecimal.valueOf(value);
            }
            case FLOAT64 -> {
                double value = buffer.getDouble();
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("设备返回了非有限浮点数：" + value);
                }
                yield BigDecimal.valueOf(value);
            }
            default -> throw new IllegalArgumentException("不支持的数值类型：" + type);
        };
    }

    private static byte[] writeNumber(ModbusDataType type, BigDecimal rawNumber) {
        return switch (type) {
            case INT16 -> {
                checkRange(rawNumber, BigDecimal.valueOf(Short.MIN_VALUE), BigDecimal.valueOf(Short.MAX_VALUE), type);
                yield ByteBuffer.allocate(2).putShort(rawNumber.setScale(0, RoundingMode.HALF_UP).shortValue()).array();
            }
            case UINT16 -> {
                checkRange(rawNumber, BigDecimal.ZERO, BigDecimal.valueOf(65535), type);
                yield ByteBuffer.allocate(2)
                        .putShort((short) rawNumber.setScale(0, RoundingMode.HALF_UP).intValue())
                        .array();
            }
            case INT32 -> {
                checkRange(
                        rawNumber,
                        BigDecimal.valueOf(Integer.MIN_VALUE),
                        BigDecimal.valueOf(Integer.MAX_VALUE),
                        type);
                yield ByteBuffer.allocate(4).putInt(rawNumber.setScale(0, RoundingMode.HALF_UP).intValue()).array();
            }
            case UINT32 -> {
                checkRange(rawNumber, BigDecimal.ZERO, new BigDecimal("4294967295"), type);
                yield ByteBuffer.allocate(4)
                        .putInt((int) rawNumber.setScale(0, RoundingMode.HALF_UP).longValue())
                        .array();
            }
            case INT64 -> {
                checkRange(
                        rawNumber,
                        BigDecimal.valueOf(Long.MIN_VALUE),
                        BigDecimal.valueOf(Long.MAX_VALUE),
                        type);
                yield ByteBuffer.allocate(8).putLong(rawNumber.setScale(0, RoundingMode.HALF_UP).longValue()).array();
            }
            case UINT64 -> {
                checkRange(rawNumber, BigDecimal.ZERO, new BigDecimal("18446744073709551615"), type);
                byte[] big = rawNumber.setScale(0, RoundingMode.HALF_UP).toBigInteger().toByteArray();
                byte[] out = new byte[8];
                int copy = Math.min(big.length, 8);
                System.arraycopy(big, big.length - copy, out, 8 - copy, copy);
                yield out;
            }
            case FLOAT32 -> {
                double value = rawNumber.doubleValue();
                if (Math.abs(value) > Float.MAX_VALUE) {
                    throw new IllegalArgumentException("数值超出 FLOAT32 范围");
                }
                yield ByteBuffer.allocate(4).putFloat((float) value).array();
            }
            case FLOAT64 -> ByteBuffer.allocate(8).putDouble(rawNumber.doubleValue()).array();
            default -> throw new IllegalArgumentException("不支持的数值类型：" + type);
        };
    }

    private static void checkRange(
            BigDecimal value, BigDecimal min, BigDecimal max, ModbusDataType type) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                    "数值 " + value.toPlainString() + " 超出 " + type + " 允许范围 ["
                            + min.toPlainString() + ", " + max.toPlainString() + "]");
        }
    }

    private static byte[] encodeString(ModbusPoint point, String value) {
        Charset charset = Charset.forName(point.getStringCharset().name().replace('_', '-'));
        byte[] encoded = value.getBytes(charset);
        byte[] out = new byte[point.byteCount()];
        if (encoded.length > out.length) {
            throw new IllegalArgumentException(
                    "字符串编码后为 " + encoded.length + " 字节，超过点位容量 " + out.length + " 字节");
        }
        System.arraycopy(encoded, 0, out, 0, encoded.length);
        return out;
    }

    private static byte[] parseHexToLength(String input, int length) {
        String cleaned = input.replaceAll("(?i)0x", "").replaceAll("[\\s,;:_-]", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("十六进制内容不能为空");
        }
        if (cleaned.length() % 2 != 0) {
            cleaned = "0" + cleaned;
        }
        if (cleaned.length() / 2 > length) {
            throw new IllegalArgumentException(
                    "十六进制内容为 " + (cleaned.length() / 2) + " 字节，超过点位容量 " + length + " 字节");
        }
        byte[] out = new byte[length];
        int offset = length - cleaned.length() / 2;
        for (int i = 0; i < cleaned.length(); i += 2) {
            out[offset + i / 2] = (byte) Integer.parseInt(cleaned.substring(i, i + 2), 16);
        }
        return out;
    }

    private static String format(BigDecimal value, Integer decimals) {
        BigDecimal display = value;
        if (decimals != null) {
            display = value.setScale(decimals, RoundingMode.HALF_UP);
        }
        String text = display.stripTrailingZeros().toPlainString();
        return text.equals("-0") ? "0" : text;
    }

    public static String describeLayout(ModbusPoint point) {
        boolean byteSwap = point.getByteOrder() == ByteOrder.LITTLE_ENDIAN;
        boolean wordSwap = point.getWordOrder() == WordOrder.LITTLE_ENDIAN;
        if (!byteSwap && !wordSwap) {
            return "ABCD";
        }
        if (byteSwap && !wordSwap) {
            return "BADC";
        }
        if (!byteSwap && wordSwap) {
            return "CDAB";
        }
        return "DCBA";
    }

    public static boolean isCoilArea(PointArea area) {
        return area == PointArea.COIL || area == PointArea.DISCRETE_INPUT;
    }
}
