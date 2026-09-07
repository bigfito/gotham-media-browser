package com.gotham.newsmediabrowser.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

class GothamErrorControllerTest {

    private final GothamErrorController controller = new GothamErrorController();

    @Test
    void rendersBrandedViewForContainer404() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);

        ModelAndView view = controller.handleError(request);

        assertThat(view.getViewName()).isEqualTo("error");
        assertThat(view.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(view.getModel()).containsEntry("errorStatus", 404);
        assertThat(view.getModel().get("errorReference")).asString().startsWith("req_");
    }

    @Test
    void defaultsToInternalServerErrorWhenStatusMissing() {
        ModelAndView view = controller.handleError(new MockHttpServletRequest());

        assertThat(view.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(view.getModel()).containsEntry("errorStatus", 500);
    }
}
