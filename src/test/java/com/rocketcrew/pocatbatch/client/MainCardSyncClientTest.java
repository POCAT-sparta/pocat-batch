package com.rocketcrew.pocatbatch.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MainCardSyncClientTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String INTERNAL_TOKEN = "test-token";
    private static final String SYNC_URL = BASE_URL + "/internal/cards/sync";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private MainCardSyncClient mainCardSyncClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        mainCardSyncClient = new MainCardSyncClient(restTemplate);
        ReflectionTestUtils.setField(mainCardSyncClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(mainCardSyncClient, "internalToken", INTERNAL_TOKEN);
    }

    @Test
    void triggerSync_202_응답이면_정상_처리된다() {
        // given
        long jobExecutionId = 100L;

        mockServer.expect(requestTo(SYNC_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        // when & then
        assertThatCode(() -> mainCardSyncClient.triggerSync(jobExecutionId))
                .doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void triggerSync_요청_헤더에_X_Internal_Token을_포함한다() {
        // given
        long jobExecutionId = 100L;

        mockServer.expect(requestTo(SYNC_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        // when
        mainCardSyncClient.triggerSync(jobExecutionId);

        // then
        mockServer.verify();
    }

    @Test
    void triggerSync_409_CARD_SYNC_IN_PROGRESS_응답이면_정상_스킵된다() {
        // given
        long jobExecutionId = 200L;

        String responseBody = """
                {
                  "success": false,
                  "status": 409,
                  "data": "CARD_SYNC_IN_PROGRESS"
                }
                """;

        mockServer.expect(requestTo(SYNC_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body(responseBody)
                        .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatCode(() -> mainCardSyncClient.triggerSync(jobExecutionId))
                .doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void triggerSync_401_오류시_RuntimeException을_던진다() {
        // given
        long jobExecutionId = 300L;

        mockServer.expect(requestTo(SYNC_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> mainCardSyncClient.triggerSync(jobExecutionId))
                .isInstanceOf(RuntimeException.class);

        mockServer.verify();
    }

    @Test
    void triggerSync_400_오류시_RuntimeException을_던진다() {
        // given
        long jobExecutionId = 400L;

        String responseBody = """
                {
                  "success": false,
                  "status": 400,
                  "data": "INVALID_REQUEST"
                }
                """;

        mockServer.expect(requestTo(SYNC_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body(responseBody)
                        .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> mainCardSyncClient.triggerSync(jobExecutionId))
                .isInstanceOf(RuntimeException.class);

        mockServer.verify();
    }
}
