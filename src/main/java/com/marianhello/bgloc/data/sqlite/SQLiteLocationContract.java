package com.marianhello.bgloc.data.sqlite;

import android.net.Uri;
import android.provider.BaseColumns;

import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.COMMA_SEP;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.INTEGER_TYPE;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.REAL_TYPE;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.TEXT_TYPE;

public final class SQLiteLocationContract {
    /**
     * The authority of the notes content provider
     */
    public static final String AUTHORITY = "com.marianhello.bgloc.data.provider";

    /**
     * The content URI for the top-level notes authority
     */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    // To prevent someone from accidentally instantiating the contract class,
    // give it an empty constructor.
    public SQLiteLocationContract() {}

    /* Inner class that defines the table contents */
    public static abstract class LocationEntry implements BaseColumns {
        public static final String TABLE_NAME = "location";
        public static final String COLUMN_NAME_NULLABLE = "NULLHACK";
        public static final String COLUMN_NAME_TIME = "time";
        public static final String COLUMN_NAME_ACCURACY = "accuracy";
        public static final String COLUMN_NAME_SPEED = "speed";
        public static final String COLUMN_NAME_BEARING = "bearing";
        public static final String COLUMN_NAME_ALTITUDE = "altitude";
        public static final String COLUMN_NAME_LATITUDE = "latitude";
        public static final String COLUMN_NAME_LONGITUDE = "longitude";
        public static final String COLUMN_NAME_RADIUS = "radius";
        public static final String COLUMN_NAME_HAS_ACCURACY = "has_accuracy";
        public static final String COLUMN_NAME_HAS_SPEED = "has_speed";
        public static final String COLUMN_NAME_HAS_BEARING = "has_bearing";
        public static final String COLUMN_NAME_HAS_ALTITUDE = "has_altitude";
        public static final String COLUMN_NAME_HAS_RADIUS = "has_radius";
        public static final String COLUMN_NAME_PROVIDER = "provider";
        public static final String COLUMN_NAME_LOCATION_PROVIDER = "service_provider";
        public static final String COLUMN_NAME_STATUS = "valid";
        public static final String COLUMN_NAME_BATCH_START_MILLIS = "batch_start";
        public static final String COLUMN_NAME_MOCK_FLAGS = "mock_flags";

        public static final String SQL_CREATE_LOCATION_TABLE =
                "CREATE TABLE " + LocationEntry.TABLE_NAME + " (" +
                        LocationEntry._ID + " INTEGER PRIMARY KEY," +
                        LocationEntry.COLUMN_NAME_TIME + INTEGER_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_ACCURACY + REAL_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_SPEED + REAL_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_BEARING + REAL_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_ALTITUDE + REAL_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_LATITUDE + REAL_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_LONGITUDE + REAL_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_RADIUS + REAL_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_HAS_ACCURACY + INTEGER_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_HAS_SPEED + INTEGER_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_HAS_BEARING + INTEGER_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_HAS_ALTITUDE + INTEGER_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_HAS_RADIUS + INTEGER_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_PROVIDER + TEXT_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_LOCATION_PROVIDER + INTEGER_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_STATUS + INTEGER_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_BATCH_START_MILLIS + INTEGER_TYPE + COMMA_SEP +
                        LocationEntry.COLUMN_NAME_MOCK_FLAGS + INTEGER_TYPE +
                        " )";

        public static final String SQL_DROP_LOCATION_TABLE =
                "DROP TABLE IF EXISTS " + LocationEntry.TABLE_NAME;

        public static final String SQL_CREATE_LOCATION_TABLE_TIME_IDX =
                "CREATE INDEX time_idx ON " + LocationEntry.TABLE_NAME + " (" + LocationEntry.COLUMN_NAME_TIME + ")";

        public static final String SQL_CREATE_LOCATION_TABLE_BATCH_ID_IDX =
                "CREATE INDEX batch_id_idx ON " + LocationEntry.TABLE_NAME + " (" + LocationEntry.COLUMN_NAME_BATCH_START_MILLIS + ")";
    }
}
