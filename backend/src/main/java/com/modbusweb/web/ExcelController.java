package com.modbusweb.web;

import com.modbusweb.service.excel.ExcelService;
import com.modbusweb.web.dto.ImportReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/excel")
public class ExcelController {

    private static final MediaType XLSX =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelService excelService;

    public ExcelController(ExcelService excelService) {
        this.excelService = excelService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return download(excelService.exportAll(), excelService.exportFilename("modbus-config"));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return download(excelService.createTemplate(), excelService.exportFilename("modbus-template"));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport importFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择要导入的 .xlsx / .xls 文件");
        }
        try {
            return excelService.importWorkbook(file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("读取上传文件失败: " + e.getMessage());
        }
    }

    private static ResponseEntity<byte[]> download(byte[] content, String filename) {
        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content);
    }
}
