package app.goodbuy.adapters.catalog;

/**
 * Wraps transport-layer failures when calling external catalogs
 * (HTTP errors, timeouts, decoding).
 *
 * RuntimeException so ExternalCatalogClient port does not need throws,
 * but callers can still explicitly catch and handle it.
 */
public class CatalogTransportException extends RuntimeException {
    public CatalogTransportException(String message) { super(message); }
    public CatalogTransportException(String message, Throwable cause) { super(message, cause); }
}
