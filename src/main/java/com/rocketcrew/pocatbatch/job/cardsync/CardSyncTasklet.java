package com.rocketcrew.pocatbatch.job.cardsync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocatbatch.domain.card.entity.Card;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocatbatch.domain.card.repository.CardRepository;
import com.rocketcrew.pocatbatch.domain.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardSyncTasklet implements Tasklet {

    private final CardRepository cardRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;

    @Value("${pocat.batch.card-sync.admin-user-id:1}")
    private Long adminUserId;

    private static final String TCGDEX_SETS_URL = "https://api.tcgdex.net/v2/en/sets";
    private static final String TCGDEX_SET_URL  = "https://api.tcgdex.net/v2/en/sets/";
    private static final String TCGDEX_CARD_URL = "https://api.tcgdex.net/v2/en/cards/";

    private static final CardGrade[] GRADES = CardGrade.values();

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("[CardSync] 배치 동기화 시작 (adminUserId={})", adminUserId);

        RestTemplate restTemplate = createRestTemplate();
        int totalSynced = 0;

        try {
            String setsJson = restTemplate.getForObject(TCGDEX_SETS_URL, String.class);
            JsonNode setsArray = objectMapper.readTree(setsJson);

            for (JsonNode setNode : setsArray) {
                String setId = setNode.path("id").asText();
                try {
                    totalSynced += syncSet(restTemplate, setId, totalSynced);
                } catch (Exception e) {
                    log.warn("[CardSync] 세트 동기화 실패 ({}): {}", setId, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[CardSync] 배치 동기화 실패", e);
            throw new RuntimeException("카드 동기화 중 오류 발생", e);
        }

        log.info("[CardSync] 배치 동기화 완료 — 신규 카드 총 {}개", totalSynced);
        return RepeatStatus.FINISHED;
    }

    private int syncSet(RestTemplate restTemplate, String setId, int offset) throws Exception {
        String setJson = restTemplate.getForObject(TCGDEX_SET_URL + setId, String.class);
        JsonNode setRoot = objectMapper.readTree(setJson);

        String setName = setRoot.path("name").asText("");
        JsonNode cardNodes = setRoot.path("cards");

        int synced = 0;
        for (JsonNode cardNode : cardNodes) {
            String tcgdexId = cardNode.path("id").asText();

            if (cardRepository.existsByTcgdexId(tcgdexId)) {
                continue;
            }

            try {
                String cardJson = restTemplate.getForObject(TCGDEX_CARD_URL + tcgdexId, String.class);
                JsonNode cardRoot = objectMapper.readTree(cardJson);

                String name      = cardRoot.path("name").asText();
                String localId   = cardRoot.path("localId").asText();
                String imageBase = cardRoot.path("image").asText("");
                String imageUrl  = imageBase.isEmpty() ? null : imageBase + "/high.webp";
                String rarity    = cardRoot.path("rarity").asText("");
                CardCategory category = parseCategory(cardRoot.path("category").asText(""));
                CardGrade grade = GRADES[(offset + synced) % GRADES.length];

                Card card = Card.builder()
                        .userId(adminUserId)
                        .tcgdexId(tcgdexId)
                        .name(name)
                        .seriesId(null)
                        .pokemonSetId(null)
                        .pokemonId(null)
                        .cardNumber(localId)
                        .rarity(rarity.isEmpty() ? "UNKNOWN" : rarity)
                        .category(category)
                        .grade(grade)
                        .imageUrl(imageUrl)
                        .source(CardSource.TCGDEX)
                        .status(CardStatus.ACTIVE)
                        .build();

                try {
                    Card saved = cardRepository.save(card);
                    outboxEventWriter.write("card", String.valueOf(saved.getId()), "card.synced", saved.getId());
                    synced++;
                } catch (DataIntegrityViolationException e) {
                    log.warn("[CardSync] 중복 카드 스킵 (race): {}", tcgdexId);
                }

            } catch (Exception e) {
                log.warn("[CardSync] 카드 처리 실패 ({}): {}", tcgdexId, e.getMessage());
            }
        }

        return synced;
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    private CardCategory parseCategory(String category) {
        if (category == null || category.isBlank()) return CardCategory.UNKNOWN;
        return switch (category.toUpperCase()) {
            case "POKEMON"             -> CardCategory.POKEMON;
            case "TRAINER", "TRAINERS" -> CardCategory.TRAINERS;
            case "ENERGY"              -> CardCategory.ENERGY;
            default                    -> CardCategory.UNKNOWN;
        };
    }
}
