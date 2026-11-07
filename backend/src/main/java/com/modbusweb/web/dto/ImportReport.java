package com.modbusweb.web.dto;

import java.time.Instant;
import java.util.List;

public record ImportReport(
        boolean success,
        String message,
        Instant importedAt,
        int devicesCreated,
        int devicesUpdated,
        int pointsCreated,
        int pointsUpdated,
        int skipped,
        List<ImportIssue> issues) {}
