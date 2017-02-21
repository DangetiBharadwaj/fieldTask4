/*
 * Copyright (C) 2009 University of Washington
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

package org.odk.collect.android.tasks;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.utilities.WebUtils;
import org.opendatakit.httpclientandroidlib.Header;
import org.opendatakit.httpclientandroidlib.HttpResponse;
import org.opendatakit.httpclientandroidlib.HttpStatus;
import org.opendatakit.httpclientandroidlib.client.ClientProtocolException;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.methods.HttpHead;
import org.opendatakit.httpclientandroidlib.client.methods.HttpPost;
import org.opendatakit.httpclientandroidlib.conn.ConnectTimeoutException;
import org.opendatakit.httpclientandroidlib.conn.HttpHostConnectException;
import org.opendatakit.httpclientandroidlib.entity.ContentType;
import org.opendatakit.httpclientandroidlib.entity.mime.MultipartEntityBuilder;
import org.opendatakit.httpclientandroidlib.entity.mime.content.FileBody;
import org.opendatakit.httpclientandroidlib.entity.mime.content.StringBody;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Background task for uploading completed forms.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class InstanceUploaderTask extends AsyncTask<Long, Integer, InstanceUploaderTask.Outcome> {

    private static final String t = "InstanceUploaderTask";
    // it can take up to 27 seconds to spin up Aggregate
    private static final int CONNECTION_TIMEOUT = 60000;
    private static final String fail = "Error: ";
    private static final String URL_PATH_SEP = "/";

    private InstanceUploaderListener mStateListener;

    public static class Outcome {
        public Uri mAuthRequestingServer = null;
        public HashMap<String, String> mResults = new HashMap<String, String>();
    }

    /**
     * Uploads to urlString the submission identified by id with filepath of instance
     *
     * @param urlString    destination URL
     * @param id
     * @param instanceFilePath
     * @param toUpdate     - Instance URL for recording status update.
     * @param localContext - context (e.g., credentials, cookies) for client connection
     * @param uriRemap     - mapping of Uris to avoid redirects on subsequent invocations
     * @param outcome
     * @return false if credentials are required and we should terminate immediately.
     */
    private boolean uploadOneSubmission(String urlString, String id, String instanceFilePath,
                            Uri toUpdate,
                            HttpContext localContext,
                            Map<Uri, Uri> uriRemap,
                            Outcome outcome,
                            String status,              // smap
                            String location_trigger,    // smap
                            String survey_notes) {		// smap add status

        Collect.getInstance().getActivityLogger().logAction(this, urlString, instanceFilePath);

        File instanceFile = new File(instanceFilePath);
        ContentValues cv = new ContentValues();
        Uri u = Uri.parse(urlString);
        HttpClient httpclient = WebUtils.createHttpClient(CONNECTION_TIMEOUT);

        boolean openRosaServer = false;
        if (uriRemap.containsKey(u)) {
            // we already issued a head request and got a response,
            // so we know the proper URL to send the submission to
            // and the proper scheme. We also know that it was an
            // OpenRosa compliant server.
            openRosaServer = true;
            u = uriRemap.get(u);

            // if https then enable preemptive basic auth...
            //if ( u.getScheme().equals("https") ) {	smap
            //	WebUtils.enablePreemptiveBasicAuth(localContext, u.getHost());
            //}

            Log.i(t, "Using Uri remap for submission " + id + ". Now: " + u.toString());
        } else {

            // if https then enable preemptive basic auth...
            //if ( u.getScheme() != null && u.getScheme().equals("https") ) { smap
            //	WebUtils.enablePreemptiveBasicAuth(localContext, u.getHost());
            //}

            // we need to issue a head request
            HttpHead httpHead = WebUtils.createOpenRosaHttpHead(u);

            // prepare response
            HttpResponse response = null;
            try {
                Log.i(t, "Issuing HEAD request for " + id + " to: " + u.toString());

                response = httpclient.execute(httpHead, localContext);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                    // clear the cookies -- should not be necessary?
                    Collect.getInstance().getCookieStore().clear();

                    WebUtils.discardEntityBytes(response);
                    // we need authentication, so stop and return what we've
                    // done so far.
               	    /*
                     * Smap Start
                     *   just fail the request, the user will need to update userid and password in the parameters
                     *   Smap does not at this stage support submissionURL's per survey each with a different user id and password
                     *   The thinking is that this may be too complex and this type of function is better handled by subscribers
                      */
                    //mAuthRequestingServer = u;
                    //return false;
                    outcome.mResults.put(id, fail
                    			+ "Authentication failure.  You will need to fix the username, password or URL in the settings screen.  ");
                    //cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED); smap
                    Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                    return true;
                    // Smap end
                } else if (statusCode == 204) {
                    Header[] locations = response.getHeaders("Location");
                    WebUtils.discardEntityBytes(response);
                    if (locations != null && locations.length == 1) {
                        try {
                            Uri uNew = Uri.parse(
                                    URLDecoder.decode(locations[0].getValue(), "utf-8"));
                            if (u.getHost().equalsIgnoreCase(uNew.getHost())) {
                                openRosaServer = true;
                                // trust the server to tell us a new location
                                // ... and possibly to use https instead.
                                uriRemap.put(u, uNew);
                                u = uNew;
                                // Start Smap
                                String deviceId = new PropertyManager(Collect.getInstance().getApplicationContext())
                                        .getSingularProperty(PropertyManager.OR_DEVICE_ID_PROPERTY);
                                u = Uri.parse(u.toString() + "?deviceID=" + URLEncoder.encode(deviceId, "UTF-8"));
                                // End Smap
                            } else {
                                // Don't follow a redirection attempt to a different host.
                                // We can't tell if this is a spoof or not.
                            	outcome.mResults.put(
                                    id,
                                    fail
                                            + "Unexpected redirection attempt to a different host: "
                                            + uNew.toString());
                                //cv.put(InstanceColumns.STATUS,
                                //    InstanceProviderAPI.STATUS_SUBMISSION_FAILED);  smap
                                Collect.getInstance().getContentResolver()
                                        .update(toUpdate, cv, null, null);
                                return true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            outcome.mResults.put(id, fail + urlString + " " + e.toString());
                            //cv.put(InstanceColumns.STATUS,
                            //    InstanceProviderAPI.STATUS_SUBMISSION_FAILED); smap
                            Collect.getInstance().getContentResolver()
                                    .update(toUpdate, cv, null, null);
                            return true;
                        }
                    }
                } else {
                    // may be a server that does not handle
                    WebUtils.discardEntityBytes(response);

                    Log.w(t, "Status code on Head request: " + statusCode);
                    if (statusCode >= HttpStatus.SC_OK
                            && statusCode < HttpStatus.SC_MULTIPLE_CHOICES) {
                        outcome.mResults.put(
                                id,
                                fail
                                        + "Invalid status code on Head request.  If you have a "
                                        + "web proxy, you may need to login to your network. ");
                        // cv.put(InstanceColumns.STATUS,
                        //         InstanceProviderAPI.STATUS_SUBMISSION_FAILED);  // smap
                        Collect.getInstance().getContentResolver()
                                .update(toUpdate, cv, null, null);
                        return true;
                    }
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Log.e(t, e.toString());
                outcome.mResults.put(id, fail + "Client Protocol Exception");
               // cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED); smap
                Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (ConnectTimeoutException e) {
                e.printStackTrace();
                Log.e(t, e.toString());
                outcome.mResults.put(id, fail + "Connection Timeout");
                //cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED); smap
                Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (UnknownHostException e) {
                e.printStackTrace();
                Log.e(t, e.toString());
                outcome.mResults.put(id, fail + e.toString() + " :: Network Connection Failed");
                //cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED); smap
                Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                Log.e(t, e.toString());
                outcome.mResults.put(id, fail + "Connection Timeout");
                //cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);  smap
                Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (HttpHostConnectException e) {
                e.printStackTrace();
                Log.e(t, e.toString());
                outcome.mResults.put(id, fail + "Network Connection Refused");
                //cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED); smap
                Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (IllegalArgumentException e) {	// smap
                e.printStackTrace();
                Log.e(t, e.getMessage());
                outcome.mResults.put(id,
                    fail + "invalid url: " + urlString + " :: details: " + e.toString());
                // cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED); smap
                Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(t, e.toString());
                String msg = e.getMessage();
                if (msg == null) {
                    msg = e.toString();
                }
                outcome.mResults.put(id, fail + "Generic Exception: " + msg);
                // cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED); smap
                Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            }
        }

        // At this point, we may have updated the uri to use https.
        // This occurs only if the Location header keeps the host name
        // the same. If it specifies a different host name, we error
        // out.
        //
        // And we may have set authentication cookies in our
        // cookiestore (referenced by localContext) that will enable
        // authenticated publication to the server.
        //
        // get instance file

        // Under normal operations, we upload the instanceFile to
        // the server.  However, during the save, there is a failure
        // window that may mark the submission as complete but leave
        // the file-to-be-uploaded with the name "submission.xml" and
        // the plaintext submission files on disk.  In this case,
        // upload the submission.xml and all the files in the directory.
        // This means the plaintext files and the encrypted files
        // will be sent to the server and the server will have to
        // figure out what to do with them.
        File submissionFile = new File(instanceFile.getParentFile(), "submission.xml");
        if (submissionFile.exists()) {
            Log.w(t,
                    "submission.xml will be uploaded instead of " + instanceFile.getAbsolutePath());
        } else {
            submissionFile = instanceFile;
        }

        if (!instanceFile.exists() && !submissionFile.exists()) {
       	    outcome.mResults.put(id, fail + "instance XML file does not exist!");
            // cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED); // smap
            Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
            return true;
        }

        // find all files in parent directory
        File[] allFiles = instanceFile.getParentFile().listFiles();

        // add media files
        List<File> files = new ArrayList<File>();
        for (File f : allFiles) {
            String fileName = f.getName();

            int dotIndex = fileName.lastIndexOf(".");
            String extension = "";
            if (dotIndex != -1) {
                extension = fileName.substring(dotIndex + 1);
            }

            if (fileName.startsWith(".")) {
                // ignore invisible files
                continue;
            }
            if (fileName.equals(instanceFile.getName())) {
                continue; // the xml file has already been added
            } else if (fileName.equals(submissionFile.getName())) {
                continue; // the xml file has already been added
            } else if (openRosaServer) {
                files.add(f);
            } else if (extension.equals("jpg")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("3gpp")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("3gp")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("mp4")) { // legacy 0.9x
                files.add(f);
            } else if (extension.equals("osm")) { // legacy 0.9x
                files.add(f);
            } else {
                Log.w(t, "unrecognized file type " + f.getName());
            }
        }

        boolean first = true;
        int j = 0;
        int lastJ;
        while (j < files.size() || first) {
            lastJ = j;
            first = false;

            HttpPost httppost = WebUtils.createOpenRosaHttpPost(u);
            httppost.setHeader("form_status", status);						// smap add form_status header
            Log.i("uploadOneSubmission", "Post to: " + u.toString());

            MimeTypeMap m = MimeTypeMap.getSingleton();

            long byteCount = 0L;

            // mime post
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            // add the submission file first...
            FileBody fb = new FileBody(submissionFile, ContentType.TEXT_XML);
            builder.addPart("xml_submission_file", fb);
            Log.i(t, "added xml_submission_file: " + submissionFile.getName());
            byteCount += submissionFile.length();

            for (; j < files.size(); j++) {
                File f = files.get(j);
                String fileName = f.getName();
                int idx = fileName.lastIndexOf(".");
                String extension = "";
                if (idx != -1) {
                    extension = fileName.substring(idx + 1);
                }
                String contentType = m.getMimeTypeFromExtension(extension);

                // we will be processing every one of these, so
                // we only need to deal with the content type determination...
                if (extension.equals("xml")) {
                    fb = new FileBody(f, ContentType.TEXT_XML);
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added xml file " + f.getName());
                } else if (extension.equals("3gpp")) {
                    fb = new FileBody(f, ContentType.create("audio/3gpp"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added audio file " + f.getName());
                } else if (extension.equals("3gp")) {
                    fb = new FileBody(f, ContentType.create("video/3gpp"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added video file " + f.getName());
                } else if (extension.equals("avi")) {
                    fb = new FileBody(f, ContentType.create("video/avi"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added video file " + f.getName());
                } else if (extension.equals("amr")) {
                    fb = new FileBody(f, ContentType.create("audio/amr"));
                    builder.addPart(f.getName(), fb);
                    Log.i(t, "added audio file " + f.getName());
                } else if (extension.equals("csv")) {
                    fb = new FileBody(f, ContentType.create("text/csv"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added csv file " + f.getName());
                } else if (extension.equals("jpg")) {
                    fb = new FileBody(f, ContentType.create("image/jpeg"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added image file " + f.getName());
                } else if (extension.equals("mp3")) {
                    fb = new FileBody(f, ContentType.create("audio/mp3"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added audio file " + f.getName());
                } else if (extension.equals("mp4")) {
                    fb = new FileBody(f, ContentType.create("video/mp4"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added video file " + f.getName());
                } else if (extension.equals("oga")) {
                    fb = new FileBody(f, ContentType.create("audio/ogg"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added audio file " + f.getName());
                } else if (extension.equals("ogg")) {
                    fb = new FileBody(f, ContentType.create("audio/ogg"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added video file " + f.getName());
                } else if (extension.equals("ogv")) {
                    fb = new FileBody(f, ContentType.create("video/ogg"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added video file " + f.getName());
                } else if (extension.equals("wav")) {
                    fb = new FileBody(f, ContentType.create("audio/wav"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added audio file " + f.getName());
                } else if (extension.equals("webm")) {
                    fb = new FileBody(f, ContentType.create("video/webm"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added video file " + f.getName());
                } else if (extension.equals("xls")) {
                    fb = new FileBody(f, ContentType.create("application/vnd.ms-excel"));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t, "added xls file " + f.getName());
                } else if (contentType != null) {
                    fb = new FileBody(f, ContentType.create(contentType));
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.i(t,
                            "added recognized filetype (" + contentType + ") " + f.getName());
                } else {
                    contentType = "application/octet-stream";
                    fb = new FileBody(f, ContentType.APPLICATION_OCTET_STREAM);
                    builder.addPart(f.getName(), fb);
                    byteCount += f.length();
                    Log.w(t, "added unrecognized file (" + contentType + ") " + f.getName());
                }

                // we've added at least one attachment to the request...
                if (j + 1 < files.size()) {
                    if ((j - lastJ + 1 > 100) || (byteCount + files.get(j + 1).length()
                            > 10000000L)) {
                        // the next file would exceed the 10MB threshold...
                        Log.i(t, "Extremely long post is being split into multiple posts");
                        try {
                            StringBody sb = new StringBody("yes",
                                    ContentType.TEXT_PLAIN.withCharset(Charset.forName("UTF-8")));
                            builder.addPart("*isIncomplete*", sb);
                        } catch (Exception e) {
                            e.printStackTrace(); // never happens...
                        }
                        ++j; // advance over the last attachment added...
                        break;
                    }
                }
            }

            // Start Smap - Add the location trigger and comments if they exist
            if(location_trigger != null) {
                try {
                    StringBody sb = new StringBody(location_trigger, ContentType.TEXT_PLAIN.withCharset(Charset.forName("UTF-8")));
                    builder.addPart("location_trigger", sb);
                } catch (Exception e) {
                    e.printStackTrace(); // never happens...
                }
            }
            if(survey_notes != null) {
                try {
                    StringBody sb = new StringBody(survey_notes, ContentType.TEXT_PLAIN.withCharset(Charset.forName("UTF-8")));
                    builder.addPart("survey_notes", sb);
                } catch (Exception e) {
                    e.printStackTrace(); // never happens...
                }
            }
            // End Smap

            httppost.setEntity(builder.build());

            // prepare response and return uploaded
            HttpResponse response = null;
            try {
                Log.i(t, "Issuing POST request for " + id + " to: " + u.toString());
                response = httpclient.execute(httppost, localContext);
                int responseCode = response.getStatusLine().getStatusCode();
                WebUtils.discardEntityBytes(response);

                Log.i(t, "Response code:" + responseCode);
                // verify that the response was a 201 or 202.
                // If it wasn't, the submission has failed.
                if (responseCode != HttpStatus.SC_CREATED
                        && responseCode != HttpStatus.SC_ACCEPTED) {
                    if (responseCode == HttpStatus.SC_OK) {
                        outcome.mResults.put(id, fail + "Network login failure? Again?");
                    } else if (responseCode == HttpStatus.SC_UNAUTHORIZED) {
                        // clear the cookies -- should not be necessary?
                        Collect.getInstance().getCookieStore().clear();
                        outcome.mResults.put(id, fail + response.getStatusLine().getReasonPhrase()
                                + " (" + responseCode + ") at " + urlString);
                    } else {
                        outcome.mResults.put(id, fail + response.getStatusLine().getReasonPhrase()
                                + " (" + responseCode + ") at " + urlString);
                    }
                    //cv.put(InstanceColumns.STATUS,
                    //    InstanceProviderAPI.STATUS_SUBMISSION_FAILED); smap
                    Collect.getInstance().getContentResolver()
                            .update(toUpdate, cv, null, null);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(t, e.toString());
                String msg = e.getMessage();
                if (msg == null) {
                    msg = e.toString();
                }
                outcome.mResults.put(id, fail + "Generic Exception: " + msg);
                //cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMISSION_FAILED);    // smap
                Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
                return true;
            }
        }

        // if it got here, it must have worked
        outcome.mResults.put(id, Collect.getInstance().getString(R.string.success));
        cv.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_SUBMITTED);
        cv.put(InstanceColumns.T_TASK_STATUS, InstanceProviderAPI.STATUS_SUBMITTED);     // smap
        Collect.getInstance().getContentResolver().update(toUpdate, cv, null, null);
        return true;
    }

    // TODO: This method is like 350 lines long, down from 400.
    // still. ridiculous. make it smaller.
    public Outcome doInBackground(Long... values) {		// smap make public
    	Outcome outcome = new Outcome();

        // Start smap
        int maxFiles = 999;
        int numberToSend = values == null ? 0 : values.length > maxFiles ? maxFiles : values.length;
        StringBuffer selectionBuf = new StringBuffer(InstanceColumns._ID + " IN (");
        String[] selectionArgs = new String[numberToSend];
        if(values != null) {
            for (int i = 0; i < numberToSend; i++) {
                if(i > 0) {
                    selectionBuf.append(",");
                }
                selectionBuf.append("?");
                selectionArgs[i] = values[i].toString();
            }
        }
        selectionBuf.append(")");
        String selection = selectionBuf.toString();
        Log.i(t, "Getting instances "  + selection);
        // end smap

        String deviceId = new PropertyManager(Collect.getInstance().getApplicationContext())
                .getSingularProperty(PropertyManager.OR_DEVICE_ID_PROPERTY);

        // get shared HttpContext so that authentication and cookies are retained.
        HttpContext localContext = Collect.getInstance().getHttpContext();

        Map<Uri, Uri> uriRemap = new HashMap<Uri, Uri>();

        Cursor c = null;
        try {
            c = Collect.getInstance().getContentResolver()
                    .query(InstanceColumns.CONTENT_URI, null, selection, selectionArgs, null);

            if (c.getCount() > 0) {
                c.moveToPosition(-1);
                while (c.moveToNext()) {
                    if (isCancelled()) {
                        return outcome;
                    }
                    publishProgress(c.getPosition() + 1, c.getCount());
                    String instance = c.getString(
                            c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
                    String id = c.getString(c.getColumnIndex(InstanceColumns._ID));
                    Uri toUpdate = Uri.withAppendedPath(InstanceColumns.CONTENT_URI, id);

	                int subIdx = c.getColumnIndex(InstanceColumns.SUBMISSION_URI);
	                String urlString = c.isNull(subIdx) ? null : c.getString(subIdx);
	                if (urlString == null) {
	                    SharedPreferences settings =
	                        PreferenceManager.getDefaultSharedPreferences(Collect.getInstance());
	                    urlString = settings.getString(PreferencesActivity.KEY_SERVER_URL,
	                    				Collect.getInstance().getString(R.string.default_server_url));
	                    if ( urlString.charAt(urlString.length()-1) == '/') {
	                    	urlString = urlString.substring(0, urlString.length()-1);
	                    }
	                    // NOTE: /submission must not be translated! It is the well-known path on the server.
	                    String submissionUrl =
	                        settings.getString(PreferencesActivity.KEY_SUBMISSION_URL,
	                        		Collect.getInstance().getString(R.string.default_odk_submission));
	                    if ( submissionUrl.charAt(0) != '/') {
	                    	submissionUrl = "/" + submissionUrl;
	                    }

	                    urlString = urlString + submissionUrl;

	                    // ---------------- Smap Start
	                    // Add credentials pre-emptively

	                    String username = settings.getString(PreferencesActivity.KEY_USERNAME, null);
	                    String password = settings.getString(PreferencesActivity.KEY_PASSWORD, null);

	                    if(username != null && password != null) {
	                    	Uri u = Uri.parse(urlString);
	                    	WebUtils.addCredentials(username, password, u.getHost());
	                    }

                        // Add updateid if this is a non repeating task
                        boolean repeat = (c.getInt(c.getColumnIndex(InstanceColumns.T_REPEAT)) > 0);
                        String updateid = c.getString(c.getColumnIndex(InstanceColumns.T_UPDATEID));
                        if(!repeat && updateid != null) {
                            urlString = urlString + "/" + updateid;
                        }
	                    // Smap End
	                }

                    // Smap start get smap specific data values to send to the server
	                String status = c.getString(c.getColumnIndex(InstanceColumns.STATUS));	// smap get status
                    String location_trigger = c.getString(c.getColumnIndex(InstanceColumns.T_LOCATION_TRIGGER));	// smap get location trigger
                    String survey_notes = c.getString(c.getColumnIndex(InstanceColumns.T_SURVEY_NOTES));	// smap get survey notes
                    // smap end

	                // add the deviceID to the request...
	                try {
						urlString += "?deviceID=" + URLEncoder.encode(deviceId, "UTF-8");
					} catch (UnsupportedEncodingException e) {
						// unreachable...
					}

	                if ( !uploadOneSubmission(urlString, id, instance, toUpdate, localContext, uriRemap, outcome,
                            status, location_trigger, survey_notes) ) {	// smap add status
	                	return outcome; // get credentials...
	                }
	            }
	        }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        return outcome;
    }

    private String getServerSubmissionURL() {

        Collect app = Collect.getInstance();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(
                Collect.getInstance());
        String serverBase = settings.getString(PreferencesActivity.KEY_SERVER_URL,
                app.getString(R.string.default_server_url));

        if (serverBase.endsWith(URL_PATH_SEP)) {
            serverBase = serverBase.substring(0, serverBase.length() - 1);
        }

        // NOTE: /submission must not be translated! It is the well-known path on the server.
        String submissionPath = settings.getString(PreferencesActivity.KEY_SUBMISSION_URL,
                app.getString(R.string.default_odk_submission));

        if (!submissionPath.startsWith(URL_PATH_SEP)) {
            submissionPath = URL_PATH_SEP + submissionPath;
        }

        return serverBase + submissionPath;
    }

    @Override
    protected void onPostExecute(Outcome outcome) {
        synchronized (this) {
            if (mStateListener != null) {
                if (outcome.mAuthRequestingServer != null) {
                    mStateListener.authRequest(outcome.mAuthRequestingServer, outcome.mResults);
                } else {
                    mStateListener.uploadingComplete(outcome.mResults);

                    StringBuilder selection = new StringBuilder();
                    Set<String> keys = outcome.mResults.keySet();
                    Iterator<String> it = keys.iterator();

                    String[] selectionArgs = new String[keys.size() + 1];
                    int i = 0;
                    selection.append("(");
                    while (it.hasNext()) {
                        String id = it.next();
                        selection.append(InstanceColumns._ID + "=?");
                        selectionArgs[i++] = id;
                        if (i != keys.size()) {
                            selection.append(" or ");
                        }
                    }
                    selection.append(") and status=?");
                    selectionArgs[i] = InstanceProviderAPI.STATUS_SUBMITTED;

                    Cursor results = null;
                    try {
                        results = Collect
                                .getInstance()
                                .getContentResolver()
                                .query(InstanceColumns.CONTENT_URI, null, selection.toString(),
                                        selectionArgs, null);
                        if (results.getCount() > 0) {
                            Long[] toDelete = new Long[results.getCount()];
                            results.moveToPosition(-1);

                            int cnt = 0;
                            while (results.moveToNext()) {
                                toDelete[cnt] = results.getLong(results
                                        .getColumnIndex(InstanceColumns._ID));
                                cnt++;
                            }

                            boolean deleteFlag = PreferenceManager.getDefaultSharedPreferences(
                                    Collect.getInstance().getApplicationContext()).getBoolean(
                                    PreferencesActivity.KEY_DELETE_AFTER_SEND, false);
                            if (deleteFlag) {
                                DeleteInstancesTask dit = new DeleteInstancesTask();
                                dit.setContentResolver(Collect.getInstance().getContentResolver());
                                dit.execute(toDelete);
                            }

                        }
                    } finally {
                        if (results != null) {
                            results.close();
                        }
                    }
                }
            }
        }
    }


    @Override
    protected void onProgressUpdate(Integer... values) {
        synchronized (this) {
            if (mStateListener != null) {
                // update progress and total
                mStateListener.progressUpdate(values[0].intValue(), values[1].intValue());
            }
        }
    }


    public void setUploaderListener(InstanceUploaderListener sl) {
        synchronized (this) {
            mStateListener = sl;
        }
    }


    public static void copyToBytes(InputStream input, OutputStream output,
            int bufferSize) throws IOException {
        byte[] buf = new byte[bufferSize];
        int bytesRead = input.read(buf);
        while (bytesRead != -1) {
            output.write(buf, 0, bytesRead);
            bytesRead = input.read(buf);
        }
        output.flush();
    }

}