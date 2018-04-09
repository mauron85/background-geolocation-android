package com.marianhello.logging;

import ch.qos.logback.classic.db.names.ColumnName;
import ch.qos.logback.classic.db.names.DBNameResolver;
import ch.qos.logback.classic.db.names.TableName;

public class SQLBuilder {
    private static final String COL_SEPARATOR = ", ";

    public static String buildSelectSQL(DBNameResolver dbNameResolver) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT ")
                .append("ROWID").append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.EVENT_ID)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.TIMESTMP)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.FORMATTED_MESSAGE)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.LOGGER_NAME)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.LEVEL_STRING)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.THREAD_NAME)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.REFERENCE_FLAG)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.ARG0)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.ARG1)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.ARG2)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.ARG3)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.CALLER_FILENAME)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.CALLER_CLASS)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.CALLER_METHOD)).append(COL_SEPARATOR)
                .append(dbNameResolver.getColumnName(ColumnName.CALLER_LINE))
                .append(" FROM ").append(dbNameResolver.getTableName(TableName.LOGGING_EVENT))
                .append(" ORDER BY ").append(dbNameResolver.getColumnName(ColumnName.TIMESTMP))
                .append(" DESC LIMIT ?");
        return sqlBuilder.toString();
    }

    public static String buildSelectPropertiesSQL(DBNameResolver dbNameResolver) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT ")
            .append(dbNameResolver.getColumnName(ColumnName.MAPPED_KEY)).append(COL_SEPARATOR)
            .append(dbNameResolver.getColumnName(ColumnName.MAPPED_VALUE))
            .append(" FROM ").append(dbNameResolver.getTableName(TableName.LOGGING_EVENT_PROPERTY))
            .append(" WHERE ").append(dbNameResolver.getColumnName(ColumnName.EVENT_ID)).append(" = ?");
        return sqlBuilder.toString();
    }

    public static String buildSelectExceptionSQL(DBNameResolver dbNameResolver) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT ")
            .append(dbNameResolver.getColumnName(ColumnName.TRACE_LINE))
            .append(" FROM ").append(dbNameResolver.getTableName(TableName.LOGGING_EVENT_EXCEPTION))
            .append(" ORDER BY ").append(dbNameResolver.getColumnName(ColumnName.I))
            .append(" WHERE ").append(dbNameResolver.getColumnName(ColumnName.EVENT_ID)).append(" = ?");
        return sqlBuilder.toString();
    }
}
