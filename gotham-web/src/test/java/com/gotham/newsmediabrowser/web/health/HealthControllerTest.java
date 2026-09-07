package com.gotham.newsmediabrowser.web.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElasticsearchHealthChecker elasticsearch;

    @MockitoBean
    private ImageBindHealthChecker imagebind;

    @Test
    void elasticsearchUpReturns200() throws Exception {
        when(elasticsearch.isUp()).thenReturn(true);
        mockMvc.perform(get("/api/health/elasticsearch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void elasticsearchDownReturns503() throws Exception {
        when(elasticsearch.isUp()).thenReturn(false);
        mockMvc.perform(get("/api/health/elasticsearch"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void imagebindUpReturns200() throws Exception {
        when(imagebind.isUp()).thenReturn(true);
        mockMvc.perform(get("/api/health/imagebind"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void imagebindDownReturns503() throws Exception {
        when(imagebind.isUp()).thenReturn(false);
        mockMvc.perform(get("/api/health/imagebind"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
}
