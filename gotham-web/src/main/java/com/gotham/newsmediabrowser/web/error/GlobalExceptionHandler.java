package com.gotham.newsmediabrowser.web.error;

import com.gotham.newsmediabrowser.common.error.GothamException;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Maps exceptions thrown by any controller to the branded error page.
 *
 * <p>Domain {@link GothamException}s carry their own status and user-safe reason; anything else
 * becomes a generic HTTP 500 so internal details never reach the browser.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String GENERIC_500_REASON =
            "We couldn’t complete that request. Please try again; if it keeps happening, quote the reference below.";

    private final ErrorViewFactory errorViewFactory = new ErrorViewFactory();

    @ExceptionHandler(GothamException.class)
    public ModelAndView handleDomain(GothamException ex) {
        return errorViewFactory.render(HttpStatus.valueOf(ex.getHttpStatus()), ex.getUserReason(), ex);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception ex) {
        // Spring's own MVC exceptions (404 no resource, 405, 400, ...) implement ErrorResponse and
        // carry the right status — honor it instead of forcing every one to 500.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            Throwable cause = status.is5xxServerError() ? ex : null;
            return errorViewFactory.render(status, errorViewFactory.defaultReasonFor(status), cause);
        }
        return errorViewFactory.render(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_500_REASON, ex);
    }
}
