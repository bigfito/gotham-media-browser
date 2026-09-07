package com.gotham.newsmediabrowser.common.error;

/**
 * Base for domain failures that map to a branded error page.
 *
 * <p>Carries an HTTP status (as an {@code int} to avoid a spring-web dependency in this module)
 * and a <strong>user-safe</strong> message — never raw internals, secrets, or stack detail. The
 * web layer ({@code GlobalExceptionHandler}) turns this into the error view with a reference id.
 */
public abstract class GothamException extends RuntimeException {

    private final int httpStatus;

    protected GothamException(int httpStatus, String userSafeMessage) {
        super(userSafeMessage);
        this.httpStatus = httpStatus;
    }

    protected GothamException(int httpStatus, String userSafeMessage, Throwable cause) {
        super(userSafeMessage, cause);
        this.httpStatus = httpStatus;
    }

    /** HTTP status this failure should surface as (e.g. 404, 413, 503). */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** The user-safe reason shown on the error page. */
    public String getUserReason() {
        return getMessage();
    }
}
