package app.goodbuy.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalErrorHandler {

    record ErrorBody(String error, String message, String path, String timestamp) {}

    private ErrorBody body(String error, String message, String path, HttpStatus status) {
        return new ErrorBody(
                error,
                message == null || message.isBlank() ? status.getReasonPhrase() : message,
                path,
                OffsetDateTime.now().toString()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ErrorBody> handleStatus(
            ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        String errorKey = (status == HttpStatus.NOT_FOUND) ? "not found" : status.getReasonPhrase().toLowerCase();
        return org.springframework.http.ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(errorKey, ex.getReason(), req.getRequestURI(), status));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public org.springframework.http.ResponseEntity<ErrorBody> handleInvalidBody(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst().orElse("Validation failed");
        return org.springframework.http.ResponseEntity
                .badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("validation", msg, req.getRequestURI(), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public org.springframework.http.ResponseEntity<ErrorBody> handleConstraint(
            ConstraintViolationException ex, HttpServletRequest req) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .findFirst().orElse("Validation failed");
        return org.springframework.http.ResponseEntity
                .badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("validation", msg, req.getRequestURI(), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public org.springframework.http.ResponseEntity<ErrorBody> handleErrorResponse(
            ErrorResponseException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return org.springframework.http.ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(status.getReasonPhrase().toLowerCase(), ex.getBody().getDetail(), req.getRequestURI(), status));
    }

    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<ErrorBody> handleOther(
            Exception ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return org.springframework.http.ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body("error", ex.getMessage(), req.getRequestURI(), status));
    }
}
