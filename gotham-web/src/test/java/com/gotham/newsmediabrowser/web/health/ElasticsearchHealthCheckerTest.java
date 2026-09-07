package com.gotham.newsmediabrowser.web.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchHealthCheckerTest {

    @Mock
    private ElasticsearchClient client;

    @InjectMocks
    private ElasticsearchHealthChecker checker;

    @Test
    void reportsUpWhenInfoSucceeds() throws Exception {
        // The response is not inspected; a successful (non-throwing) call means up.
        when(client.info()).thenReturn(null);
        assertThat(checker.isUp()).isTrue();
    }

    @Test
    void reportsDownWhenInfoThrows() throws Exception {
        when(client.info()).thenThrow(new IOException("connection refused"));
        assertThat(checker.isUp()).isFalse();
    }
}
