package com.gotham.newsmediabrowser.common.error;

/**
 * The request is well-formed HTTP but violates a search/API rule (e.g. article {@code mode=vector}).
 * Maps to HTTP 400 and the branded error page.
 */
public class BadRequestException extends GothamException {

    public BadRequestException(String userSafeMessage) {
        super(400, userSafeMessage);
    }

    /** Keeps the underlying cause for server-side logs while showing only the user-safe message. */
    public BadRequestException(String userSafeMessage, Throwable cause) {
        super(400, userSafeMessage, cause);
    }
}
