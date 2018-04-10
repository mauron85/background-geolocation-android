package com.marianhello.logging;

import android.text.TextUtils;

import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.db.names.ColumnName;
import ch.qos.logback.classic.db.names.DBNameResolver;
import ch.qos.logback.classic.db.names.TableName;

public class SQLBuilder {
    private static final String COL_SEPARATOR = ", ";

    /**
     * Generate list of levels that are same or above provided level
     * @param level
     * @return comma separated list of levels that are same or above level
     */
    private static String aboveLevel(Level level) {
        List<String> levels = new ArrayList();
        for (Level l : Level.values()) {
            if (level.compareTo(l) >= 0) {
                levels.add("'" + l.toString() + "'");
            }
        }
        return TextUtils.join(",", levels);
    }

    public static String buildSelectSQL(DBNameResolver dbNameResolver, Level minLevel) {
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
                .append(" WHERE ").append(dbNameResolver.getColumnName(ColumnName.LEVEL_STRING))
                    .append(" IN (")
                    .append(aboveLevel(minLevel)).append(")")
                .append(" ORDER BY ").append(dbNameResolver.getColumnName(ColumnName.TIMESTMP))
                .append(" DESC LIMIT ? OFFSET ?");
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
            .append(" WHERE ").append(dbNameResolver.getColumnName(ColumnName.EVENT_ID)).append(" = ?")
            .append(" ORDER BY ").append(dbNameResolver.getColumnName(ColumnName.I));
        return sqlBuilder.toString();
    }
}
