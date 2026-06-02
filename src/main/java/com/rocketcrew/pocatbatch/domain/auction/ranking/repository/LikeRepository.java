package com.rocketcrew.pocatbatch.domain.auction.ranking.repository;

import com.rocketcrew.pocatbatch.domain.auction.ranking.dto.AuctionCountProjection;
import com.rocketcrew.pocatbatch.domain.like.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Long> {

    @Query("SELECT l.auctionId AS auctionId, COUNT(l) AS cnt FROM Like l WHERE l.auctionId IN :auctionIds GROUP BY l.auctionId")
    List<AuctionCountProjection> countByAuctionIdIn(@Param("auctionIds") List<Long> auctionIds);
}
