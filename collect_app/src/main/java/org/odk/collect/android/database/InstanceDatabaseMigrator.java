package org.odk.collect.android.database;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import org.odk.collect.android.instances.Instance;
import org.odk.collect.android.utilities.SQLiteUtils;

import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

import static android.provider.BaseColumns._ID;
import static org.odk.collect.android.database.DatabaseConstants.FORMS_DATABASE_VERSION;
import static org.odk.collect.android.database.DatabaseConstants.FORMS_TABLE_NAME;
import static org.odk.collect.android.database.DatabaseConstants.INSTANCES_DATABASE_NAME;
import static org.odk.collect.android.database.DatabaseConstants.INSTANCES_DATABASE_VERSION;
import static org.odk.collect.android.database.DatabaseConstants.INSTANCES_TABLE_NAME;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.ACT_LAT;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.ACT_LON;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.CAN_EDIT_WHEN_COMPLETE;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.DELETED_DATE;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.DISPLAY_NAME;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.FORM_PATH;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.GEOMETRY;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.GEOMETRY_TYPE;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.JR_FORM_ID;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.JR_VERSION;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.LAST_STATUS_CHANGE_DATE;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.SCHED_LAT;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.SCHED_LON;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.SOURCE;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.STATUS;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.SUBMISSION_URI;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_ACT_FINISH;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_ACT_START;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_ADDRESS;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_ASS_ID;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_HIDE;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_IS_SYNC;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_LOCATION_TRIGGER;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_REPEAT;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_SCHED_FINISH;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_SCHED_START;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_SHOW_DIST;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_SURVEY_NOTES;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_TASK_COMMENT;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_TASK_STATUS;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_TITLE;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_UPDATED;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_UPDATEID;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.UUID;

public class InstanceDatabaseMigrator implements DatabaseMigrator {
    private static final String[] COLUMN_NAMES_V5 = {_ID, DISPLAY_NAME, SUBMISSION_URI, CAN_EDIT_WHEN_COMPLETE,
            INSTANCE_FILE_PATH, JR_FORM_ID, JR_VERSION, STATUS, LAST_STATUS_CHANGE_DATE, DELETED_DATE};

    private static final String[] COLUMN_NAMES_V6 = {_ID, DISPLAY_NAME, SUBMISSION_URI,
            CAN_EDIT_WHEN_COMPLETE, INSTANCE_FILE_PATH, JR_FORM_ID, JR_VERSION, STATUS,
            LAST_STATUS_CHANGE_DATE, DELETED_DATE, GEOMETRY, GEOMETRY_TYPE};

    public static final String[] CURRENT_VERSION_COLUMN_NAMES = COLUMN_NAMES_V6;

    public void onCreate(SQLiteDatabase db) {
        createLatestVersion(db);        // smap
    }

    @SuppressWarnings({"checkstyle:FallThrough"})
    public void onUpgrade(SQLiteDatabase db, int oldVersion) {
        if(oldVersion < INSTANCES_DATABASE_VERSION) {   // smap
            upgradeToLatestVersion(db);
        }
    }

    public void onDowngrade(SQLiteDatabase db) {
      // smap ignore
    }

    private void upgradeToLatestVersion(SQLiteDatabase db) {
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, GEOMETRY, "text");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, GEOMETRY_TYPE, "text");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_IS_SYNC, "text");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_ASS_ID, "long");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_TASK_STATUS, "text");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_TASK_COMMENT, "text");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_REPEAT, "integer");

        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_UPDATEID, "text");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_LOCATION_TRIGGER, "text");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_SURVEY_NOTES, "text");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, UUID, "text");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_UPDATED, "integer");

        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_SHOW_DIST, "integer");
        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, T_HIDE, "integer");

        SQLiteUtils.addColumn(db, INSTANCES_DATABASE_NAME, "displaySubtext", "text");
    }


    // smap
    public static void createLatestVersion(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + INSTANCES_TABLE_NAME + " ("
                + _ID + " integer primary key, "
                + DISPLAY_NAME + " text not null, "
                + SUBMISSION_URI + " text, "
                + CAN_EDIT_WHEN_COMPLETE + " text, "
                + INSTANCE_FILE_PATH + " text not null, "
                + JR_FORM_ID + " text not null, "
                + JR_VERSION + " text, "
                + STATUS + " text not null, "
                + LAST_STATUS_CHANGE_DATE + " date not null, "
                + DELETED_DATE + " date, "
                + SOURCE + " text, "		    // smap
                + FORM_PATH + " text, "		    // smap
                + ACT_LON + " double, "		    // smap
                + ACT_LAT + " double, "		    // smap
                + SCHED_LON + " double, "		// smap
                + SCHED_LAT + " double, "		// smap
                + T_TITLE + " text, "		    // smap
                + T_SCHED_START + " long, "		// smap
                + T_SCHED_FINISH + " long, "	// smap
                + T_ACT_START + " long, "		// smap
                + T_ACT_FINISH + " long, "		// smap
                + T_ADDRESS + " text, "		    // smap
                + GEOMETRY + " text, "		    // smap
                + GEOMETRY_TYPE + " text, "		// smap
                + T_IS_SYNC + " text, "		    // smap
                + T_ASS_ID + " long, "		    // smap
                + T_TASK_STATUS + " text, "		// smap
                + T_TASK_COMMENT + " text, "    // smap
                + T_REPEAT + " integer, "		// smap
                + T_UPDATEID + " text, "		// smap
                + T_LOCATION_TRIGGER + " text, " // smap
                + T_SURVEY_NOTES + " text, "    // smap
                + UUID + " text, "		        // smap
                + T_UPDATED + " integer, "      // smap
                + T_SHOW_DIST + " integer, "    // smap
                + T_HIDE + " integer, "         // smap

                + "displaySubtext text "   // Smap keep for downgrading
                + ");");
    }

    // smap
    public static void recreateDatabase() {

        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(InstancesDatabaseHelper.getDatabasePath(), null, SQLiteDatabase.OPEN_READWRITE);
            SQLiteUtils.dropTable(db, INSTANCES_TABLE_NAME);
            createLatestVersion(db);
            db.close();
        } catch (SQLException e) {
            Timber.i(e);
        }
    }
}
