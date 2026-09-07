package com.gotham.newsmediabrowser.common.error;

/**
 * An uploaded media file exceeds the configured size/duration limits. Maps to HTTP 413.
 * Prefer in-form validation messages where possible; this is for the error-page path.
 */
public class MediaLimitException extends GothamException {

    public MediaLimitException(String userSafeMessage) {
        super(413, userSafeMessage);
    }
}
