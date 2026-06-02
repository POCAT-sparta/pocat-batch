package com.rocketcrew.pocatbatch.domain.auction.repository;

import com.rocketcrew.pocatbatch.domain.auction.entity.Auction;
import com.rocketcrew.pocatbatch.domain.auction.enums.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    List<Auction> findAllByStatus(AuctionStatus status);

    List<Auction> findAllByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(
            AuctionStatus status,
            LocalDateTime endedAt
    );

    List<Auction> findAllByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
            AuctionStatus status,
            LocalDateTime updatedAt
    );
}
