package com.rocketcrew.pocatbatch.domain.auction.entity;

import com.rocketcrew.pocatbatch.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocatbatch.domain.freepost.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "auctions")
@SQLDelete(sql = "UPDATE auctions SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Auction extends BaseEntity {

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "highest_bidder_id")
    private Long highestBidderId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "starting_price", nullable = false)
    private Long startingPrice;

    @Column(name = "buyout_price")
    private Long buyoutPrice;

    @Column(name = "highest_price")
    private Long highestPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AuctionStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "inspected_by")
    private Long inspectedBy;

    /**
     * 승인된 경매를 실제 진행 상태로 전환하고 시작/종료 시각을 확정한다.
     */
    public void activate(LocalDateTime startedAt, LocalDateTime endedAt) {
        this.status = AuctionStatus.ACTIVE;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.reason = null;
    }

    /**
     * 진행 중인 경매를 정상 종료 상태로 전환한다.
     */
    public void end() {
        this.status = AuctionStatus.ENDED;
    }
}
