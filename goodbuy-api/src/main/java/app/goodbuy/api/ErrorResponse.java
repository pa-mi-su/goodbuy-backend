package app.goodbuy.api;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,     // machine code: not_found, invalid_barcode, server_error, etc.
        String message,   // human-friendly message
        String path       // request path
) {}
