package com.modbusweb.web.dto;

public record ImportIssue(
        String severity, String sheet, int row, String field, String message) {}
