package net.ontrack.backend.dao.jdbc;

import net.ontrack.backend.dao.ValidationStampSelectionDao;
import net.ontrack.backend.db.SQL;
import net.ontrack.dao.AbstractJdbcDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

@Component
public class ValidationStampSelectionJdbcDao extends AbstractJdbcDao implements ValidationStampSelectionDao {

    @Autowired
    public ValidationStampSelectionJdbcDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFiltered(int account, int validationStamp) {
        return getFirstItem(
                SQL.VALIDATION_STAMP_SELECTION_EXISTS,
                params("account", account).addValue("validationStamp", validationStamp),
                Integer.class) != null;
    }

    @Override
    @Transactional
    public void removeFilterValidationStamp(int account, int validationStamp) {
        getNamedParameterJdbcTemplate().update(
                SQL.VALIDATION_STAMP_SELECTION_DELETE,
                params("account", account).addValue("validationStamp", validationStamp)
        );
    }

    @Override
    @Transactional
    public void addFilterValidationStamp(int account, int validationStamp) {
        removeFilterValidationStamp(account, validationStamp);
        getNamedParameterJdbcTemplate().update(
                SQL.VALIDATION_STAMP_SELECTION_INSERT,
                params("account", account).addValue("validationStamp", validationStamp)
        );
    }
}
