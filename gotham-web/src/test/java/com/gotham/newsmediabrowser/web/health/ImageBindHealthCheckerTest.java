package com.gotham.newsmediabrowser.web.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.gotham.newsmediabrowser.common.config.ImageBindProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageBindHealthCheckerTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> response;

    private ImageBindHealthChecker checker;

    @BeforeEach
    void setUp() {
        checker = new ImageBindHealthChecker(httpClient,
                new ImageBindProperties("http://imagebind-service:8081", false, java.time.Duration.ofSeconds(60)));
    }

    @Test
    void reportsUpOn2xxWhenModelLoaded() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"status\":\"UP\",\"model_loaded\":true}");
        doReturn(response).when(httpClient).send(any(), any());
        assertThat(checker.isUp()).isTrue();
    }

    @Test
    void reportsDownWhenModelStillLoading() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"status\":\"UP\",\"model_loaded\": false}");
        doReturn(response).when(httpClient).send(any(), any());
        assertThat(checker.isUp()).isFalse();
    }

    @Test
    void reportsDownOnNon2xx() throws Exception {
        when(response.statusCode()).thenReturn(503);
        doReturn(response).when(httpClient).send(any(), any());
        assertThat(checker.isUp()).isFalse();
    }

    @Test
    void reportsDownWhenRequestFails() throws Exception {
        doThrow(new IOException("connection refused")).when(httpClient).send(any(), any());
        assertThat(checker.isUp()).isFalse();
    }
}
