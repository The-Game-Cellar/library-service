package com.thegamecellar.libraryservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String error,
        int status,
        String timestamp,
        String path,
        String requestId,
        List<FieldError> fieldErrors
) {
    public static ErrorResponse of(int status, String message, HttpServletRequest request) {
        return of(status, message, request, List.of());
    }

    public static ErrorResponse of(int status, String message, HttpServletRequest request, List<FieldError> fieldErrors) {
        String path = request != null ? request.getRequestURI() : null;
        return new ErrorResponse(message, status, Instant.now().toString(), path, MDC.get("requestId"), fieldErrors);
    }

    public record FieldError(String path, String message) {}
}
