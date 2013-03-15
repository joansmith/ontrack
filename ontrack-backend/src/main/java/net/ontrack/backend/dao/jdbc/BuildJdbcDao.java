package net.ontrack.backend.dao.jdbc;

import net.ontrack.backend.dao.BuildDao;
import net.ontrack.backend.dao.model.TBuild;
import net.ontrack.backend.db.SQL;
import net.ontrack.core.model.BuildFilter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
public class BuildJdbcDao extends AbstractJdbcDao implements BuildDao {

    private final RowMapper<TBuild> buildRowMapper = new RowMapper<TBuild>() {
        @Override
        public TBuild mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TBuild(
                    rs.getInt("id"),
                    rs.getInt("branch"),
                    rs.getString("name"),
                    rs.getString("description")
            );
        }
    };

    @Autowired
    public BuildJdbcDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    @Transactional(readOnly = true)
    public TBuild getById(int id) {
        return getNamedParameterJdbcTemplate().queryForObject(
                SQL.BUILD,
                params("id", id),
                buildRowMapper
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TBuild> findByBranch(int branch, int offset, int count) {
        return getNamedParameterJdbcTemplate().query(
                SQL.BUILD_LIST,
                params("branch", branch).addValue("offset", offset).addValue("count", count),
                buildRowMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TBuild> query(int branch, BuildFilter filter) {
        // Query root
        StringBuilder sql = new StringBuilder(SQL.BUILD_BY_PROMOTION_LEVEL);
        MapSqlParameterSource params = new MapSqlParameterSource("branch", branch);
        // Since last promotion level
        String sincePromotionLevel = filter.getSincePromotionLevel();
        if (StringUtils.isNotBlank(sincePromotionLevel)) {
            // Gets the last build having this promotion level
            TBuild build = getFindLastBuildWithPromotionLevel(branch, sincePromotionLevel);
            if (build != null) {
                sql.append(" AND B.ID <= :lastPromotedBuild");
                params.addValue("lastPromotedBuild", build.getId());
            }
        }
        // With promotion level
        String withPromotionLevel = filter.getWithPromotionLevel();
        if (StringUtils.isNotBlank(withPromotionLevel)) {
            sql.append(" AND PL.NAME = :withPromotionLevel");
            params.addValue("withPromotionLevel", withPromotionLevel);
        }
        // TODO Since validation stamp
        // TODO With validation stamp
        // Ordering
        sql.append(" ORDER BY B.ID DESC");
        // Limit
        sql.append(" LIMIT :limit");
        params.addValue("limit", filter.getLimit());
        // OK
        return getNamedParameterJdbcTemplate().query(
                sql.toString(),
                params,
                buildRowMapper
        );
    }

    // TODO Could be promoted up, up, up as a UI service
    private TBuild getFindLastBuildWithPromotionLevel(int branch, String promotionLevel) {
        return getFirstItem(
                SQL.BUILD_BY_PROMOTION_LEVEL + " AND PL.NAME = :name",
                params("branch", branch)
                        .addValue("name", promotionLevel),
                buildRowMapper
        );
    }

    @Override
    @Transactional
    public int createBuild(int branch, String name, String description) {
        return dbCreate(
                SQL.BUILD_CREATE,
                params("branch", branch)
                        .addValue("name", name)
                        .addValue("description", description));
    }
}
