package com.gotham.newsmediabrowser.web.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Exercises the global exception handling: domain and unexpected exceptions render the branded
 * error view with the right status, a human reason, a reference id, and no internal detail.
 */
@WebMvcTest(controllers = GlobalErrorHandlingTest.ThrowingController.class)
@Import({GlobalExceptionHandler.class, GlobalErrorHandlingTest.ThrowingController.class})
class GlobalErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notFoundRendersBranded404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attributeExists("errorReference"))
                .andExpect(content().string(containsString("Article abc was not found.")))
                .andExpect(content().string(not(containsString("NotFoundException"))));
    }

    @Test
    void dependencyRendersBranded503NamingService() throws Exception {
        mockMvc.perform(get("/test/dependency"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("Elasticsearch")))
                .andExpect(content().string(containsString("Reference")))
                // Neither the underlying cause nor its type leaks to the browser.
                .andExpect(content().string(not(containsString("boom"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    @Test
    void frameworkErrorResponseKeepsItsStatus() throws Exception {
        // Spring MVC exceptions (here a 404 ResponseStatusException) must not be forced to 500.
        mockMvc.perform(get("/test/framework-404"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("We couldn’t find the page you requested.")));
    }

    @Test
    void unexpectedRendersGeneric500WithoutInternals() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("Something went wrong")))
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Controller
    static class ThrowingController {

        @GetMapping("/test/not-found")
        String notFound() {
            throw new NotFoundException("Article abc was not found.");
        }

        @GetMapping("/test/dependency")
        String dependency() {
            throw new DependencyException("Elasticsearch", new RuntimeException("boom"));
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/test/framework-404")
        String framework404() {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }
}
