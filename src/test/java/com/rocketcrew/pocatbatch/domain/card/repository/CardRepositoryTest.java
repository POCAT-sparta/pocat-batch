package com.rocketcrew.pocatbatch.domain.card.repository;

import com.rocketcrew.pocatbatch.domain.card.entity.Card;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CardRepositoryTest {

    @Autowired
    private CardRepository cardRepository;

    private Card saveCard(CardStatus status) {
        Card card = Card.builder()
                .userId(1L)
                .tcgdexId(null)
                .name("테스트 카드")
                .seriesId(null)
                .pokemonSetId(null)
                .pokemonId(null)
                .cardNumber("001")
                .rarity("Common")
                .category(CardCategory.POKEMON)
                .grade(CardGrade.UNGRADED)
                .imageUrl(null)
                .source(CardSource.MANUAL)
                .status(status)
                .build();
        return cardRepository.save(card);
    }

    @Test
    void findActiveCardIdsAfter_cursor_이후_ACTIVE_카드만_ID_오름차순으로_반환한다() {
        // given
        Card active1 = saveCard(CardStatus.ACTIVE);
        Card pending = saveCard(CardStatus.PENDING);
        Card active2 = saveCard(CardStatus.ACTIVE);
        Card rejected = saveCard(CardStatus.REJECTED);
        Card active3 = saveCard(CardStatus.ACTIVE);

        // when: cursor=0, limit=100 -> 모든 ACTIVE 카드를 id 오름차순으로 반환
        List<Long> result = cardRepository.findActiveCardIdsAfter(0L, PageRequest.of(0, 100));

        // then
        assertThat(result).containsExactly(active1.getId(), active2.getId(), active3.getId());
        assertThat(result).doesNotContain(pending.getId(), rejected.getId());
    }

    @Test
    void findActiveCardIdsAfter_cursor_보다_작거나_같은_ID는_제외한다() {
        // given
        Card active1 = saveCard(CardStatus.ACTIVE);
        Card active2 = saveCard(CardStatus.ACTIVE);
        Card active3 = saveCard(CardStatus.ACTIVE);

        // when: active1.getId()를 cursor로 사용 -> active1은 제외
        List<Long> result = cardRepository.findActiveCardIdsAfter(active1.getId(), PageRequest.of(0, 100));

        // then
        assertThat(result).containsExactly(active2.getId(), active3.getId());
    }

    @Test
    void findActiveCardIdsAfter_limit을_적용하여_지정된_개수만큼만_반환한다() {
        // given
        Card active1 = saveCard(CardStatus.ACTIVE);
        Card active2 = saveCard(CardStatus.ACTIVE);
        saveCard(CardStatus.ACTIVE);

        // when: limit=2
        List<Long> result = cardRepository.findActiveCardIdsAfter(0L, PageRequest.of(0, 2));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(active1.getId(), active2.getId());
    }
}
