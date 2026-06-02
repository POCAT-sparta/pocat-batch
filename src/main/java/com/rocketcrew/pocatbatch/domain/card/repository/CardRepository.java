package com.rocketcrew.pocatbatch.domain.card.repository;

import com.rocketcrew.pocatbatch.domain.card.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    boolean existsByTcgdexId(String tcgdexId);

    @Query(value = "SELECT * FROM cards WHERE tcgdex_id = :tcgdexId LIMIT 1", nativeQuery = true)
    Optional<Card> findByTcgdexIdIncludingDeleted(@Param("tcgdexId") String tcgdexId);
}
