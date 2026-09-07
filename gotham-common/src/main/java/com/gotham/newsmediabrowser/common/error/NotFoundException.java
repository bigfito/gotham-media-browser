package com.gotham.newsmediabrowser.common.error;

/**
 * A requested resource (journalist, article, media element) does not exist. Maps to HTTP 404.
 */
public class NotFoundException extends GothamException {

    public NotFoundException(String userSafeMessage) {
        super(404, userSafeMessage);
    }
}
