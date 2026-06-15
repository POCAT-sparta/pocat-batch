package com.rocketcrew.pocatbatch.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MainAuctionLifecycleClientTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String INTERNAL_TOKEN = "test-token";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private MainAuctionLifecycleClient mainAuctionLifecycleClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        mainAuctionLifecycleClient = new MainAuctionLifecycleClient(restTemplate);
        ReflectionTestUtils.setField(mainAuctionLifecycleClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(mainAuctionLifecycleClient, "internalToken", INTERNAL_TOKEN);
    }

    // ------------------------------------------------------------
    // activate()
    // ------------------------------------------------------------

    @Test
    void activate_정상_200_응답이면_data값을_그대로_반환한다_true() {
        // given
        long auctionId = 1L;
        long jobExecutionId = 100L;

        String responseBody = """
                {
                  "success": true,
                  "status": 200,
                  "data": true
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/activate"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        boolean result = mainAuctionLifecycleClient.activate(auctionId, jobExecutionId);

        // then
        assertThat(result).isTrue();
        mockServer.verify();
    }

    @Test
    void activate_정상_200_응답이면_data값을_그대로_반환한다_false() {
        // given
        long auctionId = 2L;
        long jobExecutionId = 101L;

        String responseBody = """
                {
                  "success": true,
                  "status": 200,
                  "data": false
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/activate"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        boolean result = mainAuctionLifecycleClient.activate(auctionId, jobExecutionId);

        // then
        assertThat(result).isFalse();
        mockServer.verify();
    }

    @Test
    void activate_요청_헤더에_X_Internal_Token과_Idempotency_Key를_포함한다() {
        // given
        long auctionId = 10L;
        long jobExecutionId = 200L;
        String expectedIdempotencyKey = String.format("auction-activate-%d-%d", auctionId, jobExecutionId);

        String responseBody = """
                {
                  "success": true,
                  "status": 200,
                  "data": true
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/activate"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(header("Idempotency-Key", expectedIdempotencyKey))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        mainAuctionLifecycleClient.activate(auctionId, jobExecutionId);

        // then
        mockServer.verify();
    }

    @Test
    void activate_5xx_오류시_최대_3회까지_재시도한다() {
        // given
        long auctionId = 20L;
        long jobExecutionId = 300L;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/activate"))
                .andRespond(withServerError());
        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/activate"))
                .andRespond(withServerError());
        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/activate"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> mainAuctionLifecycleClient.activate(auctionId, jobExecutionId))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    void activate_401_오류시_RuntimeException을_던진다() {
        // given
        long auctionId = 30L;
        long jobExecutionId = 400L;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/activate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> mainAuctionLifecycleClient.activate(auctionId, jobExecutionId))
                .isInstanceOf(RuntimeException.class);

        mockServer.verify();
    }

    @Test
    void activate_400_오류시_RuntimeException으로_스킵된다() {
        // given
        long auctionId = 40L;
        long jobExecutionId = 500L;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/activate"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        // when & then
        assertThatThrownBy(() -> mainAuctionLifecycleClient.activate(auctionId, jobExecutionId))
                .isInstanceOf(RuntimeException.class);

        mockServer.verify();
    }

    // ------------------------------------------------------------
    // closeExpired()
    // ------------------------------------------------------------

    @Test
    void closeExpired_정상_200_응답이면_data값을_그대로_반환한다_true() {
        // given
        long auctionId = 1L;
        long jobExecutionId = 100L;

        String responseBody = """
                {
                  "success": true,
                  "status": 200,
                  "data": true
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/close-expired"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        boolean result = mainAuctionLifecycleClient.closeExpired(auctionId, jobExecutionId);

        // then
        assertThat(result).isTrue();
        mockServer.verify();
    }

    @Test
    void closeExpired_정상_200_응답이면_data값을_그대로_반환한다_false() {
        // given
        long auctionId = 2L;
        long jobExecutionId = 101L;

        String responseBody = """
                {
                  "success": true,
                  "status": 200,
                  "data": false
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/close-expired"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        boolean result = mainAuctionLifecycleClient.closeExpired(auctionId, jobExecutionId);

        // then
        assertThat(result).isFalse();
        mockServer.verify();
    }

    @Test
    void closeExpired_요청_헤더에_X_Internal_Token과_Idempotency_Key를_포함한다() {
        // given
        long auctionId = 10L;
        long jobExecutionId = 200L;
        String expectedIdempotencyKey = String.format("auction-close-%d-%d", auctionId, jobExecutionId);

        String responseBody = """
                {
                  "success": true,
                  "status": 200,
                  "data": true
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/close-expired"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(header("Idempotency-Key", expectedIdempotencyKey))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        mainAuctionLifecycleClient.closeExpired(auctionId, jobExecutionId);

        // then
        mockServer.verify();
    }

    @Test
    void closeExpired_5xx_오류시_최대_3회까지_재시도한다() {
        // given
        long auctionId = 20L;
        long jobExecutionId = 300L;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/close-expired"))
                .andRespond(withServerError());
        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/close-expired"))
                .andRespond(withServerError());
        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/close-expired"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> mainAuctionLifecycleClient.closeExpired(auctionId, jobExecutionId))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    void closeExpired_401_오류시_RuntimeException을_던진다() {
        // given
        long auctionId = 30L;
        long jobExecutionId = 400L;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/close-expired"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> mainAuctionLifecycleClient.closeExpired(auctionId, jobExecutionId))
                .isInstanceOf(RuntimeException.class);

        mockServer.verify();
    }

    @Test
    void closeExpired_400_오류시_RuntimeException으로_스킵된다() {
        // given
        long auctionId = 40L;
        long jobExecutionId = 500L;

        mockServer.expect(requestTo(BASE_URL + "/internal/auctions/" + auctionId + "/close-expired"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        // when & then
        assertThatThrownBy(() -> mainAuctionLifecycleClient.closeExpired(auctionId, jobExecutionId))
                .isInstanceOf(RuntimeException.class);

        mockServer.verify();
    }
}
