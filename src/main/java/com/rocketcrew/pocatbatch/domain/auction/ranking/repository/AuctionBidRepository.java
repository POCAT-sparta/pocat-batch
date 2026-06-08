package com.rocketcrew.pocatbatch.domain.auction.ranking.repository;

import com.rocketcrew.pocatbatch.domain.auction.ranking.dto.AuctionCountProjection;
import com.rocketcrew.pocatbatch.domain.bid.entity.AuctionBid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {

    @Query("SELECT b.auctionId AS auctionId, COUNT(b) AS cnt FROM AuctionBid b WHERE b.auctionId IN :auctionIds GROUP BY b.auctionId")
    List<AuctionCountProjection> countByAuctionIdIn(@Param("auctionIds") List<Long> auctionIds);

    `@Query`("SELECT DISTINCT b.userId FROM AuctionBid b WHERE b.auctionId = :auctionId AND b.userId <> :winnerId ORDER BY b.userId")
    List<Long> findLoserIdsByAuctionIdExcluding(`@Param`("auctionId") Long auctionId, `@Param`("winnerId") Long winnerId);

    `@Query`("SELECT DISTINCT b.userId FROM AuctionBid b WHERE b.auctionId = :auctionId ORDER BY b.userId")
    List<Long> findAllBidderIdsByAuctionId(`@Param`("auctionId") Long auctionId);
}
