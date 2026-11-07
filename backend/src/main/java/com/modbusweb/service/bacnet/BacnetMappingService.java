package com.modbusweb.service.bacnet;

import com.modbusweb.domain.BacnetObjectType;
import com.modbusweb.domain.entity.BacnetMapping;
import com.modbusweb.domain.entity.ModbusPoint;
import com.modbusweb.repository.BacnetMappingRepository;
import com.modbusweb.repository.ModbusPointRepository;
import com.modbusweb.service.DeviceService;
import com.modbusweb.service.ModbusReadWriteService;
import com.modbusweb.service.ModbusValueCodec;
import com.modbusweb.service.PointService;
import com.modbusweb.web.BadRequestException;
import com.modbusweb.web.ResourceNotFoundException;
import com.modbusweb.web.dto.BacnetGenerateResponse;
import com.modbusweb.web.dto.BacnetMappingRequest;
import com.modbusweb.web.dto.BacnetMappingResponse;
import com.modbusweb.web.dto.BacnetObjectView;
import com.modbusweb.web.dto.BacnetPreviewResponse;
import com.modbusweb.web.dto.MappingDtoMapper;
import com.modbusweb.web.dto.PointReadResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BacnetMappingService {

    private final BacnetMappingRepository mappingRepository;
    private final ModbusPointRepository pointRepository;
    private final DeviceService deviceService;
    private final PointService pointService;
    private final ModbusReadWriteService readWriteService;
    private final BacnetValueAdapter valueAdapter;

    public BacnetMappingService(
            BacnetMappingRepository mappingRepository,
            ModbusPointRepository pointRepository,
            DeviceService deviceService,
            PointService pointService,
            ModbusReadWriteService readWriteService,
            BacnetValueAdapter valueAdapter) {
        this.mappingRepository = mappingRepository;
        this.pointRepository = pointRepository;
        this.deviceService = deviceService;
        this.pointService = pointService;
        this.readWriteService = readWriteService;
        this.valueAdapter = valueAdapter;
    }

    @Transactional(readOnly = true)
    public List<BacnetMappingResponse> list(Long deviceId) {
        return mappingRepository.findByPointDeviceIdOrderByObjectTypeAscObjectInstanceAsc(deviceId)
                .stream()
                .map(MappingDtoMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BacnetMappingResponse get(Long id) {
        return MappingDtoMapper.toResponse(require(id));
    }

    @Transactional
    public BacnetMappingResponse create(BacnetMappingRequest request) {
        ModbusPoint point = pointService.requireWithDevice(request.pointId());
        if (mappingRepository.existsByPointId(point.getId())) {
            throw new BadRequestException("该点位已经存在 BACnet 映射");
        }
        BacnetMapping mapping = new BacnetMapping();
        mapping.setPoint(point);
        apply(mapping, request);
        return MappingDtoMapper.toResponse(mappingRepository.save(mapping));
    }

    @Transactional
    public BacnetMappingResponse update(Long id, BacnetMappingRequest request) {
        BacnetMapping mapping = require(id);
        if (!mapping.getPoint().getId().equals(request.pointId())) {
            throw new BadRequestException("不能修改已有映射关联的点位；请删除后重新创建");
        }
        apply(mapping, request);
        return MappingDtoMapper.toResponse(mappingRepository.save(mapping));
    }

    @Transactional
    public void delete(Long id) {
        mappingRepository.delete(require(id));
    }

    @Transactional
    public BacnetGenerateResponse generate(Long deviceId) {
        List<ModbusPoint> points =
                pointRepository.findByDeviceIdOrderByNameAsc(deviceId);
        if (points.isEmpty()) {
            throw new BadRequestException("该设备下没有点位");
        }
        List<String> warnings = new ArrayList<>();
        Map<BacnetObjectType, Integer> nextInstances = new EnumMap<>(BacnetObjectType.class);
        int created = 0;
        int skipped = 0;
        for (ModbusPoint point : points) {
            if (!point.isEnabled()) {
                skipped++;
                continue;
            }
            if (mappingRepository.existsByPointId(point.getId())) {
                skipped++;
                continue;
            }
            BacnetObjectType objectType = valueAdapter.suggestObjectType(point);
            if (objectType == null) {
                warnings.add(
                        "点位 " + point.getCode() + " (" + point.getDataType()
                                + ") 没有安全的 BACnet 标准对象映射，已跳过");
                skipped++;
                continue;
            }
            if (!valueAdapter.supports(point, objectType)) {
                warnings.add("点位 " + point.getCode() + " 的数据类型与建议对象类型不兼容，已跳过");
                skipped++;
                continue;
            }
            int instance =
                    nextInstances.computeIfAbsent(
                            objectType,
                            type -> {
                                int max = mappingRepository.findMaxInstance(type);
                                return max >= 4_194_302 ? 0 : max + 1;
                            });
            if (instance > 4_194_302) {
                warnings.add("对象类型 " + objectType + " 的实例号已耗尽，已跳过点位 " + point.getCode());
                skipped++;
                continue;
            }
            nextInstances.put(objectType, instance + 1);

            BacnetMapping mapping = new BacnetMapping();
            mapping.setPoint(point);
            mapping.setObjectType(objectType);
            mapping.setObjectInstance(instance);
            mapping.setObjectName(uniqueObjectName(deviceId, point, instance));
            mapping.setUnits(point.getUnit());
            mapping.setEnabled(true);
            mapping.setDescription("由 Modbus 点位 " + point.getCode() + " 自动生成");
            mappingRepository.save(mapping);
            created++;
        }
        mappingRepository.flush();
        List<BacnetMappingResponse> mappings = list(deviceId);
        return new BacnetGenerateResponse(created, skipped, mappings, warnings);
    }

    @Transactional(readOnly = true)
    public BacnetPreviewResponse preview(Long deviceId, boolean includeValues) {
        var device = deviceService.require(deviceId);
        List<BacnetMapping> mappings =
                mappingRepository.findByPointDeviceIdOrderByObjectTypeAscObjectInstanceAsc(deviceId);
        List<BacnetObjectView> objects = new ArrayList<>(mappings.size());
        List<String> warnings = new ArrayList<>();

        for (BacnetMapping mapping : mappings) {
            if (!mapping.isEnabled()) {
                continue;
            }
            ModbusPoint point = mapping.getPoint();
            ModbusValueCodec.DecodedValue decoded = null;
            if (includeValues && point.isEnabled() && point.getAccess().canRead()) {
                PointReadResponse read = readWriteService.readPoint(point.getId());
                if (read.success()) {
                    decoded = new ModbusValueCodec.DecodedValue(
                            read.value(), read.numericValue(), read.booleanValue(), null);
                } else {
                    warnings.add("读取点位 " + point.getCode() + " 失败: " + read.error());
                }
            }
            String valueType;
            Object presentValue = null;
            if (decoded != null) {
                var adapted = valueAdapter.toPresentValue(point, mapping.getObjectType(), decoded);
                valueType = adapted.presentValueType();
                presentValue = adapted.presentValue();
            } else {
                valueType =
                        switch (mapping.getObjectType().getFamily()) {
                            case BINARY -> "ENUMERATED";
                            case MULTI_STATE -> "UNSIGNED_INTEGER";
                            case ANALOG -> "REAL";
                        };
            }

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("deviceId", device.getId());
            source.put("deviceName", device.getName());
            source.put("host", device.getHost());
            source.put("port", device.getPort());
            source.put("unitId", point.effectiveUnitId());
            source.put("area", point.getArea());
            source.put("address", point.getAddress());
            source.put("addressReference", point.addressReference());
            source.put("dataType", point.getDataType());
            source.put("byteOrder", point.getByteOrder());
            source.put("wordOrder", point.getWordOrder());
            source.put("scale", point.getScale());
            source.put("offset", point.getOffset());

            objects.add(
                    new BacnetObjectView(
                            mapping.getId(),
                            point.getId(),
                            point.getCode(),
                            point.getName(),
                            mapping.getObjectType(),
                            mapping.getObjectInstance(),
                            mapping.objectId(),
                            mapping.getObjectName(),
                            valueType,
                            presentValue,
                            mapping.getUnits(),
                            mapping.getCovIncrement(),
                            mapping.isEnabled(),
                            source));
        }
        return new BacnetPreviewResponse(
                deviceId, device.getName(), Instant.now(), includeValues, objects, warnings);
    }

    private void apply(BacnetMapping mapping, BacnetMappingRequest request) {
        ModbusPoint point = mapping.getPoint();
        if (!valueAdapter.supports(point, request.objectType())) {
            throw new BadRequestException(
                    "点位数据类型 " + point.getDataType() + " 不能映射为 " + request.objectType());
        }
        if (mappingRepository.existsByObjectTypeAndObjectInstanceAndIdNot(
                request.objectType(), request.objectInstance(), mapping.getId() == null ? -1L : mapping.getId())) {
            throw new BadRequestException(
                    "BACnet 对象实例已被占用: " + request.objectType().getObjectId(request.objectInstance()));
        }
        String objectName = request.objectName().trim();
        if (objectName.isEmpty() || objectName.length() > 64) {
            throw new BadRequestException("BACnet 对象名称长度必须为 1..64 个字符");
        }
        if (mappingRepository.existsByObjectNameIgnoreCaseAndIdNot(
                objectName, mapping.getId() == null ? -1L : mapping.getId())) {
            throw new BadRequestException("BACnet 对象名称已存在: " + objectName);
        }
        mapping.setEnabled(request.enabled() == null || request.enabled());
        mapping.setObjectType(request.objectType());
        mapping.setObjectInstance(request.objectInstance());
        mapping.setObjectName(objectName);
        mapping.setUnits(trimToNull(request.units()));
        mapping.setCovIncrement(request.covIncrement());
        mapping.setDescription(trimToNull(request.description()));
    }

    private String uniqueObjectName(Long deviceId, ModbusPoint point, int instance) {
        String base =
                ("D" + deviceId + "_" + point.getCode())
                        .replaceAll("[^A-Za-z0-9_.:-]", "_");
        if (base.length() > 54) {
            base = base.substring(0, 54);
        }
        String candidate = base;
        int suffix = 0;
        while (mappingRepository.existsByObjectNameIgnoreCase(candidate)) {
            suffix++;
            String suffixText = "_" + instance;
            int maxBase = 64 - suffixText.length();
            candidate =
                    (base.length() > maxBase ? base.substring(0, maxBase) : base)
                            + suffixText
                            + (suffix > 1 ? "_" + suffix : "");
        }
        return candidate;
    }

    private BacnetMapping require(Long id) {
        return mappingRepository
                .findWithPointById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BACnet 映射不存在: " + id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public List<BacnetObjectType> supportedObjectTypes(ModbusPoint point) {
        return java.util.Arrays.stream(BacnetObjectType.values())
                .filter(type -> valueAdapter.supports(point, type))
                .toList();
    }
}
