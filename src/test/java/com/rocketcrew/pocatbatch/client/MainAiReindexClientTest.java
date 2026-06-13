package com.rocketcrew.pocatbatch.client;

import com.rocketcrew.pocatbatch.client.dto.ReindexChunkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MainAiReindexClientTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String INTERNAL_TOKEN = "test-token";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private MainAiReindexClient mainAiReindexClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        mainAiReindexClient = new MainAiReindexClient(restTemplate);
        ReflectionTestUtils.setField(mainAiReindexClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(mainAiReindexClient, "internalToken", INTERNAL_TOKEN);
    }

    @Test
    void reindexChunk_정상_응답을_ReindexChunkResponse로_매핑한다() {
        // given
        List<Long> cardIds = List.of(1L, 2L, 3L);
        long jobExecutionId = 100L;

        String responseBody = """
                {
                  "success": true,
                  "status": 200,
                  "data": {
                    "processedCount": 3,
                    "skippedCount": 0,
                    "indexedCount": 3,
                    "failedCount": 0,
                    "rateLimited": false
                  }
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/internal/ai/reindex-cards"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        ReindexChunkResponse response = mainAiReindexClient.reindexChunk(cardIds, jobExecutionId);

        // then
        assertThat(response.processedCount()).isEqualTo(3);
        assertThat(response.skippedCount()).isEqualTo(0);
        assertThat(response.indexedCount()).isEqualTo(3);
        assertThat(response.failedCount()).isEqualTo(0);
        assertThat(response.rateLimited()).isFalse();

        mockServer.verify();
    }

    @Test
    void reindexChunk_rateLimited가_true인_응답을_언래핑하여_그대로_전달한다() {
        // given
        List<Long> cardIds = List.of(1L, 2L, 3L);
        long jobExecutionId = 150L;

        String responseBody = """
                {
                  "success": true,
                  "status": 200,
                  "data": {
                    "processedCount": 0,
                    "skippedCount": 3,
                    "indexedCount": 0,
                    "failedCount": 0,
                    "rateLimited": true
                  }
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/internal/ai/reindex-cards"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        ReindexChunkResponse response = mainAiReindexClient.reindexChunk(cardIds, jobExecutionId);

        // then
        assertThat(response.rateLimited()).isTrue();
        assertThat(response.skippedCount()).isEqualTo(3);

        mockServer.verify();
    }

    @Test
    void reindexChunk_요청_헤더에_X_Internal_Token과_Idempotency_Key를_포함한다() {
        // given
        List<Long> cardIds = List.of(10L, 11L, 12L);
        long jobExecutionId = 200L;
        String expectedIdempotencyKey = String.format("reindex-cards-%d-%d-%d", 10L, 12L, jobExecutionId);

        String responseBody = """
                {
                  "success": true,
                  "status": 200,
                  "data": {
                    "processedCount": 3,
                    "skippedCount": 0,
                    "indexedCount": 3,
                    "failedCount": 0,
                    "rateLimited": false
                  }
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/internal/ai/reindex-cards"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(header("Idempotency-Key", expectedIdempotencyKey))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        mainAiReindexClient.reindexChunk(cardIds, jobExecutionId);

        // then
        mockServer.verify();
    }

    @Test
    void reindexChunk_5xx_오류시_최대_3회까지_재시도한다() {
        // given
        List<Long> cardIds = List.of(1L, 2L);
        long jobExecutionId = 300L;

        mockServer.expect(requestTo(BASE_URL + "/internal/ai/reindex-cards"))
                .andRespond(withServerError());
        mockServer.expect(requestTo(BASE_URL + "/internal/ai/reindex-cards"))
                .andRespond(withServerError());
        mockServer.expect(requestTo(BASE_URL + "/internal/ai/reindex-cards"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> mainAiReindexClient.reindexChunk(cardIds, jobExecutionId))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    void reindexChunk_4xx_오류시_재시도_없이_즉시_RuntimeException으로_스킵된다() {
        // given
        List<Long> cardIds = List.of(1L, 2L);
        long jobExecutionId = 400L;

        mockServer.expect(requestTo(BASE_URL + "/internal/ai/reindex-cards"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        // when & then
        assertThatThrownBy(() -> mainAiReindexClient.reindexChunk(cardIds, jobExecutionId))
                .isInstanceOf(RuntimeException.class);

        mockServer.verify();
    }
}
