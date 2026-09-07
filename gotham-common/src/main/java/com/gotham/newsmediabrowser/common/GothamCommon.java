package com.gotham.newsmediabrowser.common;

/**
 * Marker for the shared module. Real shared components (config properties, ES/GCS/ImageBind
 * clients, domain, projections, repositories) are added starting in P1.
 */
public final class GothamCommon {

    /** Stable module identifier, handy for logs and diagnostics. */
    public static final String MODULE_NAME = "gotham-common";

    private GothamCommon() {
        // Utility holder: not instantiable.
    }
}
