package com.modbusweb;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModbusWebIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper objectMapper;

    private static ModbusTcpServer server;
    private static int modbusPort;
    private static final ProcessImage IMAGE = new ProcessImage();

    @BeforeAll
    void startModbusServer() throws Exception {
        modbusPort = freePort();
        IMAGE.with(
                true,
                tx ->
                        tx.writeHoldingRegisters(
                                map -> {
                                    map.put(0, new byte[] {0x00, (byte) 0xEA});
                                    map.put(2, new byte[] {0x00, 0x00});
                                    map.put(3, new byte[] {0x3F, (byte) 0x80});
                                }));
        IMAGE.with(true, tx -> tx.writeCoils(map -> map.put(0, true)));

        var services =
                new ReadWriteModbusServices() {
                    @Override
                    protected Optional<ProcessImage> getProcessImage(int unitId) {
                        return unitId == 1 ? Optional.of(IMAGE) : Optional.empty();
                    }
                };
        NettyTcpServerTransport transport =
                NettyTcpServerTransport.create(
                        builder ->
                                builder.setBindAddress("127.0.0.1")
                                        .setPort(modbusPort)
                                        .setTlsEnabled(false));
        server = ModbusTcpServer.create(transport, services);
        server.start();
    }

    @AfterAll
    void stopModbusServer() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void fullDevicePointLiveBacnetAndExcelWorkflow() throws Exception {
        long deviceId = createDevice();
        long uintPointId = createPoint(deviceId, "RAW", "原始值", "HOLDING_REGISTER", 0, "UINT16", 1, 0.1);
        createPoint(deviceId, "FLOAT", "浮点值", "HOLDING_REGISTER", 2, "FLOAT32", 2, 1.0);
        long coilPointId = createPoint(deviceId, "RUN", "运行", "COIL", 0, "BOOL", 1, 1.0);

        ResponseEntity<String> readAll =
                rest.postForEntity("/api/devices/" + deviceId + "/read-all", null, String.class);
        assertThat(readAll.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode readBody = objectMapper.readTree(readAll.getBody());
        assertThat(readBody.path("successCount").asInt()).isEqualTo(3);
        assertThat(readBody.path("errorCount").asInt()).isZero();
        Map<String, JsonNode> byCode = byCode(readBody.path("results"));
        assertThat(byCode.get("RAW").path("value").asText()).isEqualTo("23.4");
        assertThat(byCode.get("FLOAT").path("value").asText()).isEqualTo("1");
        assertThat(byCode.get("RUN").path("booleanValue").asBoolean()).isTrue();

        ResponseEntity<String> write =
                rest.postForEntity(
                        "/api/points/" + uintPointId + "/write",
                        Map.of("value", "12.3"),
                        String.class);
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode writeBody = objectMapper.readTree(write.getBody());
        assertThat(writeBody.path("success").asBoolean()).isTrue();
        assertThat(writeBody.path("readBack").path("value").asText()).isEqualTo("12.3");

        ResponseEntity<String> writeCoil =
                rest.postForEntity(
                        "/api/points/" + coilPointId + "/write",
                        Map.of("value", "OFF"),
                        String.class);
        assertThat(writeCoil.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(writeCoil.getBody()).path("readBack").path("booleanValue").asBoolean())
                .isFalse();

        ResponseEntity<String> generated =
                rest.postForEntity(
                        "/api/devices/" + deviceId + "/bacnet/generate", null, String.class);
        assertThat(generated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode generatedBody = objectMapper.readTree(generated.getBody());
        assertThat(generatedBody.path("created").asInt()).isEqualTo(3);
        assertThat(generatedBody.path("mappings")).hasSize(3);

        ResponseEntity<String> preview =
                rest.getForEntity(
                        "/api/devices/" + deviceId + "/bacnet/preview?includeValues=true",
                        String.class);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode previewBody = objectMapper.readTree(preview.getBody());
        assertThat(previewBody.path("objects")).hasSize(3);
        assertThat(previewBody.path("valuesIncluded").asBoolean()).isTrue();
        assertThat(previewBody.path("objects").get(0).path("presentValue")).isNotNull();

        ResponseEntity<byte[]> exported =
                rest.getForEntity("/api/excel/export", byte[].class);
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exported.getBody()).isNotNull();
        assertThat(exported.getBody().length).isGreaterThan(2000);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(exported.getBody()))) {
            assertThat(workbook.getSheet("设备")).isNotNull();
            assertThat(workbook.getSheet("点位")).isNotNull();
            assertThat(workbook.getSheet("设备").getLastRowNum()).isGreaterThanOrEqualTo(1);
            assertThat(workbook.getSheet("点位").getLastRowNum()).isGreaterThanOrEqualTo(3);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add(
                "file",
                new ByteArrayResource(exported.getBody()) {
                    @Override
                    public String getFilename() {
                        return "roundtrip.xlsx";
                    }
                });
        ResponseEntity<String> imported =
                rest.postForEntity(
                        "/api/excel/import", new HttpEntity<>(form, headers), String.class);
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode importBody = objectMapper.readTree(imported.getBody());
        assertThat(importBody.path("success").asBoolean()).isTrue();
        assertThat(importBody.path("pointsUpdated").asInt()).isEqualTo(3);

        ResponseEntity<byte[]> template =
                rest.getForEntity("/api/excel/template", byte[].class);
        assertThat(template.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(template.getBody().length).isGreaterThan(2000);

        ResponseEntity<String> delete =
                rest.exchange(
                        "/api/devices/" + deviceId, HttpMethod.DELETE, null, String.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private long createDevice() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Integration PLC");
        body.put("protocol", "MODBUS_TCP");
        body.put("host", "127.0.0.1");
        body.put("port", modbusPort);
        body.put("unitId", 1);
        body.put("connectTimeoutMs", 2000);
        body.put("requestTimeoutMs", 2000);
        body.put("enabled", true);
        body.put("tlsVerifyHostname", true);
        ResponseEntity<String> response =
                rest.postForEntity("/api/devices", body, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).path("id").asLong();
    }

    private long createPoint(
            long deviceId,
            String code,
            String name,
            String area,
            int address,
            String dataType,
            int registerLength,
            double scale) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("area", area);
        body.put("address", address);
        body.put("dataType", dataType);
        body.put("byteOrder", "BIG_ENDIAN");
        body.put("wordOrder", "LITTLE_ENDIAN");
        body.put("scale", scale);
        body.put("offset", 0.0);
        body.put("access", "READ_WRITE");
        body.put("enabled", true);
        body.put("stringCharset", "UTF_8");
        if (dataType.equals("BOOL")) {
            body.put("bitIndex", null);
            body.put("registerLength", null);
        } else {
            body.put("registerLength", dataType.equals("STRING") || dataType.equals("HEX") ? 8 : null);
        }
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/devices/" + deviceId + "/points", body, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        try {
            return objectMapper.readTree(response.getBody()).path("id").asLong();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, JsonNode> byCode(JsonNode results) {
        Map<String, JsonNode> map = new HashMap<>();
        results.forEach(node -> map.put(node.path("pointCode").asText(), node));
        return map;
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
