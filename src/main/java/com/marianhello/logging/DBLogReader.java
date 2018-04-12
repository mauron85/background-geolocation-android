package com.marianhello.logging;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import org.slf4j.event.Level;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.db.names.ColumnName;
import ch.qos.logback.classic.db.names.DefaultDBNameResolver;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.android.CommonPathUtil;

public class DBLogReader {

    private static final String DB_FILENAME = "logback.db";

    private DefaultDBNameResolver mDbNameResolver;
    private SQLiteDatabase mDatabase;

    public Collection<LogEntry> getEntries(int limit, int offset, Level minLevel) {
        try {
            return getDbEntries(limit, offset, minLevel);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    private SQLiteDatabase openDatabase() throws SQLException {
        if (mDatabase != null && mDatabase.isOpen()) {
            return mDatabase;
        }

        String packageName = null;
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();

        if (context != null) {
            packageName = context.getProperty(CoreConstants.PACKAGE_NAME_KEY);
        }

        if (packageName == null || packageName.length() == 0) {
            throw new SQLException("Cannot open database without package name");
        }

        try {
            File dbfile = new File(CommonPathUtil.getDatabaseDirectoryPath(packageName), DB_FILENAME);
            mDatabase = SQLiteDatabase.openDatabase(dbfile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        } catch (SQLiteException e) {
            throw new SQLException("Cannot open database", e);
        }

        return mDatabase;
    }

    private DefaultDBNameResolver getDbNameResolver() {
        if (mDbNameResolver != null) {
            return mDbNameResolver;
        }

        mDbNameResolver = new DefaultDBNameResolver();
        return mDbNameResolver;
    }

    private Collection<String> getStackTrace(int logEntryId) throws SQLException {
        Collection<String> stackTrace = new ArrayList();
        SQLiteDatabase db = openDatabase();
        Cursor cursor = null;

        try {
            DefaultDBNameResolver dbNameResolver = getDbNameResolver();
            String entrySQL = SQLBuilder.buildSelectExceptionSQL(dbNameResolver);
            cursor = mDatabase.rawQuery(entrySQL, new String[] { String.valueOf(logEntryId) });
            while (cursor.moveToNext()) {
                stackTrace.add(cursor.getString(cursor.getColumnIndex(dbNameResolver.getColumnName(ColumnName.TRACE_LINE))));
            }
        } catch (SQLiteException e) {
            throw new SQLException("Cannot retrieve log entries", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return stackTrace;
    }

    private Collection<LogEntry> getDbEntries(int limit, int offset, Level minLevel) throws SQLException {
        Collection<LogEntry> entries = new ArrayList<LogEntry>();
        SQLiteDatabase db = openDatabase();
        Cursor cursor = null;

        try {
            DefaultDBNameResolver dbNameResolver = getDbNameResolver();
            String entrySQL = SQLBuilder.buildSelectSQL(dbNameResolver, minLevel, limit < 0);
            cursor = db.rawQuery(entrySQL, new String[] { String.valueOf(Math.abs(limit)), String.valueOf(offset) });
            while (cursor.moveToNext()) {
                LogEntry entry = new LogEntry();
                entry.setContext(0);
                entry.setId(cursor.getInt(0));
                entry.setLevel(cursor.getString(cursor.getColumnIndex(dbNameResolver.getColumnName(ColumnName.LEVEL_STRING))));
                entry.setMessage(cursor.getString(cursor.getColumnIndex(dbNameResolver.getColumnName(ColumnName.FORMATTED_MESSAGE))));
                entry.setTimestamp(cursor.getLong(cursor.getColumnIndex(dbNameResolver.getColumnName(ColumnName.TIMESTMP))));
                entry.setLoggerName(cursor.getString(cursor.getColumnIndex(dbNameResolver.getColumnName(ColumnName.LOGGER_NAME))));
                if ("ERROR".equals(entry.getLevel())) {
                    entry.setStackTrace(getStackTrace(entry.getId()));
                }
                entries.add(entry);
            }
        } catch (SQLiteException e) {
            throw new SQLException("Cannot retrieve log entries", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }

        return entries;
    }

}
