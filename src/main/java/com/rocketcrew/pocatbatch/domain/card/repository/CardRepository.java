package com.rocketcrew.pocatbatch.domain.card.repository;

import com.rocketcrew.pocatbatch.domain.card.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Long> {

    boolean existsByTcgdexId(String tcgdexId);
}
