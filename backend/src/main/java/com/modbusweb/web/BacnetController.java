package com.modbusweb.web;

import com.modbusweb.domain.BacnetObjectType;
import com.modbusweb.service.PointService;
import com.modbusweb.service.bacnet.BacnetMappingService;
import com.modbusweb.web.dto.BacnetGenerateResponse;
import com.modbusweb.web.dto.BacnetMappingRequest;
import com.modbusweb.web.dto.BacnetMappingResponse;
import com.modbusweb.web.dto.BacnetPreviewResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BacnetController {

    private final BacnetMappingService mappingService;
    private final PointService pointService;

    public BacnetController(
            BacnetMappingService mappingService, PointService pointService) {
        this.mappingService = mappingService;
        this.pointService = pointService;
    }

    @GetMapping("/devices/{deviceId}/bacnet/mappings")
    public List<BacnetMappingResponse> list(@PathVariable Long deviceId) {
        return mappingService.list(deviceId);
    }

    @PostMapping("/devices/{deviceId}/bacnet/generate")
    public BacnetGenerateResponse generate(@PathVariable Long deviceId) {
        return mappingService.generate(deviceId);
    }

    @GetMapping("/devices/{deviceId}/bacnet/preview")
    public BacnetPreviewResponse preview(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "false") boolean includeValues) {
        return mappingService.preview(deviceId, includeValues);
    }

    @GetMapping("/devices/{deviceId}/bacnet/export")
    public ResponseEntity<BacnetPreviewResponse> export(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "false") boolean includeValues) {
        return ResponseEntity.ok()
                .header("X-BACnet-Contract-Version", "1")
                .body(mappingService.preview(deviceId, includeValues));
    }

    @PostMapping("/bacnet/mappings")
    public ResponseEntity<BacnetMappingResponse> create(
            @Valid @RequestBody BacnetMappingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mappingService.create(request));
    }

    @PutMapping("/bacnet/mappings/{id}")
    public BacnetMappingResponse update(
            @PathVariable Long id, @Valid @RequestBody BacnetMappingRequest request) {
        return mappingService.update(id, request);
    }

    @DeleteMapping("/bacnet/mappings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        mappingService.delete(id);
    }

    @GetMapping("/points/{pointId}/bacnet/supported-types")
    public List<BacnetObjectType> supportedTypes(@PathVariable Long pointId) {
        return mappingService.supportedObjectTypes(pointService.requireWithDevice(pointId));
    }
}
