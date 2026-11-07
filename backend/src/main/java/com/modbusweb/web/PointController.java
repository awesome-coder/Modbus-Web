package com.modbusweb.web;

import com.modbusweb.service.ModbusReadWriteService;
import com.modbusweb.service.PointService;
import com.modbusweb.web.dto.PointReadResponse;
import com.modbusweb.web.dto.PointRequest;
import com.modbusweb.web.dto.PointResponse;
import com.modbusweb.web.dto.ReadAllResponse;
import com.modbusweb.web.dto.WriteValueRequest;
import com.modbusweb.web.dto.WriteValueResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PointController {

    private final PointService pointService;
    private final ModbusReadWriteService readWriteService;

    public PointController(
            PointService pointService, ModbusReadWriteService readWriteService) {
        this.pointService = pointService;
        this.readWriteService = readWriteService;
    }

    @GetMapping("/devices/{deviceId}/points")
    public List<PointResponse> list(@PathVariable Long deviceId) {
        return pointService.list(deviceId);
    }

    @PostMapping("/devices/{deviceId}/points")
    public ResponseEntity<PointResponse> create(
            @PathVariable Long deviceId, @Valid @RequestBody PointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pointService.create(deviceId, request));
    }

    @GetMapping("/points/{id}")
    public PointResponse get(@PathVariable Long id) {
        return pointService.get(id);
    }

    @PutMapping("/points/{id}")
    public PointResponse update(@PathVariable Long id, @Valid @RequestBody PointRequest request) {
        return pointService.update(id, request);
    }

    @DeleteMapping("/points/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        pointService.delete(id);
    }

    @PostMapping("/points/{id}/read")
    public PointReadResponse read(@PathVariable Long id) {
        return readWriteService.readPoint(id);
    }

    @PostMapping("/points/{id}/write")
    public WriteValueResponse write(
            @PathVariable Long id, @Valid @RequestBody WriteValueRequest request) {
        return readWriteService.writePoint(id, request.value());
    }

    @PostMapping("/devices/{deviceId}/read-all")
    public ReadAllResponse readAll(@PathVariable Long deviceId) {
        return readWriteService.readAll(deviceId);
    }
}
