package app.goodbuy.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@ControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message, String path) {
        var body = new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                path
        );
        return ResponseEntity.status(status).body(body);
    }

    // 422 for validation problems on @PathVariable/@RequestParam (e.g., @Pattern on gtin)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_barcode",
                ex.getMessage(), req.getRequestURI());
    }

    // 422 for @RequestBody validation (e.g., batch with @Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        var msg = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid request")
                .orElse("Invalid request");
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "validation_error", msg, req.getRequestURI());
    }

    // Honors codes thrown via ResponseStatusException (e.g., 404/422 from your controller)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleRse(
            ResponseStatusException ex, HttpServletRequest req) {
        var status = ex.getStatusCode();
        var reason = ex.getReason() != null ? ex.getReason() : status.toString();

        String code;
        if (status.value() == 404) {
            code = "not_found";
        } else if (status.value() == 422) {
            // If caller indicated invalid_barcode, surface that exact code; otherwise generic invalid_request
            code = reason.toLowerCase().startsWith("invalid_barcode")
                    ? "invalid_barcode"
                    : "invalid_request";
        } else {
            code = "error";
        }

        return build(HttpStatus.valueOf(status.value()), code, reason, req.getRequestURI());
    }

    // Catch-all → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(
            Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "server_error",
                "An unexpected error occurred", req.getRequestURI());
    }
}
