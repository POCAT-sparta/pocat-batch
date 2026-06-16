package com.rocketcrew.pocatbatch.domain.tradepost.entity;

import com.rocketcrew.pocatbatch.domain.freepost.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "trade_posts")
@SQLRestriction("deleted_at IS NULL")
public class TradePost extends BaseEntity {

    @Column(name = "view_count", nullable = false)
    private int viewCount;
}
