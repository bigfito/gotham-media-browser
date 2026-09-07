package com.gotham.newsmediabrowser.common.error;

/**
 * A required backing service (Elasticsearch, ImageBind, GCS) is unavailable or timed out.
 * Maps to HTTP 503 and names the service in a user-safe way (no endpoints, keys, or stack detail).
 */
public class DependencyException extends GothamException {

    private final String serviceName;

    public DependencyException(String serviceName, Throwable cause) {
        super(503, "The " + serviceName + " service is currently unavailable. Please try again shortly.", cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
