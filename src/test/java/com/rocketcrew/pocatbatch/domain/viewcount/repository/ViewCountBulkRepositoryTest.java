package com.rocketcrew.pocatbatch.domain.viewcount.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ViewCountBulkRepositoryTest {

    @Autowired
    private ViewCountBulkRepository bulkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void insertFreePost(long id, int viewCount, int commentCount) {
        jdbcTemplate.update(
                "INSERT INTO free_posts (id, user_id, title, content, view_count, comment_count) " +
                        "VALUES (?, 1, 't', 'c', ?, ?)",
                id, viewCount, commentCount);
    }

    private void insertTradePost(long id, int viewCount) {
        // 배치 TradePost 엔티티는 view_count만 매핑 → H2 trade_posts 테이블에는
        // id/view_count + BaseEntity 공통 컬럼만 존재한다. (price 등 미매핑 컬럼 INSERT 금지)
        jdbcTemplate.update(
                "INSERT INTO trade_posts (id, view_count) VALUES (?, ?)",
                id, viewCount);
    }

    private int freeViewCount(long id) {
        return jdbcTemplate.queryForObject("SELECT view_count FROM free_posts WHERE id = ?", Integer.class, id);
    }

    private int freeCommentCount(long id) {
        return jdbcTemplate.queryForObject("SELECT comment_count FROM free_posts WHERE id = ?", Integer.class, id);
    }

    private int tradeViewCount(long id) {
        return jdbcTemplate.queryForObject("SELECT view_count FROM trade_posts WHERE id = ?", Integer.class, id);
    }

    @Test
    void 자유글_조회수_다건_배치_증가() {
        insertFreePost(9001, 5, 0);
        insertFreePost(9002, 10, 0);

        bulkRepository.increaseFreePostViewCount(Map.of(9001L, 3, 9002L, 7));

        assertThat(freeViewCount(9001)).isEqualTo(8);
        assertThat(freeViewCount(9002)).isEqualTo(17);
    }

    @Test
    void 자유글_댓글수는_0_미만으로_내려가지_않음() {
        insertFreePost(9101, 0, 2);

        bulkRepository.increaseFreePostCommentCount(Map.of(9101L, -5));

        assertThat(freeCommentCount(9101)).isZero();
    }

    @Test
    void 거래글_조회수_배치_증가() {
        insertTradePost(9201, 4);

        bulkRepository.increaseTradePostViewCount(Map.of(9201L, 6));

        assertThat(tradeViewCount(9201)).isEqualTo(10);
    }

    @Test
    void 빈_맵은_아무것도_하지_않음() {
        int[] result = bulkRepository.increaseFreePostViewCount(Map.of());
        assertThat(result).isEmpty();
    }
}
