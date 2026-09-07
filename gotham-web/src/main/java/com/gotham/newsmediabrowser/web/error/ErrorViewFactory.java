package com.gotham.newsmediabrowser.web.error;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

/**
 * Builds the branded {@code error} view for every failure path.
 *
 * <p>Generates a short reference id, logs the full detail server-side keyed by that id (the browser
 * only ever sees a sanitized reason), and populates the model the {@code error.html} template reads.
 *
 * <p>A plain, stateless helper (not a Spring bean) so the global advice can be included in any
 * {@code @WebMvcTest} slice without extra wiring.
 */
public class ErrorViewFactory {

    private static final Logger log = LoggerFactory.getLogger(ErrorViewFactory.class);

    /**
     * Render the error view for the given status/reason, logging {@code cause} (if any) with the
     * generated reference id. The response status is set on the returned {@link ModelAndView}.
     */
    public ModelAndView render(HttpStatus status, String reason, Throwable cause) {
        String reference = newReference();
        if (cause != null) {
            log.error("[{}] HTTP {} — {}", reference, status.value(), cause.toString(), cause);
        } else {
            log.error("[{}] HTTP {} — {}", reference, status.value(), reason);
        }

        ModelAndView view = new ModelAndView("error");
        view.setStatus(status);
        view.addObject("errorStatus", status.value());
        view.addObject("errorTitle", titleFor(status));
        view.addObject("errorReason", reason);
        view.addObject("errorReference", reference);
        return view;
    }

    private String newReference() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** A user-safe reason for a status that has no domain-specific message (framework/container errors). */
    public String defaultReasonFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "We couldn’t find the page you requested.";
            case METHOD_NOT_ALLOWED -> "That action isn’t allowed here.";
            case BAD_REQUEST -> "The request couldn’t be understood. Please check it and try again.";
            case PAYLOAD_TOO_LARGE -> "That upload is larger than the allowed limit.";
            case SERVICE_UNAVAILABLE -> "A required service is currently unavailable. Please try again shortly.";
            default -> "We couldn’t complete that request. Please try again; if it keeps happening, quote the reference below.";
        };
    }

    private String titleFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Check your input";
            case NOT_FOUND -> "We couldn’t find that";
            case PAYLOAD_TOO_LARGE -> "Upload too large";
            case SERVICE_UNAVAILABLE -> "A required service is unavailable";
            default -> "Something went wrong";
        };
    }
}
