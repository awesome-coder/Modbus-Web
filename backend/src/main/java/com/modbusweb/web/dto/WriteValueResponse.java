package com.modbusweb.web.dto;

public record WriteValueResponse(
        boolean success, String message, PointReadResponse readBack) {}
