package com.gotham.newsmediabrowser.web.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Handles container-level errors (routes with no handler, errors raised in filters, etc.) that are
 * forwarded to {@code /error}. Replaces Spring Boot's Whitelabel page with the branded error view.
 * Exceptions thrown inside controllers are handled earlier by {@link GlobalExceptionHandler}.
 */
@Controller
public class GothamErrorController implements ErrorController {

    private final ErrorViewFactory errorViewFactory = new ErrorViewFactory();

    @RequestMapping("/error")
    public ModelAndView handleError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        Throwable cause = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        return errorViewFactory.render(status, errorViewFactory.defaultReasonFor(status), cause);
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer statusCode) {
            HttpStatus resolved = HttpStatus.resolve(statusCode);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
