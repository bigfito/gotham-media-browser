package com.gotham.newsmediabrowser.common.index;

/**
 * Raised when an index cannot be bootstrapped (existence check or creation failed).
 *
 * <p>Names the offending index in its message so operators know exactly what to fix, without
 * leaking connection secrets.
 */
public class IndexBootstrapException extends RuntimeException {

    public IndexBootstrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
