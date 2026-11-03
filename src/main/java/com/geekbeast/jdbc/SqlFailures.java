package com.geekbeast.jdbc;

import java.sql.SQLException;

/** Database failure metadata without driver text, bound values, or cause chains. */
public final class SqlFailures {
    private SqlFailures() {}

    public static StorageFailure sanitize(SQLException failure) {
        StorageFailure sanitized = new StorageFailure(failure.getSQLState(), failure.getClass().getSimpleName());
        sanitized.setStackTrace(failure.getStackTrace());
        return sanitized;
    }

    public static final class StorageFailure extends IllegalStateException {
        private final String sqlState;
        private final String category;

        private StorageFailure(String sqlState, String category) {
            super("Database operation failed; category=" + category + "; sqlState=" + sqlState);
            this.sqlState = sqlState;
            this.category = category;
        }

        public String getSqlState() { return sqlState; }
        public String getCategory() { return category; }
    }
}
