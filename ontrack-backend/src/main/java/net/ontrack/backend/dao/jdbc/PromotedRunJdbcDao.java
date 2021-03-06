package net.ontrack.backend.dao.jdbc;

import net.ontrack.backend.dao.PromotedRunDao;
import net.ontrack.backend.dao.model.TPromotedRun;
import net.ontrack.backend.db.SQL;
import net.ontrack.core.model.Ack;
import net.ontrack.dao.AbstractJdbcDao;
import net.ontrack.dao.SQLUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
public class PromotedRunJdbcDao extends AbstractJdbcDao implements PromotedRunDao {

    private final RowMapper<TPromotedRun> promotedRunRowMapper = new RowMapper<TPromotedRun>() {
        @Override
        public TPromotedRun mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TPromotedRun(
                    rs.getInt("id"),
                    rs.getInt("build"),
                    rs.getInt("promotion_level"),
                    getInteger(rs, "author_id"),
                    rs.getString("author"),
                    SQLUtils.getDateTime(rs, "creation"),
                    rs.getString("description")
            );
        }
    };

    @Autowired
    public PromotedRunJdbcDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    @Transactional(readOnly = true)
    public TPromotedRun findByBuildAndPromotionLevel(int build, int promotionLevel) {
        try {
            return getNamedParameterJdbcTemplate().queryForObject(
                    SQL.PROMOTED_RUN,
                    params("build", build).addValue("promotionLevel", promotionLevel),
                    promotedRunRowMapper
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    @Transactional
    public int createPromotedRun(int build, int promotionLevel, String author, Integer authorId, DateTime creation, String description) {
        // Deletes any one before
        getNamedParameterJdbcTemplate().update(
                SQL.PROMOTED_RUN_DELETE,
                params("build", build)
                        .addValue("promotionLevel", promotionLevel)
        );
        // Creation
        return dbCreate(
                SQL.PROMOTED_RUN_CREATE,
                params("build", build)
                        .addValue("promotionLevel", promotionLevel)
                        .addValue("authorId", authorId)
                        .addValue("author", author)
                        .addValue("creation", SQLUtils.toTimestamp(creation))
                        .addValue("description", description));
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findBuildByEarliestPromotion(int buildId, int promotionLevelId) {
        return getFirstItem(
                SQL.PROMOTED_EARLIEST_RUN,
                params("build", buildId).addValue("promotionLevel", promotionLevelId),
                Integer.class
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TPromotedRun> findByPromotionLevel(int promotionLevel, int offset, int count) {
        return getNamedParameterJdbcTemplate().query(
                SQL.PROMOTED_RUN_BY_PROMOTION_LEVEL,
                params("promotionLevel", promotionLevel).addValue("count", count).addValue("offset", offset),
                promotedRunRowMapper
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TPromotedRun> findByBuild(int buildId) {
        return getNamedParameterJdbcTemplate().query(
                SQL.PROMOTED_RUN_BY_BUILD,
                params("build", buildId),
                promotedRunRowMapper
        );
    }

    @Override
    @Transactional
    public Ack remove(int buildId, int promotionLevelId) {
        return Ack.one(
                getNamedParameterJdbcTemplate().update(
                        SQL.PROMOTED_RUN_REMOVE,
                        params("build", buildId).addValue("promotionLevel", promotionLevelId)
                )
        );
    }
}
