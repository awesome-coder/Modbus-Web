package com.modbusweb.service;

import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointAccess;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.StringCharset;
import com.modbusweb.domain.entity.ModbusPoint;
import com.modbusweb.repository.ModbusPointRepository;
import com.modbusweb.web.BadRequestException;
import com.modbusweb.web.ResourceNotFoundException;
import com.modbusweb.web.dto.MappingDtoMapper;
import com.modbusweb.web.dto.PointRequest;
import com.modbusweb.web.dto.PointResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {

    private final ModbusPointRepository pointRepository;
    private final DeviceService deviceService;

    public PointService(ModbusPointRepository pointRepository, DeviceService deviceService) {
        this.pointRepository = pointRepository;
        this.deviceService = deviceService;
    }

    @Transactional(readOnly = true)
    public List<PointResponse> list(Long deviceId) {
        deviceService.require(deviceId);
        return pointRepository.findByDeviceIdOrderByNameAsc(deviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PointResponse get(Long id) {
        return toResponse(requireWithDevice(id));
    }

    @Transactional(readOnly = true)
    public ModbusPoint requireWithDevice(Long id) {
        return pointRepository
                .findWithDeviceById(id)
                .orElseThrow(() -> new ResourceNotFoundException("点位不存在: " + id));
    }

    @Transactional(readOnly = true)
    public ModbusPoint require(Long id) {
        return pointRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("点位不存在: " + id));
    }

    @Transactional
    public PointResponse create(Long deviceId, PointRequest request) {
        validateForImport(deviceId, null, request);
        var device = deviceService.require(deviceId);
        ModbusPoint point = new ModbusPoint();
        point.setDevice(device);
        point.setCode(request.code().trim());
        apply(point, request);
        point = pointRepository.save(point);
        return toResponse(point);
    }

    @Transactional
    public PointResponse update(Long id, PointRequest request) {
        validateForImport(null, id, request);
        ModbusPoint point = requireWithDevice(id);
        point.setCode(request.code().trim());
        apply(point, request);
        point = pointRepository.save(point);
        return toResponse(point);
    }

    /**
     * Validate a point request without writing it. If {@code existingId} is supplied it is treated
     * as an update and {@code deviceId} is ignored.
     */
    @Transactional(readOnly = true)
    public void validateForImport(Long deviceId, Long existingId, PointRequest request) {
        Long effectiveDeviceId = deviceId;
        if (existingId != null) {
            ModbusPoint existing = require(existingId);
            effectiveDeviceId = existing.getDevice().getId();
        }
        if (effectiveDeviceId == null) {
            throw new BadRequestException("缺少设备 ID");
        }
        String code = request.code() == null ? "" : request.code().trim();
        if (code.isEmpty()) {
            throw new BadRequestException("点位编码不能为空");
        }
        boolean duplicate =
                existingId == null
                        ? pointRepository.existsByDeviceIdAndCodeIgnoreCase(effectiveDeviceId, code)
                        : pointRepository.existsByDeviceIdAndCodeIgnoreCaseAndIdNot(
                                effectiveDeviceId, code, existingId);
        if (duplicate) {
            throw new BadRequestException("点位编码已存在: " + code);
        }
        ModbusPoint probe = new ModbusPoint();
        probe.setCode(code);
        apply(probe, request);
    }

    @Transactional
    public void delete(Long id) {
        ModbusPoint point = require(id);
        pointRepository.delete(point);
    }

    private void apply(ModbusPoint point, PointRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw BadRequestException.withField("name", "点位名称不能为空");
        }
        PointArea area = request.area();
        ModbusDataType dataType = request.dataType();
        if (!area.isRegister() && dataType != ModbusDataType.BOOL) {
            throw BadRequestException.withField("dataType", "线圈/离散输入只能使用 BOOL 数据类型");
        }
        if (!area.isWritable()
                && (request.access() == PointAccess.READ_WRITE
                        || request.access() == PointAccess.WRITE_ONLY)) {
            throw BadRequestException.withField("access", area.getLabelZh() + "区域不支持写入");
        }

        point.setName(name);
        point.setArea(area);
        point.setAddress(request.address());
        point.setUnitId(request.unitId());
        point.setDataType(dataType);
        point.setByteOrder(request.byteOrder());
        point.setWordOrder(request.wordOrder());
        point.setAccess(request.access());
        point.setStringCharset(
                request.stringCharset() == null ? StringCharset.UTF_8 : request.stringCharset());
        point.setScale(request.scale() == null ? 1.0 : request.scale());
        point.setOffset(request.offset() == null ? 0.0 : request.offset());
        point.setDecimals(request.decimals());
        point.setUnit(trimToNull(request.unit()));
        point.setEnabled(request.enabled() == null || request.enabled());
        point.setDescription(trimToNull(request.description()));

        if (point.getScale() == 0.0d) {
            throw BadRequestException.withField("scale", "缩放系数不能为 0");
        }

        if (dataType == ModbusDataType.BOOL) {
            if (area.isRegister()) {
                point.setBitIndex(request.bitIndex() == null ? 0 : request.bitIndex());
            } else {
                point.setBitIndex(null);
            }
            point.setRegisterLength(null);
        } else {
            point.setBitIndex(null);
            if (dataType.isVariableLength()) {
                if (request.registerLength() == null) {
                    throw BadRequestException.withField(
                            "registerLength", "STRING / HEX 类型必须指定寄存器数量");
                }
                point.setRegisterLength(request.registerLength());
            } else {
                point.setRegisterLength(null);
            }
        }

        int registerCount = point.registerCount();
        int maxRegisters = point.supportsWrite() ? 123 : 125;
        if (area.isRegister() && registerCount > maxRegisters) {
            throw BadRequestException.withField(
                    "registerLength", "单个点位最多使用 " + maxRegisters + " 个寄存器");
        }
        if ((long) point.getAddress() + registerCount > 65536L) {
            throw BadRequestException.withField("address", "地址 + 数据长度超出 Modbus 地址空间 (65536)");
        }
        if (dataType == ModbusDataType.STRING) {
            int maxChars = point.byteCount();
            if (maxChars <= 0) {
                throw BadRequestException.withField("registerLength", "字符串容量必须大于 0");
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public PointResponse toResponse(ModbusPoint point) {
        return new PointResponse(
                point.getId(),
                point.getDevice().getId(),
                point.getDevice().getName(),
                point.getCode(),
                point.getName(),
                point.getArea(),
                point.getAddress(),
                point.addressReference(),
                point.getUnitId(),
                point.effectiveUnitId(),
                point.getDataType(),
                point.getByteOrder(),
                point.getWordOrder(),
                point.getBitIndex(),
                point.getRegisterLength(),
                point.registerCount(),
                point.getStringCharset(),
                point.getScale(),
                point.getOffset(),
                point.getDecimals(),
                point.getUnit(),
                point.getAccess(),
                point.isEnabled(),
                point.getDescription(),
                point.supportsWrite(),
                MappingDtoMapper.toResponse(point.getBacnetMapping()),
                point.getCreatedAt(),
                point.getUpdatedAt(),
                point.getVersion());
    }
}
