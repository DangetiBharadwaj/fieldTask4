/*
 * Copyright (C) 2014 Smap Consulting Pty Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.utilities;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.database.TaskAssignment;
import org.odk.collect.android.http.openrosa.OpenRosaHttpInterface;
import org.odk.collect.android.loaders.GeofenceEntry;
import org.odk.collect.android.loaders.TaskEntry;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.provider.InstanceProvider;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.taskModel.InstanceXML;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;

import timber.log.Timber;

import static java.lang.StrictMath.abs;
import static org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns.T_TASK_STATUS;

public class Utilities {

    @Inject
    public OpenRosaHttpInterface httpInterface;

    @Inject
    public WebCredentialsUtils webCredentialsUtils;

    // Valid values for task status
    public static final String STATUS_T_ACCEPTED = "accepted";
    public static final String STATUS_T_REJECTED = "rejected";
    public static String STATUS_T_COMPLETE = "complete";
    public static final String STATUS_T_SUBMITTED = "submitted";
    public static final String STATUS_T_CANCELLED = "cancelled";
    public static final String STATUS_T_CLOSED = "closed";
    public static final String STATUS_T_NEW = "new";

    // Valid values for is synced
    public static final String STATUS_SYNC_YES = "synchronized";
    public static final String STATUS_SYNC_NO = "not synchronized";

    public Utilities() {Collect.getInstance().getComponent().inject(this);}
    // Get the task source
    public static String getSource() {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(Collect.getInstance()
                        .getBaseContext());
        String serverUrl = sharedPreferences.getString(
                GeneralKeys.KEY_SERVER_URL, null);
        String source = STFileUtils.getSource(serverUrl);


        return source;
    }

    public static String getOrgMediaPath() {
        String source = getSource();
        return Collect.FORMS_PATH + File.separator + "smap_media" + File.separator + source;
    }

    public static TaskEntry getTaskWithIdOrPath(long id, String instancePath) {

        TaskEntry entry = null;

        // Get cursor
        String[] proj = {
                InstanceColumns._ID,
                InstanceColumns.T_TITLE,
                InstanceColumns.T_TASK_STATUS,
                InstanceColumns.T_REPEAT,
                InstanceColumns.T_SCHED_START,
                InstanceColumns.T_SCHED_FINISH,
                InstanceColumns.T_ADDRESS,
                InstanceColumns.FORM_PATH,
                InstanceColumns.JR_FORM_ID,
                InstanceColumns.INSTANCE_FILE_PATH,
                InstanceColumns.SCHED_LON,
                InstanceColumns.SCHED_LAT,
                InstanceColumns.ACT_LON,
                InstanceColumns.ACT_LAT,
                InstanceColumns.T_SHOW_DIST,
                InstanceColumns.T_ACT_FINISH,
                InstanceColumns.T_IS_SYNC,
                InstanceColumns.T_ASS_ID,
                InstanceColumns.T_LOCATION_TRIGGER,
                InstanceColumns.UUID

        };

        String selectClause = InstanceColumns._ID + " = " + id;
        if (instancePath != null) {
            selectClause = InstanceColumns.INSTANCE_FILE_PATH + " = '" + instancePath + "'";
        }

        final ContentResolver resolver = Collect.getInstance().getContentResolver();
        Cursor c = resolver.query(InstanceColumns.CONTENT_URI, proj, selectClause, null, null);

        try {
            c.moveToFirst();
            DateFormat dFormat = DateFormat.getDateTimeInstance();

            entry = new TaskEntry();

            entry.type = "task";
            entry.id = c.getLong(c.getColumnIndex(InstanceColumns._ID));
            entry.name = c.getString(c.getColumnIndex(InstanceColumns.T_TITLE));
            entry.taskStatus = c.getString(c.getColumnIndex(InstanceColumns.T_TASK_STATUS));
            entry.repeat = (c.getInt(c.getColumnIndex(InstanceColumns.T_REPEAT)) > 0);
            entry.taskStart = c.getLong(c.getColumnIndex(InstanceColumns.T_SCHED_START));
            entry.taskFinish = c.getLong(c.getColumnIndex(InstanceColumns.T_SCHED_FINISH));
            entry.taskAddress = c.getString(c.getColumnIndex(InstanceColumns.T_ADDRESS));
            entry.taskForm = c.getString(c.getColumnIndex(InstanceColumns.FORM_PATH));
            entry.jrFormId = c.getString(c.getColumnIndex(InstanceColumns.JR_FORM_ID));
            entry.instancePath = c.getString(c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
            entry.schedLon = c.getDouble(c.getColumnIndex(InstanceColumns.SCHED_LON));
            entry.schedLat = c.getDouble(c.getColumnIndex(InstanceColumns.SCHED_LAT));
            entry.showDist = c.getInt(c.getColumnIndex(InstanceColumns.T_SHOW_DIST));
            entry.actLon = c.getDouble(c.getColumnIndex(InstanceColumns.ACT_LON));
            entry.actLat = c.getDouble(c.getColumnIndex(InstanceColumns.ACT_LAT));
            entry.actFinish = c.getLong(c.getColumnIndex(InstanceColumns.T_ACT_FINISH));
            entry.isSynced = c.getString(c.getColumnIndex(InstanceColumns.T_IS_SYNC));
            entry.locationTrigger = c.getString(c.getColumnIndex(InstanceColumns.T_LOCATION_TRIGGER));
            entry.uuid = c.getString(c.getColumnIndex(InstanceColumns.UUID));

        } catch (Exception e) {
            Timber.i("Get task with ID or path: ID: " + id + " Path: " + instancePath);
            Timber.e(e);
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception e) {
                }
            }
        }

        return entry;
    }

    public static void duplicateTask(String originalInstancePath, String newInstancePath, TaskEntry entry) {


        try {

            ContentValues values = new ContentValues();
            values.put(InstanceColumns.T_TITLE, entry.name);
            values.put(InstanceColumns.DISPLAY_NAME, entry.displayName);
            values.put(InstanceColumns.T_TASK_STATUS, entry.taskStatus);
            values.put(InstanceColumns.STATUS, entry.taskStatus);
            values.put(InstanceColumns.T_REPEAT, true);     // Duplicated task should also be a repeat
            values.put(InstanceColumns.T_SCHED_START, entry.taskStart);
            values.put(InstanceColumns.T_SCHED_FINISH, entry.taskFinish);
            values.put(InstanceColumns.T_ADDRESS, entry.taskAddress);
            values.put(InstanceColumns.FORM_PATH, entry.taskForm);
            values.put(InstanceColumns.JR_FORM_ID, entry.jrFormId);
            values.put(InstanceColumns.JR_VERSION, entry.formVersion);
            values.put(InstanceColumns.INSTANCE_FILE_PATH, newInstancePath);    // Set the new path
            values.put(InstanceColumns.SCHED_LON, entry.schedLon);
            values.put(InstanceColumns.SCHED_LAT, entry.schedLat);
            values.put(InstanceColumns.SOURCE, entry.source);
            values.put(InstanceColumns.T_LOCATION_TRIGGER, entry.locationTrigger);
            values.put(InstanceColumns.T_ASS_ID, entry.assId);

            final ContentResolver resolver = Collect.getInstance().getContentResolver();
            resolver.insert(InstanceColumns.CONTENT_URI, values);

            // Update the existing task and set it to a non repeat in case the user exits out without saving
            Uri initialUri = Uri.withAppendedPath(InstanceProviderAPI.InstanceColumns.CONTENT_URI, String.valueOf(entry.id));

            values = new ContentValues();
            values.put(InstanceColumns.T_REPEAT, 0);
            values.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_INCOMPLETE);

            Collect.getInstance().getContentResolver().update(initialUri, values, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Common routine to download an instance XML document including any attachments
     * <p>
     * Smap Specific
     *
     * @param file        the final file
     * @param downloadUrl the url to get the contents from.
     * @throws Exception
     */
    public void downloadInstanceFile(File file, String downloadUrl, String serverUrl, String formId, int version) throws Exception {

        String t = "DownloadInstanceFile";

        URI uri;
        try {
            // assume the downloadUrl is escaped properly
            URL url = new URL(downloadUrl);
            uri = url.toURI();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw e;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw e;
        }

        InputStream is = httpInterface.executeGetRequest(uri, null, webCredentialsUtils.getCredentials(uri)).getInputStream();


        try {

            // Create instance object
            Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd hh:mm").create();

            OutputStream os = null;
            try {

                Reader isReader = new InputStreamReader(is);

                os = new FileOutputStream(file);
                byte buf[] = new byte[4096];
                int len;

                if (version < 1) {
                    while ((len = is.read(buf)) > 0) {
                        os.write(buf, 0, len);
                    }
                } else {
                    InstanceXML instance = gson.fromJson(isReader, InstanceXML.class);

                    os.write(instance.instanceStrToEdit.getBytes());

                    if (instance.paths != null && instance.paths.size() > 0) {
                        for (String media : instance.paths) {
                            FormDownloader fd = new FormDownloader();
                            String mediaUrl = serverUrl + "/" + media;
                            int idx = media.lastIndexOf('/');
                            String mediaName = null;
                            if (idx > -1) {
                                mediaName = media.substring(idx + 1);
                            }
                            String mediaPath = file.getParent() + "/" + mediaName;
                            try {
                                File f = new File(mediaPath);
                                fd.downloadFile(f, mediaUrl);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (instance.files != null && instance.files.size() > 0) {       // Deprecate instance.files
                        for (String media : instance.files) {
                            FormDownloader fd = new FormDownloader();
                            String mediaUrl = serverUrl + "/attachments/" +
                                    formId + "/" + media;
                            String mediaPath = file.getParent() + "/" + media;
                            try {
                                File f = new File(mediaPath);
                                fd.downloadFile(f, mediaUrl);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                os.flush();


            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (Exception e) {
                    }
                }
                if (is != null) {
                    try {
                        // ensure stream is consumed...
                        final long count = 1024L;
                        while (is.skip(count) == count)
                            ;
                    } catch (Exception e) {
                        // no-op
                    }
                    try {
                        is.close();
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
            Log.e(t, e.toString());
            // silently retry unless this is the last attempt,
            // in which case we rethrow the exception.

            FileUtils.deleteAndReport(file);


            throw e;
        }

    }

    /*
     * Get current tasks
     *  set recalculateGeofences to false if the data has not changed and location has not changed (For example the filter has been changed)
     *  set useGeofenceFilter to true if you want to filter out tasks which show distance is less than the distance of the user to the task
     */
    public static void getTasks(
            ArrayList<TaskEntry> tasks,
            boolean all_non_synchronised,
            String sortOrder, String filter,
            boolean serverOnly,
            boolean useGeofenceFilter) {

        // Get cursor
        String[] proj = {
                InstanceColumns._ID,
                InstanceColumns.T_TITLE,
                InstanceColumns.DISPLAY_NAME,
                InstanceColumns.T_TASK_STATUS,
                InstanceColumns.T_TASK_COMMENT,
                InstanceColumns.T_REPEAT,
                InstanceColumns.T_SCHED_START,
                InstanceColumns.T_SCHED_FINISH,
                InstanceColumns.T_ADDRESS,
                InstanceColumns.FORM_PATH,
                InstanceColumns.JR_FORM_ID,
                InstanceColumns.JR_VERSION,
                InstanceColumns.INSTANCE_FILE_PATH,
                InstanceColumns.SCHED_LON,
                InstanceColumns.SCHED_LAT,
                InstanceColumns.ACT_LON,
                InstanceColumns.ACT_LAT,
                InstanceColumns.T_SHOW_DIST,
                InstanceColumns.T_ACT_FINISH,
                InstanceColumns.T_IS_SYNC,
                InstanceColumns.T_ASS_ID,
                InstanceColumns.UUID,
                InstanceColumns.SOURCE,
                InstanceColumns.T_LOCATION_TRIGGER
        };

        String selectClause = null;
        if (all_non_synchronised) {
            selectClause = "(lower(" + InstanceColumns.SOURCE + ") = ?" +
                    " or " + InstanceColumns.SOURCE + " = 'local')" +
                    " and " + InstanceColumns.T_IS_SYNC + " = ? ";
        } else {
            selectClause = "(lower(" + InstanceColumns.SOURCE + ") = ?" +
                    " or " + InstanceColumns.SOURCE + " = 'local')" +
                    " and " + InstanceColumns.T_TASK_STATUS + " != ? ";
        }

        if (serverOnly) {
            selectClause += "and " + InstanceColumns.T_ASS_ID + " is not null ";
        }

        ArrayList<String> selectArgsList = new ArrayList<>();

        selectArgsList.add(Utilities.getSource());
        if (all_non_synchronised) {
            selectArgsList.add(Utilities.STATUS_SYNC_NO);
        } else {
            selectArgsList.add(Utilities.STATUS_T_CLOSED);
        }

        if (filter.trim().length() > 0) {
            selectClause += " and " + InstanceColumns.T_TITLE + " LIKE ?";
            selectArgsList.add("%" + filter + "%");
        }
        String[] selectArgs = new String[selectArgsList.size()];
        selectArgs = selectArgsList.toArray(selectArgs);

        Cursor c = Collect.getInstance().getContentResolver().query(InstanceColumns.CONTENT_URI, proj,
                selectClause, selectArgs, getTaskSortOrderExpr(sortOrder));

        // Set up geofencing

        Location location = null;
        ArrayList<GeofenceEntry> geofences = new ArrayList<GeofenceEntry> ();
        if(useGeofenceFilter) {
            Timber.i("############ use geofence filter");
            location = Collect.getInstance().getLocation();
            if(location == null) {
                Timber.i("############ location is null");
            }
        }



        try {
            c.moveToFirst();
            while (!c.isAfterLast()) {

                TaskEntry entry = new TaskEntry();

                entry.type = "task";
                entry.name = c.getString(c.getColumnIndex(InstanceColumns.T_TITLE));
                entry.displayName = c.getString(c.getColumnIndex(InstanceColumns.DISPLAY_NAME));
                entry.taskStatus = c.getString(c.getColumnIndex(InstanceColumns.T_TASK_STATUS));
                entry.taskComment = c.getString(c.getColumnIndex(InstanceColumns.T_TASK_COMMENT));
                entry.repeat = (c.getInt(c.getColumnIndex(InstanceColumns.T_REPEAT)) > 0);
                entry.taskStart = c.getLong(c.getColumnIndex(InstanceColumns.T_SCHED_START));
                entry.taskFinish = c.getLong(c.getColumnIndex(InstanceColumns.T_SCHED_FINISH));
                entry.taskAddress = c.getString(c.getColumnIndex(InstanceColumns.T_ADDRESS));
                entry.taskForm = c.getString(c.getColumnIndex(InstanceColumns.FORM_PATH));
                entry.jrFormId = c.getString(c.getColumnIndex(InstanceColumns.JR_FORM_ID));
                entry.formVersion = c.getInt(c.getColumnIndex(InstanceColumns.JR_VERSION));
                entry.instancePath = c.getString(c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
                entry.id = c.getLong(c.getColumnIndex(InstanceColumns._ID));
                entry.schedLon = c.getDouble(c.getColumnIndex(InstanceColumns.SCHED_LON));
                entry.schedLat = c.getDouble(c.getColumnIndex(InstanceColumns.SCHED_LAT));
                entry.actLon = c.getDouble(c.getColumnIndex(InstanceColumns.ACT_LON));
                entry.actLat = c.getDouble(c.getColumnIndex(InstanceColumns.ACT_LAT));
                entry.showDist = c.getInt(c.getColumnIndex(InstanceColumns.T_SHOW_DIST));
                entry.actFinish = c.getLong(c.getColumnIndex(InstanceColumns.T_ACT_FINISH));
                entry.isSynced = c.getString(c.getColumnIndex(InstanceColumns.T_IS_SYNC));
                entry.assId = c.getLong(c.getColumnIndex(InstanceColumns.T_ASS_ID));
                entry.uuid = c.getString(c.getColumnIndex(InstanceColumns.UUID));
                entry.source = c.getString(c.getColumnIndex(InstanceColumns.SOURCE));
                entry.locationTrigger = c.getString(c.getColumnIndex(InstanceColumns.T_LOCATION_TRIGGER));

                if(useGeofenceFilter && location != null) {
                    if(entry.showDist > 0 && entry.schedLat != 0.0 && entry.schedLon != 0.0) {

                        Location taskLocation = new Location("");
                        taskLocation.setLatitude(entry.schedLat);
                        taskLocation.setLongitude(entry.schedLon);

                        float distance = location.distanceTo(taskLocation);

                        /*
                         * Do a quick check on latitude before the slower check on longitude
                         */
                        GeofenceEntry gfe = new GeofenceEntry(entry.showDist, taskLocation);
                        double yDistance = abs(location.getLatitude() - gfe.location.getLatitude()) * 111111.1;     // lattitude difference in meters
                        if(yDistance < entry.showDist) {            // rough check
                            if (distance < entry.showDist) {        // detailed check
                                tasks.add(entry);
                                gfe.in = true;
                            }
                        }

                        geofences.add(gfe);
                    } else {
                        tasks.add(entry);
                    }
                } else {
                    tasks.add(entry);
                }

                c.moveToNext();
            }

            Collect.getInstance().setGeofences(geofences);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception e) {
                }
            }
        }

    }

    /*
     * Mark closed any tasks with the matching status
     * Only mark closed if the task status has been successfully synchronised with the server or
     * it is a local task
     */
    public static int markClosedTasksWithStatus(String status) {

        Uri dbUri = InstanceColumns.CONTENT_URI;

        String selectClause = InstanceColumns.T_TASK_STATUS + " = ? and "
                + InstanceColumns.SOURCE + " = ? and "
                + "(" + InstanceColumns.T_IS_SYNC + " = ? or "
                + InstanceColumns.T_ASS_ID + " is null)";           // Local task

        String[] selectArgs = {"", "", ""};
        selectArgs[0] = status;
        selectArgs[1] = Utilities.getSource();
        selectArgs[2] = Utilities.STATUS_SYNC_YES;

        ContentValues cv = new ContentValues();
        cv.put(T_TASK_STATUS, Utilities.STATUS_T_CLOSED);
        return Collect.getInstance().getContentResolver().update(dbUri, cv, selectClause, selectArgs);
    }

    /*
     * Reject any tasks with that are not in the array of assignment identifiers
     * This can be used to remove tasks that have been removed from the server
     * (Do not reject repeating tasks?????? is this comment valid)
     */
    public static int rejectObsoleteTasks(List<TaskAssignment> assignmentsToKeep) {

        Uri dbUri = InstanceColumns.CONTENT_URI;
        int nIds = 0;
        if (assignmentsToKeep != null) {
            nIds = assignmentsToKeep.size();
        }

        String[] selectArgs = new String[nIds + 1];
        selectArgs[0] = Utilities.getSource();

        StringBuffer selectClause = new StringBuffer(InstanceColumns.T_ASS_ID + " is not null and " + InstanceColumns.SOURCE + " = ?");
        selectClause.append(" and " + InstanceColumns.DELETED_DATE + " is null");

        if (nIds > 0) {
            selectClause.append(" and " + InstanceColumns.T_ASS_ID + " not in (");
            for (int i = 0; i < nIds; i++) {
                if (i > 0) {
                    selectClause.append(",");
                }
                selectClause.append("?");
                selectArgs[i + 1] = String.valueOf(assignmentsToKeep.get(i).assignment.assignment_id);
            }
            selectClause.append(")");
        }

        ContentValues cv = new ContentValues();
        cv.put(T_TASK_STATUS, Utilities.STATUS_T_REJECTED);

        return Collect.getInstance().getContentResolver().update(dbUri, cv, selectClause.toString(), selectArgs);
    }

    /*
     * Mark the task as synchronised
     */
    public static void setTaskSynchronized(Long id) {

        Uri taskUri = Uri.withAppendedPath(InstanceColumns.CONTENT_URI, id.toString());

        ContentValues values = new ContentValues();
        values.put(InstanceColumns.T_IS_SYNC, STATUS_SYNC_YES);

        Collect.getInstance().getContentResolver().update(taskUri, values, null, null);

    }

    /*
     * Mark the status of a task
     */
    public static void setStatusForTask(Long id, String status, String reason) {

        Uri taskUri = Uri.withAppendedPath(InstanceColumns.CONTENT_URI, id.toString());

        ContentValues values = new ContentValues();
        values.put(InstanceColumns.T_TASK_STATUS, status);
        values.put(InstanceColumns.T_TASK_COMMENT, reason);
        values.put(InstanceColumns.T_IS_SYNC, Utilities.STATUS_SYNC_NO);

        Collect.getInstance().getContentResolver().update(taskUri, values, null, null);

    }

    /*
     * Delete rejected tasks (that have not already been deleted)
     */
    public static void deleteRejectedTasks() {

        Uri dbUri = InstanceColumns.CONTENT_URI;

        String where = InstanceColumns.T_TASK_STATUS + " = 'rejected' and "
                + InstanceColumns.SOURCE + " = ? and "
                + InstanceColumns.DELETED_DATE + " is null";

        String[] whereArgs = {""};
        whereArgs[0] = Utilities.getSource();

        InstanceProvider ip = new InstanceProvider();

        Cursor c = null;
        String status = null;
        try {
            c = Collect.getInstance().getContentResolver().query(dbUri, null, where, whereArgs, null);
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                status = c.getString(c.getColumnIndex(InstanceColumns.STATUS));
                do {
                    String instanceFile = c.getString(
                            c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
                    File instanceDir = (new File(instanceFile)).getParentFile();
                    ip.deleteAllFilesInDirectory(instanceDir);

                } while (c.moveToNext());
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        // Set the deleted date in the instance table
        ContentValues cv = new ContentValues();
        cv.put(InstanceColumns.DELETED_DATE, System.currentTimeMillis());
        Collect.getInstance().getContentResolver().update(dbUri, cv, where, whereArgs);

    }

    /*
     * Clean out history from the instances table that is older than 6 months
     */
    public static void cleanHistory() {

        Uri dbUri = InstanceColumns.CONTENT_URI;

        String selectClause = InstanceColumns.DELETED_DATE + " is not null and "
                +"datetime(" + InstanceColumns.DELETED_DATE
                + " / 1000, 'unixepoch') <  datetime('now','-6 months')";

        Collect.getInstance().getContentResolver().delete(dbUri, selectClause, null);
    }

    /*
    * Set the status for the provided assignment id
    */
    public static void setStatusForAssignment(long assId, String status) {

        Uri dbUri = InstanceColumns.CONTENT_URI;

        String selectClause = InstanceColumns.T_ASS_ID + " = " + assId + " and "
                + InstanceColumns.SOURCE + " = ?";

        String[] selectArgs = {""};
        selectArgs[0] = Utilities.getSource();

        ContentValues values = new ContentValues();
        values.put(InstanceColumns.T_TASK_STATUS, status);

        Collect.getInstance().getContentResolver().update(dbUri, values, selectClause, selectArgs);

    }

    /*
     * Update parameters for the provided assignment id
     */
    public static void updateParametersForAssignment(long assId, TaskAssignment ta) {

        Uri dbUri = InstanceColumns.CONTENT_URI;

        String selectClause = InstanceColumns.T_ASS_ID + " = " + assId + " and "
                + InstanceColumns.SOURCE + " = ?";


        String[] selectArgs = {""};
        selectArgs[0] = Utilities.getSource();

        ContentValues values = new ContentValues();
        if (ta.task.scheduled_at != null) {
            values.put(InstanceColumns.T_SCHED_START, ta.task.scheduled_at.getTime());
        }
        if (ta.task.scheduled_finish != null) {
            values.put(InstanceColumns.T_SCHED_FINISH, ta.task.scheduled_finish.getTime());
        }
        values.put(InstanceColumns.T_LOCATION_TRIGGER, ta.task.location_trigger);
        values.put(InstanceColumns.T_SHOW_DIST, ta.task.show_dist);
        values.put(InstanceColumns.T_HIDE, (ta.task.show_dist > 0) ? 1 : 0);

        values.put(InstanceColumns.T_TITLE, ta.task.title);
        values.put(InstanceColumns.T_ADDRESS, ta.task.address);
        values.put(InstanceColumns.T_LOCATION_TRIGGER, ta.task.location_trigger);

        // Add task geofence values
        values.put(InstanceColumns.T_SHOW_DIST, ta.task.show_dist);
        values.put(InstanceColumns.T_HIDE, (ta.task.show_dist > 0) ? 1 : 0);

        // Update target location
        if (ta.location != null && ta.location.geometry != null && ta.location.geometry.coordinates != null && ta.location.geometry.coordinates.length >= 1) {
            // Set the location of the task to the first coordinate pair
            String firstCoord = ta.location.geometry.coordinates[0];
            String [] fc = firstCoord.split(" ");
            if(fc.length > 1) {
                values.put(InstanceColumns.SCHED_LON, fc[0]);
                values.put(InstanceColumns.SCHED_LAT, fc[1]);
            }
            StringBuilder builder = new StringBuilder();
            for(String coord : ta.location.geometry.coordinates) {
                builder.append(coord);
                builder.append(",");
            }
            values.put(InstanceColumns.GEOMETRY, builder.toString());
            values.put(InstanceColumns.GEOMETRY_TYPE, ta.location.geometry.type);
        }

        Collect.getInstance().getContentResolver().update(dbUri, values, selectClause, selectArgs);

    }

    /*
     * Return true if the current task status allows it to be rejected
     */
    public static boolean canReject(String currentStatus) {
        boolean valid = false;
        if (currentStatus != null && currentStatus.equals(Utilities.STATUS_T_ACCEPTED)) {
            valid = true;
        } else  if (currentStatus != null && currentStatus.equals(Utilities.STATUS_T_NEW)) {
            valid = true;
        }
        return valid;
    }

    /*
     * Return true if the current task status allows it to be restored
     */
    public static boolean canRestore(String currentStatus) {
        boolean valid = false;
        if (currentStatus != null && currentStatus.equals(Utilities.STATUS_T_REJECTED)) {
            valid = true;
        }
        return valid;
    }

    /*
     * Return true if the current task status allows it to be completed
     */
    public static boolean canComplete(String currentStatus) {

        boolean valid = false;
        if (currentStatus != null && currentStatus.equals(STATUS_T_ACCEPTED)) {
            valid = true;
        }

        return valid;
    }

    /*
     * Return true if the current task status allows it to be accepted
     */
    public static boolean canAccept(String currentStatus) {
        boolean canAccept = false;
        if (currentStatus != null && currentStatus.equals(STATUS_T_NEW)) {
            canAccept = true;
        }

        return canAccept;
    }

    /*
     * Return true if the current task is submitted
     */
    public static boolean isSubmitted(String currentStatus) {
        boolean submitted = false;
        if (currentStatus != null && currentStatus.equals(STATUS_T_SUBMITTED)) {
            submitted = true;
        }

        return submitted;
    }

    /*
     * Return true if the current task is selfAssigned
     */
    public static boolean isSelfAssigned(String currentStatus) {
        boolean selfAssigned = false;
        if (currentStatus != null && currentStatus.equals(STATUS_T_NEW)) {
            selfAssigned = true;
        }

        return selfAssigned;
    }


    /*
     * Copy instance files to a new location
     */
    public static void copyInstanceFiles(String fromInstancePath, String toInstancePath) {
        String fromFolderPath = fromInstancePath.substring(0, fromInstancePath.lastIndexOf('/'));   // InstancePaths are for the XML files
        String toFolderPath = toInstancePath.substring(0, toInstancePath.lastIndexOf('/'));   // InstancePaths are for the XML files

        File fromInstanceFolder = new File(fromFolderPath);
        File toInstanceFolder = new File(toFolderPath);
        toInstanceFolder.mkdir();
        File[] instanceFiles = fromInstanceFolder.listFiles();
        if (instanceFiles != null && instanceFiles.length > 0) {
            for (File f : instanceFiles) {
                try {
                    String fileName = f.getName();
                    if (fileName.endsWith("xml")) {
                        // Copy xml file to new instance path
                        File toFile = new File(toInstancePath);
                        org.apache.commons.io.FileUtils.copyFile(f, toFile, false);
                    } else {
                        // Copy attachments to new folder
                        org.apache.commons.io.FileUtils.copyFileToDirectory(f, toInstanceFolder, false);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
     * Get a time to display for the task, either the planned start time or the actual finish time
     */
    public static String getTaskTime(String status, long actFinish, long taskStart) {

        String s = null;
        long theTime = 0;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        if (status.equals(Utilities.STATUS_T_COMPLETE) || status.equals(Utilities.STATUS_T_SUBMITTED)) {
            theTime = actFinish;
        } else if (taskStart > 0) {
            theTime = taskStart;
        } else {
            theTime = actFinish;
        }

        df.setTimeZone(TimeZone.getDefault());
        s = df.format(theTime);

        return s;
    }

    /*
     * Convert a time long format to a string
     */
    public static String getTime(long t) {

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        String s = "";
        if(t > 0) {

            df.setTimeZone(TimeZone.getDefault());
            s = df.format(t);
        }

        return s;
    }

    /*
     * Translate a message
     */
    public static String translateMsg(Exception e, String in) {
        String msg = null;
        if(e != null) {
            msg = e.getMessage();
        } else {
            msg = in;
        }
        String out = null;
        if (msg != null) {
            if (msg.contains("Unauthorized")) {
                out = Collect.getInstance().getString(R.string.smap_unauth);
            } else if(msg.contains("Unable to resolve host")) {
                out = msg + ". " + Collect.getInstance().getString(R.string.no_connection);
            } else {
                if(e != null) {
                    out = e.getLocalizedMessage();
                } else {
                    out = msg;
                }
            }
        }
        if(out == null) {
            out = Collect.getInstance().getString(R.string.smap_unknown_error);
        }
        return out;
    }

    public static StringBuilder getUploadMessage(HashMap<String, String> result) {
        StringBuilder message = new StringBuilder();
        message
                .append(Collect.getInstance().getString(R.string.smap_refresh_finished))
                .append(" :: \n\n");

        String[] selectionArgs = null;
        StringBuilder selection = new StringBuilder();
        ArrayList<String> instanceKeys = new ArrayList<String>();

        if (result != null) {   // smap - ignore null results

            for (String key : result.keySet()) {
                try {
                    Integer.parseInt(key);
                    instanceKeys.add(key);
                } catch (Exception e) {
                    message.append(key).append(" - ").append(result.get(key)).append("\n\n");
                }
            }

            selectionArgs = new String[instanceKeys.size()];
            int i = 0;
            for (String key : instanceKeys) {
                if (i > 0) {
                    selection.append(" or ");
                }
                selection.append(InstanceColumns._ID + "=?");
                selectionArgs[i++] = key;
            }
        }

        if (instanceKeys.size() > 0) {
            Cursor results = null;
            try {
                results = new InstancesDao().getInstancesCursor(selection.toString(), selectionArgs);
                if (results.getCount() > 0) {
                    results.moveToPosition(-1);
                    while (results.moveToNext()) {
                        String name = results.getString(results
                                .getColumnIndex(InstanceColumns.DISPLAY_NAME));
                        String id = results.getString(results
                                .getColumnIndex(InstanceColumns._ID));
                        message
                                .append(name)
                                .append(" - ")
                                .append(result.get(id))
                                .append("\n\n");
                    }
                }
            } finally {
                if (results != null) {
                    results.close();
                }
            }
        }


        return message;
    }

    private static String getTaskSortOrderExpr(String sortOrder) {

        String sortOrderExpr = InstanceColumns.T_SCHED_START + " ASC, " + InstanceColumns.T_TITLE + " COLLATE NOCASE ASC";

        if (sortOrder.equals("BY_NAME_ASC")) {
            sortOrderExpr = InstanceColumns.T_TITLE + " COLLATE NOCASE ASC, " + InstanceColumns.T_SCHED_START + " ASC";
        } else if (sortOrder.equals("BY_NAME_DESC")) {
            sortOrderExpr = InstanceColumns.T_TITLE + " COLLATE NOCASE DESC, " + InstanceColumns.T_SCHED_START + " DESC";
        } else if (sortOrder.equals("BY_DATE_ASC")) {
            sortOrderExpr = InstanceColumns.T_SCHED_START + " ASC, " + InstanceColumns.T_TITLE + " ASC";
        } else if (sortOrder.equals("BY_DATE_DESC")) {
            sortOrderExpr = InstanceColumns.T_SCHED_START + " DESC, " + InstanceColumns.T_TITLE + " DESC";
        } else if (sortOrder.equals("BY_STATUS_ASC")) {
            sortOrderExpr = T_TASK_STATUS + " ASC, " + InstanceColumns.T_TITLE + " ASC";
        } else if (sortOrder.equals("BY_STATUS_DESC")) {
            sortOrderExpr = InstanceColumns.T_TASK_STATUS + " DESC, " + InstanceColumns.T_TITLE + " DESC";
        }
        return sortOrderExpr;
    }

}
