package com.modbusweb.web;

import com.modbusweb.service.DeviceService;
import com.modbusweb.web.dto.ConnectionTestResponse;
import com.modbusweb.web.dto.DeviceRequest;
import com.modbusweb.web.dto.DeviceResponse;
import com.modbusweb.web.dto.DeviceStatusResponse;
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
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public List<DeviceResponse> list() {
        return deviceService.list();
    }

    @GetMapping("/status")
    public List<DeviceStatusResponse> statuses() {
        return deviceService.statuses();
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@PathVariable Long id) {
        return deviceService.get(id);
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> create(@Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.create(request));
    }

    @PutMapping("/{id}")
    public DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return deviceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deviceService.delete(id);
    }

    @PostMapping("/{id}/test")
    public ConnectionTestResponse test(@PathVariable Long id) {
        return deviceService.test(id);
    }

    @PostMapping("/{id}/connect")
    public DeviceStatusResponse connect(@PathVariable Long id) {
        deviceService.connect(id);
        return deviceService.status(id);
    }

    @PostMapping("/{id}/disconnect")
    public DeviceStatusResponse disconnect(@PathVariable Long id) {
        deviceService.disconnect(id);
        return deviceService.status(id);
    }

    @GetMapping("/{id}/status")
    public DeviceStatusResponse status(@PathVariable Long id) {
        return deviceService.status(id);
    }
}
