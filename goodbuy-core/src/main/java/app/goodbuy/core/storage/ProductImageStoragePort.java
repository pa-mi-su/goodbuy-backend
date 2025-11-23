package app.goodbuy.core.storage;

/**
 * Abstraction for storing product-related images.
 *
 * We will have:
 *   - S3StorageAdapter implementing this
 *   - MissingProductReportService and future flows using this port
 */
public interface ProductImageStoragePort {

    /**
     * Upload raw image bytes and return a public URL.
     *
     * @param key          Full S3 key (e.g. "missing-products/0647658062020/front-abc123.jpg")
     * @param bytes        Image data
     * @param contentType  MIME type (e.g. image/jpeg)
     * @return public URL to the uploaded image
     */
    String uploadImage(String key, byte[] bytes, String contentType);
}
