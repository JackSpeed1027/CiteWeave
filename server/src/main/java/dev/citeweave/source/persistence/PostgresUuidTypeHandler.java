package dev.citeweave.source.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/** 保持 PostgreSQL uuid 列在 MyBatis 中直接映射为 Java UUID。 */
@MappedTypes(UUID.class)
public class PostgresUuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, UUID value, JdbcType type)
            throws SQLException {
        statement.setObject(index, value, Types.OTHER);
    }

    @Override
    public UUID getNullableResult(ResultSet result, String column) throws SQLException {
        return result.getObject(column, UUID.class);
    }

    @Override
    public UUID getNullableResult(ResultSet result, int index) throws SQLException {
        return result.getObject(index, UUID.class);
    }

    @Override
    public UUID getNullableResult(CallableStatement statement, int index) throws SQLException {
        return statement.getObject(index, UUID.class);
    }
}
