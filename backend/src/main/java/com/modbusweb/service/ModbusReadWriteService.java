package com.modbusweb.service;

import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointAccess;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.ValueQuality;
import com.modbusweb.domain.entity.ModbusDevice;
import com.modbusweb.domain.entity.ModbusPoint;
import com.modbusweb.repository.ModbusPointRepository;
import com.modbusweb.web.BadRequestException;
import com.modbusweb.web.ModbusOperationException;
import com.modbusweb.web.dto.PointReadResponse;
import com.modbusweb.web.dto.ReadAllResponse;
import com.modbusweb.web.dto.WriteValueResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModbusReadWriteService {

    private static final Logger log = LoggerFactory.getLogger(ModbusReadWriteService.class);

    private final ModbusClientManager clientManager;
    private final PointService pointService;
    private final DeviceService deviceService;
    private final ModbusPointRepository pointRepository;

    public ModbusReadWriteService(
            ModbusClientManager clientManager,
            PointService pointService,
            DeviceService deviceService,
            ModbusPointRepository pointRepository) {
        this.clientManager = clientManager;
        this.pointService = pointService;
        this.deviceService = deviceService;
        this.pointRepository = pointRepository;
    }

    @Transactional(readOnly = true)
    public PointReadResponse readPoint(Long pointId) {
        ModbusPoint point = pointService.requireWithDevice(pointId);
        if (!point.isEnabled()) {
            throw new BadRequestException("点位已禁用: " + point.getCode());
        }
        if (!point.getAccess().canRead()) {
            throw new BadRequestException("点位为只写，不能读取: " + point.getCode());
        }
        return readPointInternal(point);
    }

    @Transactional(readOnly = true)
    public ReadAllResponse readAll(Long deviceId) {
        ModbusDevice device = deviceService.require(deviceId);
        if (!device.isEnabled()) {
            throw new BadRequestException("设备已禁用，无法读取");
        }
        List<ModbusPoint> points =
                pointRepository.findByDeviceIdAndEnabledTrueOrderByNameAsc(deviceId);
        Instant started = Instant.now();
        long startNanos = System.nanoTime();
        List<PointReadResponse> results = new ArrayList<>(points.size());
        int success = 0;
        for (ModbusPoint point : points) {
            PointReadResponse result;
            if (!point.getAccess().canRead()) {
                result = failed(point, "点位为只写，不能读取", 0);
            } else {
                result = readPointInternal(point);
            }
            if (result.success()) {
                success++;
            }
            results.add(result);
        }
        Instant finished = Instant.now();
        return new ReadAllResponse(
                device.getId(),
                device.getName(),
                started,
                finished,
                (System.nanoTime() - startNanos) / 1_000_000,
                results.size(),
                success,
                results.size() - success,
                results);
    }

    @Transactional(readOnly = true)
    public WriteValueResponse writePoint(Long pointId, String value) {
        ModbusPoint point = pointService.requireWithDevice(pointId);
        if (!point.isEnabled()) {
            throw new BadRequestException("点位已禁用: " + point.getCode());
        }
        if (!point.getAccess().canWrite()) {
            throw new BadRequestException("点位为只读，不能写入: " + point.getCode());
        }
        if (!point.getArea().isWritable()) {
            throw new BadRequestException(point.getArea().getLabelZh() + " 区域不支持写入");
        }

        try {
            clientManager.execute(
                    point.getDevice(),
                    client -> {
                        write(client, point, value);
                        return null;
                    });
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        } catch (Exception e) {
            throw new ModbusOperationException("写入点位 [" + point.getCode() + "] 失败: " + messageOf(e), e);
        }

        PointReadResponse readBack = readPointInternal(point);
        String message =
                readBack.success()
                        ? "写入成功，回读值：" + readBack.value()
                        : "写入命令已发送，但回读失败：" + readBack.error();
        return new WriteValueResponse(true, message, readBack);
    }

    private void write(ModbusTcpClient client, ModbusPoint point, String value) throws Exception {
        int unitId = point.effectiveUnitId();
        if (ModbusValueCodec.isCoilArea(point.getArea())) {
            boolean boolValue = ModbusValueCodec.parseBoolean(value);
            client.writeSingleCoil(unitId, new WriteSingleCoilRequest(point.getAddress(), boolValue));
            return;
        }
        if (point.getArea() != PointArea.HOLDING_REGISTER) {
            throw new IllegalArgumentException("只有保持寄存器支持写入数值");
        }

        if (point.getDataType() == ModbusDataType.BOOL) {
            int current =
                    readRegister(
                            client,
                            unitId,
                            point.getAddress(),
                            point.getArea());
            byte[] currentWire = new byte[] {(byte) (current >> 8), (byte) current};
            boolean boolValue = ModbusValueCodec.parseBoolean(value);
            byte[] updated =
                    ModbusValueCodec.encodeRegisterBit(point, boolValue, currentWire);
            int register = ((updated[0] & 0xFF) << 8) | (updated[1] & 0xFF);
            client.writeSingleRegister(
                    unitId, new WriteSingleRegisterRequest(point.getAddress(), register));
            return;
        }

        ModbusValueCodec.EncodedValue encoded = ModbusValueCodec.encodeRegisters(point, value);
        if (encoded.registerCount() == 1) {
            int register = ((encoded.wireBytes()[0] & 0xFF) << 8) | (encoded.wireBytes()[1] & 0xFF);
            client.writeSingleRegister(
                    unitId, new WriteSingleRegisterRequest(point.getAddress(), register));
        } else {
            client.writeMultipleRegisters(
                    unitId,
                    new WriteMultipleRegistersRequest(
                            point.getAddress(),
                            encoded.registerCount(),
                            encoded.wireBytes()));
        }
    }

    private int readRegister(
            ModbusTcpClient client, int unitId, int address, PointArea area) throws Exception {
        if (area == PointArea.HOLDING_REGISTER) {
            var response =
                    client.readHoldingRegisters(
                            unitId, new ReadHoldingRegistersRequest(address, 1));
            if (response.registers().length < 2) {
                throw new IllegalStateException("读取当前寄存器返回数据不足");
            }
            return ((response.registers()[0] & 0xFF) << 8) | (response.registers()[1] & 0xFF);
        }
        throw new IllegalArgumentException("指定区域不支持寄存器位写入");
    }

    private PointReadResponse readPointInternal(ModbusPoint point) {
        long start = System.nanoTime();
        try {
            ModbusValueCodec.DecodedValue decoded =
                    clientManager.execute(
                            point.getDevice(),
                            client -> readValue(client, point));
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return new PointReadResponse(
                    point.getId(),
                    point.getCode(),
                    point.getName(),
                    point.getArea(),
                    point.getAddress(),
                    point.addressReference(),
                    point.getDataType(),
                    true,
                    decoded.text(),
                    decoded.numericValue(),
                    decoded.booleanValue(),
                    ModbusValueCodec.toHex(decoded.rawBytes()),
                    ValueQuality.GOOD,
                    null,
                    point.getUnit(),
                    point.registerCount(),
                    Instant.now(),
                    elapsed);
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            log.debug("Read point {} failed: {}", point.getCode(), e.getMessage());
            return failed(point, messageOf(e), elapsed);
        }
    }

    private ModbusValueCodec.DecodedValue readValue(ModbusTcpClient client, ModbusPoint point)
            throws Exception {
        int unitId = point.effectiveUnitId();
        int address = point.getAddress();
        if (point.getArea() == PointArea.COIL) {
            var response = client.readCoils(unitId, new ReadCoilsRequest(address, 1));
            boolean value =
                    response.coils().length > 0 && (response.coils()[0] & 0x01) != 0;
            return ModbusValueCodec.decodeCoil(point, value);
        }
        if (point.getArea() == PointArea.DISCRETE_INPUT) {
            var response =
                    client.readDiscreteInputs(
                            unitId, new ReadDiscreteInputsRequest(address, 1));
            boolean value =
                    response.inputs().length > 0 && (response.inputs()[0] & 0x01) != 0;
            return ModbusValueCodec.decodeCoil(point, value);
        }
        int quantity = point.registerCount();
        if (quantity > ModbusValueCodec.MAX_READ_REGISTERS) {
            throw new IllegalArgumentException("单个点位读取寄存器数量超过 125");
        }
        byte[] registers;
        if (point.getArea() == PointArea.HOLDING_REGISTER) {
            registers =
                    client.readHoldingRegisters(
                                    unitId, new ReadHoldingRegistersRequest(address, quantity))
                            .registers();
        } else {
            registers =
                    client.readInputRegisters(
                                    unitId, new ReadInputRegistersRequest(address, quantity))
                            .registers();
        }
        return ModbusValueCodec.decodeRegisters(point, registers);
    }

    private PointReadResponse failed(ModbusPoint point, String error, long elapsedMs) {
        return new PointReadResponse(
                point.getId(),
                point.getCode(),
                point.getName(),
                point.getArea(),
                point.getAddress(),
                point.addressReference(),
                point.getDataType(),
                false,
                null,
                null,
                null,
                null,
                ValueQuality.BAD,
                error,
                point.getUnit(),
                point.registerCount(),
                Instant.now(),
                elapsedMs);
    }

    private static String messageOf(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message == null ? throwable.getClass().getSimpleName() : message;
    }
}
