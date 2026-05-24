package com.rocketcrew.pocatbatch.domain.freepost.repository;

import com.rocketcrew.pocatbatch.domain.freepost.entity.FreePost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FreePostRepository extends JpaRepository<FreePost, Long> {

    int COMMENT_WEIGHT = 3;

    @Modifying
    @Query("UPDATE FreePost f SET f.viewCount = f.viewCount + :count WHERE f.id = :postId")
    int increaseViewCount(@Param("postId") Long postId, @Param("count") int count);

    @Modifying
    @Query("UPDATE FreePost f SET f.commentCount = GREATEST(0, f.commentCount + :delta) WHERE f.id = :postId")
    int updateCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Query("SELECT f FROM FreePost f WHERE f.createdAt >= :since ORDER BY (f.viewCount + f.commentCount * 3) DESC")
    List<FreePost> findTopByPopularScore(Pageable pageable, @Param("since") LocalDateTime since);
}
