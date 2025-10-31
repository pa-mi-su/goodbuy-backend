package app.goodbuy.catalog;

/**
 * Wraps transport-layer failures when calling external catalogs
 * (HTTP errors, timeouts, decoding).
 */
public class CatalogTransportException extends Exception {
    public CatalogTransportException(String message) { super(message); }
    public CatalogTransportException(String message, Throwable cause) { super(message, cause); }
}
