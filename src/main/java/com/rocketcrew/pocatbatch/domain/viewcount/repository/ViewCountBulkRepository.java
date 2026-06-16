package com.rocketcrew.pocatbatch.domain.viewcount.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ViewCountBulkRepository {

    private static final String FREE_VIEW_SQL =
            "UPDATE free_posts SET view_count = view_count + ? WHERE id = ?";
    private static final String FREE_COMMENT_SQL =
            "UPDATE free_posts SET comment_count = GREATEST(0, comment_count + ?) WHERE id = ?";
    private static final String TRADE_VIEW_SQL =
            "UPDATE trade_posts SET view_count = view_count + ? WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] increaseFreePostViewCount(Map<Long, Integer> deltas) {
        return batchUpdate(FREE_VIEW_SQL, deltas);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] increaseFreePostCommentCount(Map<Long, Integer> deltas) {
        return batchUpdate(FREE_COMMENT_SQL, deltas);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] increaseTradePostViewCount(Map<Long, Integer> deltas) {
        return batchUpdate(TRADE_VIEW_SQL, deltas);
    }

    private int[] batchUpdate(String sql, Map<Long, Integer> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return new int[0];
        }
        List<Map.Entry<Long, Integer>> entries = new ArrayList<>(deltas.entrySet());
        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map.Entry<Long, Integer> entry = entries.get(i);
                ps.setInt(1, entry.getValue());
                ps.setLong(2, entry.getKey());
            }

            @Override
            public int getBatchSize() {
                return entries.size();
            }
        });
    }
}
