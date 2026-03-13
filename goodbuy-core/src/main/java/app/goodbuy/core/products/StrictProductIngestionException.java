package app.goodbuy.core.products;

public class StrictProductIngestionException extends RuntimeException {

    public StrictProductIngestionException(String message) {
        super(message);
    }

    public StrictProductIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
