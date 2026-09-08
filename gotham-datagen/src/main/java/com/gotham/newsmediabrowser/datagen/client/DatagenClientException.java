package com.gotham.newsmediabrowser.datagen.client;

/**
 * Unchecked exception thrown when a datagen helper container (Ollama, ComfyUI, Kokoro)
 * is unavailable, times out, or returns a non-2xx / malformed response.
 */
public class DatagenClientException extends RuntimeException {

    private final String serviceName;
    private final Integer statusCode;

    public DatagenClientException(String serviceName, String message) {
        super("[" + serviceName + "] " + message);
        this.serviceName = serviceName;
        this.statusCode = null;
    }

    public DatagenClientException(String serviceName, String message, Throwable cause) {
        super("[" + serviceName + "] " + message, cause);
        this.serviceName = serviceName;
        this.statusCode = null;
    }

    public DatagenClientException(String serviceName, int statusCode, String message) {
        super("[" + serviceName + "] HTTP " + statusCode + ": " + message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
