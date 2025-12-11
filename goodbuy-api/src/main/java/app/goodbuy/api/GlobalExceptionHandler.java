package app.goodbuy.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /* ------------------------
       Utility: Build JSON Body
       ------------------------ */
    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String code, String message, String path) {

        var body = new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                path
        );

        return ResponseEntity.status(status).body(body);
    }

    /* ----------------------------
       400 - IllegalArgument / IllegalState (bad client usage)
       ---------------------------- */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleIllegalArgumentOrState(
            RuntimeException ex, HttpServletRequest req) {

        String msg = ex.getMessage() != null ? ex.getMessage() : "Invalid request";

        log.warn("Bad request at {}: {} ({})",
                req.getRequestURI(), msg, ex.getClass().getSimpleName());

        return build(
                HttpStatus.BAD_REQUEST,
                "invalid_request",
                msg,
                req.getRequestURI()
        );
    }

    /* ----------------------------
       422 - @PathVariable/@RequestParam
       ---------------------------- */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {

        log.warn("Constraint violation at {}: {}", req.getRequestURI(), ex.getMessage());

        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "invalid_request",
                ex.getMessage(),
                req.getRequestURI()
        );
    }

    /* ----------------------------
       422 - @RequestBody validation
       ---------------------------- */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        var msg = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .findFirst()
                .map(e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid request")
                .orElse("Invalid request");

        log.warn("RequestBody validation failed at {}: {}", req.getRequestURI(), msg);

        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation_error",
                msg,
                req.getRequestURI()
        );
    }

    /* ----------------------------
       Pass-through for ResponseStatusException
       ---------------------------- */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex, HttpServletRequest req) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = ex.getReason() != null ? ex.getReason() : status.toString();

        String code = switch (status.value()) {
            case 404 -> "not_found";
            case 422 -> reason.toLowerCase().contains("invalid") ? "invalid_request" : "invalid_request";
            default -> "error";
        };

        log.warn("{} at {}: {}", status.value(), req.getRequestURI(), reason);

        return build(status, code, reason, req.getRequestURI());
    }

    /* ----------------------------
       Catch-all 500
       ---------------------------- */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAnyUnexpected(
            Exception ex, HttpServletRequest req) {

        log.error("Unhandled exception at {}: {}", req.getRequestURI(), ex.getMessage(), ex);

        // include class name for easier debugging when testing with curl
        String msg = ex.getClass().getSimpleName() +
                (ex.getMessage() != null ? ": " + ex.getMessage() : "");

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "server_error",
                msg,
                req.getRequestURI()
        );
    }
}
