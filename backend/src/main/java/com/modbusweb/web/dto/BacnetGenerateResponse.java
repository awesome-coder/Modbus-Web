package com.modbusweb.web.dto;

import java.util.List;

public record BacnetGenerateResponse(
        int created,
        int skipped,
        List<BacnetMappingResponse> mappings,
        List<String> warnings) {}
