package com.modbusweb.service.excel;

import com.modbusweb.domain.AddressNotation;
import com.modbusweb.domain.BacnetObjectType;
import com.modbusweb.domain.ByteOrder;
import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.PointAccess;
import com.modbusweb.domain.PointArea;
import com.modbusweb.domain.ProtocolType;
import com.modbusweb.domain.StringCharset;
import com.modbusweb.domain.WordOrder;
import com.modbusweb.domain.entity.ModbusDevice;
import com.modbusweb.domain.entity.ModbusPoint;
import com.modbusweb.repository.ModbusDeviceRepository;
import com.modbusweb.repository.ModbusPointRepository;
import com.modbusweb.service.DeviceService;
import com.modbusweb.service.ModbusValueCodec;
import com.modbusweb.service.PointService;
import com.modbusweb.web.BadRequestException;
import com.modbusweb.web.dto.DeviceRequest;
import com.modbusweb.web.dto.ImportIssue;
import com.modbusweb.web.dto.ImportReport;
import com.modbusweb.web.dto.PointRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcelService {

    private static final String[] DEVICE_HEADERS = {
        "设备名称",
        "协议",
        "IP地址",
        "端口",
        "从站地址",
        "连接超时(ms)",
        "请求超时(ms)",
        "启用",
        "TLS客户端证书",
        "TLS客户端私钥",
        "私钥密码",
        "TLS CA证书",
        "校验主机名",
        "描述"
    };

    private static final String[] POINT_HEADERS = {
        "设备名称",
        "点位编码",
        "点位名称",
        "区域",
        "地址",
        "地址格式",
        "数据类型",
        "字节序",
        "字序",
        "端序",
        "位索引",
        "寄存器数量",
        "字符串编码",
        "缩放系数",
        "偏移量",
        "小数位",
        "单位",
        "读写权限",
        "从站地址",
        "启用",
        "描述"
    };

    private final DeviceService deviceService;
    private final PointService pointService;
    private final ModbusDeviceRepository deviceRepository;
    private final ModbusPointRepository pointRepository;
    private final Validator validator;
    private final int maxRows;

    public ExcelService(
            DeviceService deviceService,
            PointService pointService,
            ModbusDeviceRepository deviceRepository,
            ModbusPointRepository pointRepository,
            Validator validator,
            @Value("${app.excel.max-rows:5000}") int maxRows) {
        this.deviceService = deviceService;
        this.pointService = pointService;
        this.deviceRepository = deviceRepository;
        this.pointRepository = pointRepository;
        this.validator = validator;
        this.maxRows = maxRows;
    }

    @Transactional(readOnly = true)
    public byte[] exportAll() {
        return buildWorkbook(false);
    }

    public byte[] createTemplate() {
        return buildWorkbook(true);
    }
    public String exportFilename(String prefix) {
        return prefix
                + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".xlsx";
    }

    private byte[] buildWorkbook(boolean includeExamples) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet devices = workbook.createSheet("设备");
            Sheet points = workbook.createSheet("点位");
            writeDeviceSheet(workbook, devices, styles, includeExamples);
            writePointSheet(workbook, points, styles, includeExamples);
            writeInstructions(workbook, styles);
            workbook.setActiveSheet(0);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 文件失败", e);
        }
    }

    private void writeDeviceSheet(
            Workbook workbook, Sheet sheet, Styles styles, boolean includeExamples) {
        int rowIndex = writeHeader(sheet, styles, DEVICE_HEADERS);
        if (!includeExamples) {
            for (ModbusDevice device : deviceRepository.findAllByOrderByNameAsc()) {
                Row row = sheet.createRow(rowIndex++);
                write(
                        row,
                        device.getName(),
                        device.getProtocol().name(),
                        device.getHost(),
                        device.getPort(),
                        device.getUnitId(),
                        device.getConnectTimeoutMs(),
                        device.getRequestTimeoutMs(),
                        device.isEnabled() ? "是" : "否",
                        device.getTlsClientCertPath(),
                        device.getTlsClientKeyPath(),
                        "",
                        device.getTlsCaCertPath(),
                        device.isTlsVerifyHostname() ? "是" : "否",
                        device.getDescription());
            }
        }
        if (includeExamples) {
            Row row = sheet.createRow(rowIndex++);
            write(
                    row,
                    "示例设备",
                    ProtocolType.MODBUS_TCP.name(),
                    "127.0.0.1",
                    502,
                    1,
                    3000,
                    3000,
                    "是",
                    "",
                    "",
                    "",
                    "",
                    "是",
                    "导入前请替换为真实设备，或删除本示例行");
            styleExampleRow(row, styles, DEVICE_HEADERS.length);
        }
        finishSheet(sheet, styles, 0, Math.max(0, rowIndex - 1), DEVICE_HEADERS.length);
        setWidths(sheet, new int[] {22, 22, 22, 10, 12, 16, 16, 10, 34, 34, 16, 34, 12, 44});
    }

    private void writePointSheet(
            Workbook workbook, Sheet sheet, Styles styles, boolean includeExamples) {
        int rowIndex = writeHeader(sheet, styles, POINT_HEADERS);
        if (!includeExamples) {
            List<ModbusPoint> points =
                    pointRepository.findAll().stream()
                            .sorted(
                                    (a, b) -> {
                                        int device =
                                                a.getDevice()
                                                        .getName()
                                                        .compareToIgnoreCase(b.getDevice().getName());
                                        return device != 0
                                                ? device
                                                : a.getCode().compareToIgnoreCase(b.getCode());
                                    })
                            .toList();
            for (ModbusPoint point : points) {
                Row row = sheet.createRow(rowIndex++);
                writePoint(
                        row,
                        point.getDevice().getName(),
                        point.getCode(),
                        point.getName(),
                        point.getArea(),
                        point.addressReference(),
                        "MODBUS_REFERENCE",
                        point.getDataType(),
                        point.getByteOrder(),
                        point.getWordOrder(),
                        ModbusValueCodec.describeLayout(point),
                        point.getBitIndex(),
                        point.getRegisterLength(),
                        point.getStringCharset(),
                        point.getScale(),
                        point.getOffset(),
                        point.getDecimals(),
                        point.getUnit(),
                        point.getAccess(),
                        point.getUnitId(),
                        point.isEnabled(),
                        point.getDescription());
            }
        }
        if (includeExamples) {
            rowIndex =
                    addExamplePoint(
                            sheet,
                            styles,
                            rowIndex,
                            "示例设备",
                            "TEMP_01",
                            "温度",
                            PointArea.HOLDING_REGISTER,
                            "40001",
                            "MODBUS_REFERENCE",
                            ModbusDataType.FLOAT32,
                            ByteOrder.BIG_ENDIAN,
                            WordOrder.BIG_ENDIAN,
                            "ABCD",
                            null,
                            2,
                            StringCharset.UTF_8,
                            1.0,
                            0.0,
                            2,
                            "°C",
                            PointAccess.READ_ONLY,
                            null,
                            true,
                            "FLOAT32 ABCD 示例");
            rowIndex =
                    addExamplePoint(
                            sheet,
                            styles,
                            rowIndex,
                            "示例设备",
                            "RUN",
                            "运行状态",
                            PointArea.COIL,
                            "00001",
                            "MODBUS_REFERENCE",
                            ModbusDataType.BOOL,
                            ByteOrder.BIG_ENDIAN,
                            WordOrder.BIG_ENDIAN,
                            "ABCD",
                            null,
                            null,
                            StringCharset.UTF_8,
                            1.0,
                            0.0,
                            null,
                            "",
                            PointAccess.READ_WRITE,
                            null,
                            true,
                            "线圈示例");
            rowIndex =
                    addExamplePoint(
                            sheet,
                            styles,
                            rowIndex,
                            "示例设备",
                            "ENERGY",
                            "累计电量",
                            PointArea.INPUT_REGISTER,
                            "30001",
                            "MODBUS_REFERENCE",
                            ModbusDataType.UINT32,
                            ByteOrder.BIG_ENDIAN,
                            WordOrder.LITTLE_ENDIAN,
                            "CDAB",
                            null,
                            2,
                            StringCharset.UTF_8,
                            0.1,
                            0.0,
                            1,
                            "kWh",
                            PointAccess.READ_ONLY,
                            null,
                            true,
                            "UINT32 CDAB + 缩放示例");
        }
        finishSheet(sheet, styles, 0, Math.max(0, rowIndex - 1), POINT_HEADERS.length);
        setWidths(
                sheet,
                new int[] {
                    22, 18, 20, 18, 12, 18, 16, 16, 16, 10, 10, 14, 14, 12, 12, 10, 12, 12, 12, 10, 40
                });
    }

    private int addExamplePoint(
            Sheet sheet,
            Styles styles,
            int rowIndex,
            String device,
            String code,
            String name,
            PointArea area,
            String address,
            String addressFormat,
            ModbusDataType dataType,
            ByteOrder byteOrder,
            WordOrder wordOrder,
            String layout,
            Integer bitIndex,
            Integer registerLength,
            StringCharset charset,
            double scale,
            double offset,
            Integer decimals,
            String unit,
            PointAccess access,
            Integer unitId,
            boolean enabled,
            String description) {
        Row row = sheet.createRow(rowIndex);
        writePoint(
                row,
                device,
                code,
                name,
                area,
                address,
                addressFormat,
                dataType,
                byteOrder,
                wordOrder,
                layout,
                bitIndex,
                registerLength,
                charset,
                scale,
                offset,
                decimals,
                unit,
                access,
                unitId,
                enabled,
                description);
        styleExampleRow(row, styles, POINT_HEADERS.length);
        return rowIndex + 1;
    }

    private void writePoint(
            Row row,
            String device,
            String code,
            String name,
            PointArea area,
            String address,
            String addressFormat,
            ModbusDataType dataType,
            ByteOrder byteOrder,
            WordOrder wordOrder,
            String layout,
            Integer bitIndex,
            Integer registerLength,
            StringCharset charset,
            double scale,
            double offset,
            Integer decimals,
            String unit,
            PointAccess access,
            Integer unitId,
            boolean enabled,
            String description) {
        write(
                row,
                device,
                code,
                name,
                area.name(),
                address,
                addressFormat,
                dataType.name(),
                byteOrder.name(),
                wordOrder.name(),
                layout,
                bitIndex,
                registerLength,
                charset.name(),
                scale,
                offset,
                decimals,
                unit,
                access.name(),
                unitId,
                enabled ? "是" : "否",
                description);
    }

    private int writeHeader(Sheet sheet, Styles styles, String[] headers) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }
        return 1;
    }

    private void finishSheet(Sheet sheet, Styles styles, int headerRow, int lastRow, int columns) {
        sheet.createFreezePane(0, headerRow + 1);
        if (lastRow > headerRow) {
            sheet.setAutoFilter(new CellRangeAddress(headerRow, lastRow, 0, columns - 1));
        }
        for (int i = 1; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                for (int c = 0; c < columns; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellStyle() != styles.example) {
                        cell.setCellStyle(styles.body);
                    }
                }
            }
        }
    }

    private void setWidths(Sheet sheet, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, Math.min(255, widths[i]) * 256);
        }
    }

    private void styleExampleRow(Row row, Styles styles, int columns) {
        for (int c = 0; c < columns; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) {
                cell = row.createCell(c);
            }
            cell.setCellStyle(styles.example);
        }
    }

    private void writeInstructions(Workbook workbook, Styles styles) {
        Sheet sheet = workbook.createSheet("说明");
        int rowIndex = 0;
        Row title = sheet.createRow(rowIndex++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Modbus Web 设备与点位导入说明");
        titleCell.setCellStyle(styles.title);

        String[][] sections = {
            {
                "基本规则",
                "1. 设备表按“设备名称”唯一标识；点位表按“设备名称 + 点位编码”唯一标识。同名记录会被更新。\n"
                        + "2. 导入是全量校验后一次性执行：只要存在错误，整份文件不会写入数据库。\n"
                        + "3. “点位编码”留空时自动生成 P_行号；建议使用字母、数字、下划线、点、冒号、横线。\n"
                        + "4. 模板中的黄色示例行仅供格式参考，正式导入前请替换或删除。"
            },
            {
                "地址与地址格式",
                "地址支持两种表示：\n"
                        + "• MODBUS_REFERENCE：传统 5 位参考地址，例如保持寄存器 40001 表示协议地址 0，输入寄存器 30001 表示 0，线圈 00001 表示 0，离散输入 10001 表示 0。\n"
                        + "• ZERO_BASED：协议地址，从 0 开始，范围 0..65535。\n"
                        + "地址格式留空时会按区域和数值自动判断：保持寄存器 >= 40001、输入寄存器 >= 30001、离散输入 >= 10001 时按参考地址处理；线圈建议明确填写地址格式。"
            },
            {
                "区域 / 功能码",
                "COIL = 01 线圈\nDISCRETE_INPUT = 02 离散输入\nHOLDING_REGISTER = 03 保持寄存器\nINPUT_REGISTER = 04 输入寄存器\n"
                        + "线圈和离散输入只能使用 BOOL 类型；只有线圈和保持寄存器可以写入。"
            },
            {
                "数据类型",
                "BOOL（1 位）、INT16/UINT16（1 寄存器）、INT32/UINT32/FLOAT32（2 寄存器）、INT64/UINT64/FLOAT64（4 寄存器）、STRING/HEX（寄存器数量由“寄存器数量”列指定，最大 123/125）。\n"
                        + "寄存器内的 BOOL 请填写“位索引”0..15。STRING 请填写字符串编码；HEX 请填写“AA BB”或“AABB”格式。"
            },
            {
                "字节序 / 字序 / 端序",
                "常用四种浮点布局：\n"
                        + "ABCD：字节序=大端，字序=大端\n"
                        + "BADC：字节序=小端，字序=大端\n"
                        + "CDAB：字节序=大端，字序=小端\n"
                        + "DCBA：字节序=小端，字序=小端\n"
                        + "“端序”列填写 ABCD/BADC/CDAB/DCBA 可一次设置；若同时填写字节序/字序，则以明确的字节序/字序列为准。"
            },
            {
                "缩放与偏移",
                "工程值 = 原始值 × 缩放系数 + 偏移量。写入时自动执行逆运算。注意整数类型会四舍五入到整数原始值。"
            },
            {
                "Modbus TCP Security",
                "协议填写 MODBUS_TCP_SECURE（或包含 TLS / SECURE / 安全）。需要填写客户端证书、客户端私钥、CA 证书的本地绝对路径，以及私钥密码（如私钥未加密可留空）。"
                        + "服务端必须支持双向 TLS（客户端证书认证），这是 Modbus Security 规范要求。私钥支持 PKCS#8、加密 PKCS#8、PKCS#1 RSA/EC PEM。"
            },
            {
                "导出与再次导入",
                "导出的“设备”和“点位”工作表可以直接修改后重新导入。出于安全考虑，私钥密码不会导出；如果私钥已加密，重新导入后需要补填密码。"
            }
        };

        for (String[] section : sections) {
            rowIndex++;
            Row header = sheet.createRow(rowIndex++);
            Cell headerCell = header.createCell(0);
            headerCell.setCellValue(section[0]);
            headerCell.setCellStyle(styles.instructionHeader);
            Row body = sheet.createRow(rowIndex++);
            Cell bodyCell = body.createCell(0);
            bodyCell.setCellValue(section[1]);
            bodyCell.setCellStyle(styles.instructionBody);
            body.setHeightInPoints(70);
        }
        sheet.setColumnWidth(0, 120 * 256);
        sheet.setColumnWidth(1, 2 * 256);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportReport importWorkbook(byte[] content) {
        List<ImportIssue> issues = new ArrayList<>();
        if (content == null || content.length == 0) {
            return failedReport(List.of(new ImportIssue("ERROR", "", 0, "", "文件为空")));
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            HeaderIndex pointHeader = null;
            HeaderIndex deviceHeader = null;
            Sheet pointSheet = null;
            Sheet deviceSheet = null;

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = normalize(sheet.getSheetName());
                if (sheetName.contains("说明")
                        || sheetName.contains("instruction")
                        || sheetName.contains("readme")) {
                    continue;
                }
                HeaderIndex header = findHeader(sheet, formatter, evaluator);
                if (header == null) {
                    continue;
                }
                boolean pointish =
                        header.has("点位编码", "点位名称", "功能码", "寄存器类型")
                                || sheetName.contains("点位")
                                || sheetName.contains("point");
                if (pointish && pointSheet == null) {
                    pointSheet = sheet;
                    pointHeader = header;
                } else if (!pointish
                        && deviceSheet == null
                        && header.has("设备名称")
                        && header.has("ip地址", "ip", "主机", "host", "ipaddress")) {
                    deviceSheet = sheet;
                    deviceHeader = header;
                }
                if (pointSheet == null && deviceSheet == null) {
                    if (header.has("设备名称")
                            && header.has("ip地址", "ip", "主机", "host", "ipaddress")) {
                        deviceSheet = sheet;
                        deviceHeader = header;
                    }
                }
            }

            if (pointSheet == null && deviceSheet == null) {
                return failedReport(
                        List.of(
                                new ImportIssue(
                                        "ERROR",
                                        "",
                                        0,
                                        "",
                                        "未找到可识别的“设备”或“点位”工作表")));
            }

            List<ParsedDevice> parsedDevices = new ArrayList<>();
            List<ParsedPoint> parsedPoints = new ArrayList<>();
            Map<String, DeviceSeed> deviceSeeds = new LinkedHashMap<>();

            if (deviceSheet != null) {
                parseDevices(deviceSheet, deviceHeader, formatter, evaluator, issues, parsedDevices);
            }
            if (pointSheet != null) {
                parsePoints(
                        pointSheet,
                        pointHeader,
                        formatter,
                        evaluator,
                        issues,
                        parsedPoints,
                        deviceSeeds,
                        deviceSheet == null);
            }

            if (issues.stream().anyMatch(issue -> "ERROR".equals(issue.severity()))) {
                return failedReport(issues);
            }

            Map<String, ModbusDevice> existingByName = new LinkedHashMap<>();
            for (ModbusDevice device : deviceRepository.findAllByOrderByNameAsc()) {
                existingByName.put(device.getName().toLowerCase(Locale.ROOT), device);
            }

            Map<String, ParsedDevice> fileDevices = new LinkedHashMap<>();
            for (ParsedDevice parsed : parsedDevices) {
                String key = parsed.request().name().toLowerCase(Locale.ROOT);
                if (fileDevices.putIfAbsent(key, parsed) != null) {
                    addIssue(
                            issues,
                            "ERROR",
                            "设备",
                            parsed.row(),
                            "设备名称",
                            "设备名称在文件中重复: " + parsed.request().name());
                }
            }

            for (ParsedDevice parsed : parsedDevices) {
                ModbusDevice existing =
                        existingByName.get(parsed.request().name().toLowerCase(Locale.ROOT));
                validateDeviceRequest(
                        issues, parsed.row(), parsed.request(), existing == null ? null : existing.getId());
            }

            for (DeviceSeed seed : deviceSeeds.values()) {
                String key = seed.request().name().toLowerCase(Locale.ROOT);
                ModbusDevice existing = existingByName.get(key);
                if (existing != null) {
                    addIssue(
                            issues,
                            "WARNING",
                            "点位",
                            seed.row(),
                            "设备名称",
                            "设备已存在，平铺导入模式不会更新设备配置: " + seed.request().name());
                } else if (!fileDevices.containsKey(key)) {
                    validateDeviceRequest(issues, seed.row(), seed.request(), null);
                }
            }

            Map<String, Long> deviceIds = new LinkedHashMap<>();
            existingByName.forEach((key, device) -> deviceIds.put(key, device.getId()));
            for (ParsedDevice parsed : parsedDevices) {
                ModbusDevice existing =
                        existingByName.get(parsed.request().name().toLowerCase(Locale.ROOT));
                deviceIds.put(parsed.request().name().toLowerCase(Locale.ROOT),
                        existing == null ? null : existing.getId());
            }
            for (DeviceSeed seed : deviceSeeds.values()) {
                String key = seed.request().name().toLowerCase(Locale.ROOT);
                if (!deviceIds.containsKey(key)) {
                    deviceIds.put(key, null);
                }
            }

            Map<String, Long> uniquePointKeys = new LinkedHashMap<>();
            for (ParsedPoint point : parsedPoints) {
                String key =
                        point.deviceName().toLowerCase(Locale.ROOT)
                                + "\u0000"
                                + point.request().code().toLowerCase(Locale.ROOT);
                if (uniquePointKeys.putIfAbsent(key, (long) point.row()) != null) {
                    addIssue(
                            issues,
                            "ERROR",
                            "点位",
                            point.row(),
                            "点位编码",
                            "同一设备下点位编码在文件中重复: " + point.request().code());
                }
                Long deviceId = deviceIds.get(point.deviceName().toLowerCase(Locale.ROOT));
                if (!deviceIds.containsKey(point.deviceName().toLowerCase(Locale.ROOT))) {
                    addIssue(
                            issues,
                            "ERROR",
                            "点位",
                            point.row(),
                            "设备名称",
                            "找不到设备: " + point.deviceName());
                    continue;
                }
                Long existingPointId = null;
                if (deviceId != null) {
                    existingPointId =
                            pointRepository
                                    .findByDeviceIdAndCodeIgnoreCase(
                                            deviceId, point.request().code())
                                    .map(ModbusPoint::getId)
                                    .orElse(null);
                }
                validatePointRequest(
                        issues,
                        point.row(),
                        deviceId == null ? -1L : deviceId,
                        existingPointId,
                        point.request());
            }

            if (issues.stream().anyMatch(issue -> "ERROR".equals(issue.severity()))) {
                return failedReport(issues);
            }

            int devicesCreated = 0;
            int devicesUpdated = 0;
            int pointsCreated = 0;
            int pointsUpdated = 0;

            for (ParsedDevice parsed : parsedDevices) {
                ModbusDevice existing =
                        existingByName.get(parsed.request().name().toLowerCase(Locale.ROOT));
                if (existing == null) {
                    try {
                        var created = deviceService.create(parsed.request());
                        deviceIds.put(created.name().toLowerCase(Locale.ROOT), created.id());
                        devicesCreated++;
                    } catch (RuntimeException e) {
                        throw importFailure(parsed.row(), "保存设备失败: " + messageOf(e), e);
                    }
                } else {
                    try {
                        deviceService.update(existing.getId(), parsed.request());
                        devicesUpdated++;
                    } catch (RuntimeException e) {
                        throw importFailure(parsed.row(), "更新设备失败: " + messageOf(e), e);
                    }
                }
            }

            for (DeviceSeed seed : deviceSeeds.values()) {
                String key = seed.request().name().toLowerCase(Locale.ROOT);
                if (deviceIds.get(key) != null) {
                    continue;
                }
                try {
                    var created = deviceService.create(seed.request());
                    deviceIds.put(key, created.id());
                    devicesCreated++;
                } catch (RuntimeException e) {
                    throw importFailure(seed.row(), "创建设备失败: " + messageOf(e), e);
                }
            }

            for (ParsedPoint parsed : parsedPoints) {
                Long deviceId = deviceIds.get(parsed.deviceName().toLowerCase(Locale.ROOT));
                if (deviceId == null) {
                    throw importFailure(parsed.row(), "内部错误：设备 ID 未解析", null);
                }
                var existing =
                        pointRepository.findByDeviceIdAndCodeIgnoreCase(
                                deviceId, parsed.request().code());
                try {
                    if (existing.isPresent()) {
                        pointService.update(existing.get().getId(), parsed.request());
                        pointsUpdated++;
                    } else {
                        pointService.create(deviceId, parsed.request());
                        pointsCreated++;
                    }
                } catch (RuntimeException e) {
                    throw importFailure(parsed.row(), "保存点位失败: " + messageOf(e), e);
                }
            }

            String message =
                    "导入成功：设备新增 "
                            + devicesCreated
                            + "，更新 "
                            + devicesUpdated
                            + "；点位新增 "
                            + pointsCreated
                            + "，更新 "
                            + pointsUpdated;
            return new ImportReport(
                    true,
                    message,
                    Instant.now(),
                    devicesCreated,
                    devicesUpdated,
                    pointsCreated,
                    pointsUpdated,
                    0,
                    issues);
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            return failedReport(
                    List.of(new ImportIssue("ERROR", "", 0, "", "无法读取 Excel 文件: " + e.getMessage())));
        } catch (RuntimeException e) {
            throw new BadRequestException("Excel 导入失败，已回滚: " + messageOf(e), Map.of());
        }
    }

    private void parseDevices(
            Sheet sheet,
            HeaderIndex header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            List<ImportIssue> issues,
            List<ParsedDevice> out) {
        if (!header.has("设备名称", "设备名", "device", "devicename")) {
            addIssue(issues, "ERROR", sheet.getSheetName(), header.row() + 1, "设备名称", "缺少设备名称列");
            return;
        }
        if (!header.has("ip地址", "ip", "主机", "host", "ipaddress")) {
            addIssue(issues, "ERROR", sheet.getSheetName(), header.row() + 1, "IP地址", "缺少 IP 地址列");
            return;
        }
        int dataRows = 0;
        for (int r = header.row() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isEmptyRow(row, formatter, evaluator)) {
                continue;
            }
            if (++dataRows > maxRows) {
                addIssue(issues, "ERROR", sheet.getSheetName(), r + 1, "", "数据行数超过限制 " + maxRows);
                return;
            }
            try {
                String name = required(header.value(row, formatter, evaluator, "设备名称", "设备名", "device", "devicename"), "设备名称");
                String host = required(header.value(row, formatter, evaluator, "ip地址", "ip", "主机", "host", "ipaddress"), "IP地址");
                ProtocolType protocol =
                        parseProtocol(header.value(row, formatter, evaluator, "协议", "protocol", "协议类型"));
                Integer port =
                        parseInteger(
                                header.value(row, formatter, evaluator, "端口", "port"),
                                "端口",
                                protocol.getDefaultPort());
                Integer unitId =
                        parseInteger(
                                header.value(row, formatter, evaluator, "从站地址", "站号", "单元号", "unitid", "slaveid", "unit"),
                                "从站地址",
                                1);
                Integer connectTimeout =
                        parseInteger(
                                header.value(row, formatter, evaluator, "连接超时(ms)", "连接超时", "connecttimeout"),
                                "连接超时",
                                3000);
                Integer requestTimeout =
                        parseInteger(
                                header.value(row, formatter, evaluator, "请求超时(ms)", "请求超时", "requesttimeout"),
                                "请求超时",
                                3000);
                boolean enabled =
                        parseBoolean(
                                header.value(row, formatter, evaluator, "启用", "使能", "enabled"),
                                true,
                                "启用");
                String cert = header.value(row, formatter, evaluator, "tls客户端证书", "客户端证书", "clientcert");
                String key = header.value(row, formatter, evaluator, "tls客户端私钥", "客户端私钥", "clientkey");
                String password = header.value(row, formatter, evaluator, "私钥密码", "keypassword");
                String ca = header.value(row, formatter, evaluator, "tls ca证书", "ca证书", "cacert");
                boolean verify =
                        parseBoolean(
                                header.value(row, formatter, evaluator, "校验主机名", "verifyhostname"),
                                true,
                                "校验主机名");
                String description =
                        header.value(row, formatter, evaluator, "描述", "备注", "description", "remark");
                out.add(
                        new ParsedDevice(
                                r + 1,
                                new DeviceRequest(
                                        name,
                                        protocol,
                                        host,
                                        port,
                                        unitId,
                                        connectTimeout,
                                        requestTimeout,
                                        enabled,
                                        cert,
                                        key,
                                        password,
                                        ca,
                                        verify,
                                        description)));
            } catch (RowException e) {
                addIssue(issues, "ERROR", sheet.getSheetName(), r + 1, e.field, e.getMessage());
            }
        }
    }

    private void parsePoints(
            Sheet sheet,
            HeaderIndex header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            List<ImportIssue> issues,
            List<ParsedPoint> out,
            Map<String, DeviceSeed> seeds,
            boolean flatMode) {
        if (!header.has("设备名称", "设备名", "device", "devicename")) {
            addIssue(issues, "ERROR", sheet.getSheetName(), header.row() + 1, "设备名称", "缺少设备名称列");
            return;
        }
        if (!header.has("点位名称", "点名称", "pointname", "name")) {
            addIssue(issues, "ERROR", sheet.getSheetName(), header.row() + 1, "点位名称", "缺少点位名称列");
            return;
        }
        if (!header.has("区域", "功能码", "寄存器类型", "area", "functioncode", "registertype", "fc")) {
            addIssue(issues, "ERROR", sheet.getSheetName(), header.row() + 1, "区域", "缺少区域/功能码列");
            return;
        }
        if (!header.has("地址", "address", "寄存器地址", "协议地址")) {
            addIssue(issues, "ERROR", sheet.getSheetName(), header.row() + 1, "地址", "缺少地址列");
            return;
        }
        if (!header.has("数据类型", "类型", "datatype", "type")) {
            addIssue(issues, "ERROR", sheet.getSheetName(), header.row() + 1, "数据类型", "缺少数据类型列");
            return;
        }

        int dataRows = 0;
        for (int r = header.row() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isEmptyRow(row, formatter, evaluator)) {
                continue;
            }
            if (++dataRows > maxRows) {
                addIssue(issues, "ERROR", sheet.getSheetName(), r + 1, "", "数据行数超过限制 " + maxRows);
                return;
            }
            try {
                String deviceName =
                        required(
                                header.value(row, formatter, evaluator, "设备名称", "设备名", "device", "devicename"),
                                "设备名称");
                String code =
                        header.value(row, formatter, evaluator, "点位编码", "编码", "点号", "code", "pointcode", "tag");
                if (code == null || code.isBlank()) {
                    code = "P_" + (r + 1);
                }
                String name =
                        required(
                                header.value(row, formatter, evaluator, "点位名称", "点名称", "pointname", "name"),
                                "点位名称");
                PointArea area =
                        parseArea(
                                required(
                                        header.value(row, formatter, evaluator, "区域", "功能码", "寄存器类型", "area", "functioncode", "registertype", "fc"),
                                        "区域"));
                ModbusDataType dataType =
                        parseDataType(
                                required(
                                        header.value(row, formatter, evaluator, "数据类型", "类型", "datatype", "type"),
                                        "数据类型"));
                AddressNotation notation =
                        parseAddressNotation(
                                header.value(row, formatter, evaluator, "地址格式", "地址表示法", "addressnotation", "addressformat"));
                int addressValue =
                        parseAddress(
                                required(
                                        header.value(row, formatter, evaluator, "地址", "address", "寄存器地址", "协议地址"),
                                        "地址"),
                                area,
                                notation);

                ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
                WordOrder wordOrder = WordOrder.BIG_ENDIAN;
                String layout = header.value(row, formatter, evaluator, "端序", "字节字序", "layout", "endianness", "bytewordorder");
                if (layout != null && !layout.isBlank()) {
                    String normalizedLayout = normalize(layout);
                    switch (normalizedLayout) {
                        case "abcd" -> {
                            byteOrder = ByteOrder.BIG_ENDIAN;
                            wordOrder = WordOrder.BIG_ENDIAN;
                        }
                        case "badc" -> {
                            byteOrder = ByteOrder.LITTLE_ENDIAN;
                            wordOrder = WordOrder.BIG_ENDIAN;
                        }
                        case "cdab" -> {
                            byteOrder = ByteOrder.BIG_ENDIAN;
                            wordOrder = WordOrder.LITTLE_ENDIAN;
                        }
                        case "dcba" -> {
                            byteOrder = ByteOrder.LITTLE_ENDIAN;
                            wordOrder = WordOrder.LITTLE_ENDIAN;
                        }
                        default -> throw new RowException("端序", "端序必须是 ABCD/BADC/CDAB/DCBA 之一: " + layout);
                    }
                }
                String byteOrderText =
                        header.value(row, formatter, evaluator, "字节序", "byteorder");
                if (byteOrderText != null && !byteOrderText.isBlank()) {
                    byteOrder = parseByteOrder(byteOrderText);
                }
                String wordOrderText = header.value(row, formatter, evaluator, "字序", "wordorder");
                if (wordOrderText != null && !wordOrderText.isBlank()) {
                    wordOrder = parseWordOrder(wordOrderText);
                }

                Integer bitIndex =
                        parseInteger(
                                header.value(row, formatter, evaluator, "位索引", "位", "bit", "bitindex"),
                                "位索引",
                                null);
                Integer registerLength =
                        parseInteger(
                                header.value(row, formatter, evaluator, "寄存器数量", "寄存器个数", "寄存器数", "长度", "registerlength", "registercount"),
                                "寄存器数量",
                                null);
                String charsetText =
                        header.value(row, formatter, evaluator, "字符串编码", "编码格式", "字符集", "charset", "stringcharset");
                StringCharset charset =
                        charsetText == null || charsetText.isBlank()
                                ? StringCharset.UTF_8
                                : parseCharset(charsetText);
                Double scale =
                        parseDouble(
                                header.value(row, formatter, evaluator, "缩放系数", "系数", "scale", "multiplier"),
                                "缩放系数",
                                1.0);
                Double offset =
                        parseDouble(
                                header.value(row, formatter, evaluator, "偏移量", "偏移", "offset"),
                                "偏移量",
                                0.0);
                Integer decimals =
                        parseInteger(
                                header.value(row, formatter, evaluator, "小数位", "decimals"),
                                "小数位",
                                null);
                String unit = header.value(row, formatter, evaluator, "单位", "unit", "工程单位");
                PointAccess access =
                        parseAccess(
                                header.value(row, formatter, evaluator, "读写权限", "读写", "权限", "access", "rw"));
                Integer pointUnitId =
                        parseInteger(
                                header.value(row, formatter, evaluator, "从站地址", "站号", "单元号", "unitid", "slaveid"),
                                "从站地址",
                                null);
                boolean enabled =
                        parseBoolean(
                                header.value(row, formatter, evaluator, "启用", "使能", "enabled"),
                                true,
                                "启用");
                String description =
                        header.value(row, formatter, evaluator, "描述", "备注", "description", "remark");

                out.add(
                        new ParsedPoint(
                                r + 1,
                                deviceName,
                                new PointRequest(
                                        code.trim(),
                                        name,
                                        area,
                                        addressValue,
                                        pointUnitId,
                                        dataType,
                                        byteOrder,
                                        wordOrder,
                                        bitIndex,
                                        registerLength,
                                        charset,
                                        scale,
                                        offset,
                                        decimals,
                                        unit,
                                        access,
                                        enabled,
                                        description)));

                if (flatMode) {
                    String key = deviceName.toLowerCase(Locale.ROOT);
                    if (!seeds.containsKey(key)) {
                        String host =
                                header.value(row, formatter, evaluator, "ip地址", "ip", "主机", "host", "ipaddress");
                        if (host != null && !host.isBlank()) {
                            ProtocolType protocol =
                                    parseProtocol(
                                            header.value(row, formatter, evaluator, "协议", "protocol", "协议类型"));
                            Integer port =
                                    parseInteger(
                                            header.value(row, formatter, evaluator, "端口", "port"),
                                            "端口",
                                            protocol.getDefaultPort());
                            Integer unitId =
                                    parseInteger(
                                            header.value(row, formatter, evaluator, "从站地址", "站号", "单元号", "unitid", "slaveid", "unit"),
                                            "从站地址",
                                            1);
                            seeds.put(
                                    key,
                                    new DeviceSeed(
                                            r + 1,
                                            new DeviceRequest(
                                                    deviceName,
                                                    protocol,
                                                    host,
                                                    port,
                                                    unitId,
                                                    3000,
                                                    3000,
                                                    true,
                                                    header.value(row, formatter, evaluator, "tls客户端证书", "客户端证书", "clientcert"),
                                                    header.value(row, formatter, evaluator, "tls客户端私钥", "客户端私钥", "clientkey"),
                                                    header.value(row, formatter, evaluator, "私钥密码", "keypassword"),
                                                    header.value(row, formatter, evaluator, "tls ca证书", "ca证书", "cacert"),
                                                    true,
                                                    header.value(row, formatter, evaluator, "描述", "备注", "description", "remark"))));
                        }
                    }
                }
            } catch (RowException e) {
                addIssue(issues, "ERROR", sheet.getSheetName(), r + 1, e.field, e.getMessage());
            }
        }
    }

    private void validateDeviceRequest(
            List<ImportIssue> issues, int row, DeviceRequest request, Long existingId) {
        for (String message : beanValidation(request)) {
            addIssue(issues, "ERROR", "设备", row, "", message);
        }
        try {
            deviceService.validateForImport(request, existingId);
        } catch (BadRequestException e) {
            addIssue(issues, "ERROR", "设备", row, "", e.getMessage());
        }
    }

    private void validatePointRequest(
            List<ImportIssue> issues,
            int row,
            Long deviceId,
            Long existingId,
            PointRequest request) {
        for (String message : beanValidation(request)) {
            addIssue(issues, "ERROR", "点位", row, "", message);
        }
        try {
            pointService.validateForImport(deviceId, existingId, request);
        } catch (BadRequestException e) {
            addIssue(issues, "ERROR", "点位", row, "", e.getMessage());
        }
    }

    private <T> List<String> beanValidation(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .toList();
    }

    private static ImportReport failedReport(List<ImportIssue> issues) {
        long errors = issues.stream().filter(i -> "ERROR".equals(i.severity())).count();
        return new ImportReport(
                false,
                "导入未执行：发现 " + errors + " 个错误，请修正后重试",
                Instant.now(),
                0,
                0,
                0,
                0,
                0,
                issues);
    }

    private static BadRequestException importFailure(int row, String message, Throwable cause) {
        return new BadRequestException("第 " + row + " 行: " + message, Map.of());
    }

    private static void addIssue(
            List<ImportIssue> issues,
            String severity,
            String sheet,
            int row,
            String field,
            String message) {
        issues.add(new ImportIssue(severity, sheet, row, field == null ? "" : field, message));
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

    private HeaderIndex findHeader(
            Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int last = Math.min(sheet.getLastRowNum(), 19);
        HeaderIndex best = null;
        int bestScore = 0;
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    continue;
                }
                String text = formatter.formatCellValue(cell, evaluator);
                if (text != null && !text.isBlank()) {
                    columns.putIfAbsent(normalize(text), c);
                }
            }
            if (columns.isEmpty()) {
                continue;
            }
            HeaderIndex index = new HeaderIndex(sheet, r, columns);
            int score = 0;
            if (index.has("点位名称", "点名称", "pointname")) {
                score += 5;
            }
            if (index.has("点位编码", "编码", "code", "pointcode")) {
                score += 3;
            }
            if (index.has("区域", "功能码", "area", "functioncode", "registertype")) {
                score += 3;
            }
            if (index.has("地址", "address", "寄存器地址")) {
                score += 2;
            }
            if (index.has("设备名称", "设备名", "device", "devicename")) {
                score += 3;
            }
            if (index.has("ip地址", "ip", "主机", "host", "ipaddress")) {
                score += 2;
            }
            if (score > bestScore) {
                bestScore = score;
                best = index;
            }
        }
        return bestScore >= 5 ? best : null;
    }

    private static boolean isEmptyRow(
            Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return true;
        }
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = formatter.formatCellValue(cell, evaluator);
                if (value != null && !value.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ProtocolType parseProtocol(String value) {
        if (value == null || value.isBlank()) {
            return ProtocolType.MODBUS_TCP;
        }
        String normalized = normalize(value);
        if (normalized.contains("secure")
                || normalized.contains("tls")
                || normalized.contains("ssl")
                || normalized.contains("安全")) {
            return ProtocolType.MODBUS_TCP_SECURE;
        }
        return ProtocolType.MODBUS_TCP;
    }

    private static PointArea parseArea(String value) {
        String normalized = normalize(value);
        if (normalized.equals("1")
                || normalized.equals("01")
                || normalized.equals("fc01")
                || normalized.equals("coil")
                || normalized.contains("线圈")
                || normalized.equals("do")) {
            return PointArea.COIL;
        }
        if (normalized.equals("2")
                || normalized.equals("02")
                || normalized.equals("fc02")
                || normalized.contains("discrete")
                || normalized.contains("离散")
                || normalized.equals("di")) {
            return PointArea.DISCRETE_INPUT;
        }
        if (normalized.equals("3")
                || normalized.equals("03")
                || normalized.equals("fc03")
                || normalized.contains("holding")
                || normalized.contains("保持")) {
            return PointArea.HOLDING_REGISTER;
        }
        if (normalized.equals("4")
                || normalized.equals("04")
                || normalized.equals("fc04")
                || normalized.contains("input")
                || normalized.contains("输入寄存器")) {
            return PointArea.INPUT_REGISTER;
        }
        throw new RowException("区域", "无法识别区域/功能码: " + value);
    }

    private static AddressNotation parseAddressNotation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalize(value);
        if (normalized.contains("zero") || normalized.contains("0based") || normalized.contains("协议地址")) {
            return AddressNotation.ZERO_BASED;
        }
        if (normalized.contains("reference")
                || normalized.contains("modbus")
                || normalized.contains("参考")
                || normalized.contains("4x")
                || normalized.contains("3x")) {
            return AddressNotation.MODBUS_REFERENCE;
        }
        throw new RowException("地址格式", "无法识别地址格式: " + value);
    }

    private static int parseAddress(
            String value, PointArea area, AddressNotation notation) {
        String cleaned = value.trim().replace(",", "");
        try {
            long raw;
            if (cleaned.toLowerCase(Locale.ROOT).startsWith("0x")) {
                raw = Long.parseLong(cleaned.substring(2), 16);
                notation = AddressNotation.ZERO_BASED;
            } else {
                raw = Long.parseLong(cleaned);
            }
            AddressNotation effective = notation;
            if (effective == null) {
                if (area == PointArea.HOLDING_REGISTER
                        && raw >= 40001
                        && raw <= 40001 + 65535) {
                    effective = AddressNotation.MODBUS_REFERENCE;
                } else if (area == PointArea.INPUT_REGISTER
                        && raw >= 30001
                        && raw <= 30001 + 65535) {
                    effective = AddressNotation.MODBUS_REFERENCE;
                } else if (area == PointArea.DISCRETE_INPUT
                        && raw >= 10001
                        && raw <= 10001 + 65535) {
                    effective = AddressNotation.MODBUS_REFERENCE;
                } else {
                    effective = AddressNotation.ZERO_BASED;
                }
            }
            long address =
                    effective == AddressNotation.MODBUS_REFERENCE
                            ? raw - area.getReferenceBase()
                            : raw;
            if (address < 0 || address > 65535) {
                throw new RowException(
                        "地址",
                        "地址超出范围: " + value + "（协议地址必须为 0..65535）");
            }
            return (int) address;
        } catch (NumberFormatException e) {
            throw new RowException("地址", "地址不是有效数字: " + value);
        }
    }

    private static ModbusDataType parseDataType(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "bool", "boolean", "bit", "布尔", "开关" -> ModbusDataType.BOOL;
            case "int16", "short", "s16", "int" -> ModbusDataType.INT16;
            case "uint16", "word", "unsigned16", "u16" -> ModbusDataType.UINT16;
            case "int32", "dint", "long", "s32" -> ModbusDataType.INT32;
            case "uint32", "dword", "udint", "unsigned32", "u32" -> ModbusDataType.UINT32;
            case "int64", "lint", "s64", "longlong" -> ModbusDataType.INT64;
            case "uint64", "ulint", "unsigned64", "u64" -> ModbusDataType.UINT64;
            case "float32", "float", "real", "fp32", "单精度" -> ModbusDataType.FLOAT32;
            case "float64", "double", "lreal", "fp64", "双精度" -> ModbusDataType.FLOAT64;
            case "string", "text", "char", "字符串" -> ModbusDataType.STRING;
            case "hex", "raw", "bytes", "十六进制", "原始数据" -> ModbusDataType.HEX;
            default -> throw new RowException("数据类型", "无法识别数据类型: " + value);
        };
    }

    private static ByteOrder parseByteOrder(String value) {
        String normalized = normalize(value);
        if (normalized.contains("big")
                || normalized.contains("大端")
                || normalized.equals("be")
                || normalized.contains("高字节")) {
            return ByteOrder.BIG_ENDIAN;
        }
        if (normalized.contains("little")
                || normalized.contains("小端")
                || normalized.equals("le")
                || normalized.contains("低字节")) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        throw new RowException("字节序", "无法识别字节序: " + value);
    }

    private static WordOrder parseWordOrder(String value) {
        String normalized = normalize(value);
        if (normalized.contains("big")
                || normalized.contains("大端")
                || normalized.contains("高字")
                || normalized.equals("be")) {
            return WordOrder.BIG_ENDIAN;
        }
        if (normalized.contains("little")
                || normalized.contains("小端")
                || normalized.contains("低字")
                || normalized.equals("le")) {
            return WordOrder.LITTLE_ENDIAN;
        }
        throw new RowException("字序", "无法识别字序: " + value);
    }

    private static PointAccess parseAccess(String value) {
        if (value == null || value.isBlank()) {
            return PointAccess.READ_ONLY;
        }
        String normalized = normalize(value);
        if (normalized.equals("rw")
                || normalized.contains("readwrite")
                || normalized.contains("读写")) {
            return PointAccess.READ_WRITE;
        }
        if (normalized.equals("w")
                || normalized.equals("wo")
                || normalized.contains("writeonly")
                || normalized.contains("只写")) {
            return PointAccess.WRITE_ONLY;
        }
        if (normalized.equals("r")
                || normalized.equals("ro")
                || normalized.contains("readonly")
                || normalized.contains("只读")) {
            return PointAccess.READ_ONLY;
        }
        throw new RowException("读写权限", "无法识别读写权限: " + value);
    }

    private static StringCharset parseCharset(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "ascii" -> StringCharset.ASCII;
            case "utf8" -> StringCharset.UTF_8;
            case "utf16be" -> StringCharset.UTF_16BE;
            case "utf16le" -> StringCharset.UTF_16LE;
            default -> throw new RowException("字符串编码", "无法识别字符串编码: " + value);
        };
    }

    private static Integer parseInteger(String value, String field, Integer defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            BigDecimal decimal = new BigDecimal(value.trim().replace(",", ""));
            return decimal.intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new RowException(field, field + " 不是有效整数: " + value);
        }
    }

    private static Double parseDouble(String value, String field, Double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new RowException(field, field + " 不是有效数字: " + value);
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue, String field) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return ModbusValueCodec.parseBoolean(value);
        } catch (IllegalArgumentException e) {
            throw new RowException(field, field + " 不是有效布尔值（是/否、true/false、1/0）: " + value);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new RowException(field, field + "不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_*()\\[\\]{}:：/\\\\\\-]", "")
                .replace("（）", "");
    }

    private record ParsedDevice(int row, DeviceRequest request) {}

    private record ParsedPoint(int row, String deviceName, PointRequest request) {}

    private record DeviceSeed(int row, DeviceRequest request) {}

    private static final class RowException extends RuntimeException {
        private final String field;

        private RowException(String field, String message) {
            super(message);
            this.field = field;
        }
    }

    private record HeaderIndex(Sheet sheet, int row, Map<String, Integer> columns) {
        boolean has(String... names) {
            return find(names) >= 0;
        }

        int find(String... names) {
            for (String name : names) {
                Integer index = columns.get(normalize(name));
                if (index != null) {
                    return index;
                }
            }
            return -1;
        }

        String value(
                Row dataRow,
                DataFormatter formatter,
                FormulaEvaluator evaluator,
                String... names) {
            int index = find(names);
            if (index < 0 || dataRow == null) {
                return null;
            }
            Cell cell = dataRow.getCell(index);
            if (cell == null) {
                return null;
            }
            String value = formatter.formatCellValue(cell, evaluator);
            return value == null ? null : value.trim();
        }
    }

    private void write(Row row, Object... values) {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            Cell cell = row.createCell(i);
            if (value == null) {
                cell.setBlank();
            } else if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof Boolean bool) {
                cell.setCellValue(bool);
            } else {
                cell.setCellValue(String.valueOf(value));
            }
        }
    }

    private static final class Styles {
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle example;
        private final CellStyle title;
        private final CellStyle instructionHeader;
        private final CellStyle instructionBody;

        private Styles(Workbook workbook) {
            header = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setBorderBottom(BorderStyle.THIN);
            header.setWrapText(true);

            body = workbook.createCellStyle();
            body.setVerticalAlignment(VerticalAlignment.CENTER);
            body.setBorderBottom(BorderStyle.HAIR);
            body.setBorderRight(BorderStyle.HAIR);
            body.setWrapText(true);

            example = workbook.createCellStyle();
            example.cloneStyleFrom(body);
            example.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            example.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            title = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            title.setFont(titleFont);

            instructionHeader = workbook.createCellStyle();
            Font sectionFont = workbook.createFont();
            sectionFont.setBold(true);
            sectionFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            instructionHeader.setFont(sectionFont);

            instructionBody = workbook.createCellStyle();
            instructionBody.setWrapText(true);
            instructionBody.setVerticalAlignment(VerticalAlignment.TOP);
        }
    }
}
