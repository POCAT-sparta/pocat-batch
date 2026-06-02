package com.rocketcrew.pocatbatch.domain.card.entity;

import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocatbatch.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocatbatch.domain.freepost.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "cards",
        indexes = {
                @Index(name = "idx_cards_status", columnList = "status"),
                @Index(name = "idx_cards_user_id_status", columnList = "user_id, status"),
                @Index(name = "idx_cards_grade", columnList = "grade"),
                @Index(name = "idx_cards_category", columnList = "category"),
                @Index(name = "idx_cards_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE cards SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Card extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tcgdex_id", length = 100)
    private String tcgdexId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "pokemon_set_id")
    private Long pokemonSetId;

    @Column(name = "pokemon_id")
    private Long pokemonId;

    @Column(name = "card_number", length = 20, nullable = false)
    private String cardNumber;

    @Column(name = "rarity", length = 50, nullable = false)
    private String rarity;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private CardCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 20)
    private CardGrade grade;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CardSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CardStatus status;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;
}
