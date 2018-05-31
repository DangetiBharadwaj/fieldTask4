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

import org.javarosa.xform.parse.XFormParser;
import org.kxml2.kdom.Element;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.listeners.DownloadFormsTaskListener;
import org.odk.collect.android.logic.FormDetails;
import org.odk.collect.android.logic.MediaFile;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.preferences.PreferenceKeys;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.utilities.DocumentFetchResult;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.STFileUtils;
import org.odk.collect.android.utilities.Utilities;
import org.odk.collect.android.utilities.Validator;
import org.odk.collect.android.utilities.WebUtils;
import org.opendatakit.httpclientandroidlib.Header;
import org.opendatakit.httpclientandroidlib.HttpEntity;
import org.opendatakit.httpclientandroidlib.HttpResponse;
import org.opendatakit.httpclientandroidlib.HttpStatus;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.methods.HttpGet;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import timber.log.Timber;

/**
 * Background task for downloading a given list of forms. We assume right now that the forms are
 * coming from the same server that presented the form list, but theoretically that won't always be
 * true.
 *
 * @author msundt
 * @author carlhartung
 */
public class FormDownloaderOld extends
        AsyncTask<ArrayList<FormDetails>, String, HashMap<FormDetails, String>> {

    private static final String MD5_COLON_PREFIX = "md5:";
    private static final String TEMP_DOWNLOAD_EXTENSION = ".tempDownload";

    private DownloadFormsTaskListener stateListener;

    private FormsDao formsDao;

    private static final String NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST =
            "http://openrosa.org/xforms/xformsManifest";

    static boolean isXformsManifestNamespacedElement(Element e) {
        return e.getNamespace().equalsIgnoreCase(NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST);
    }

    private class TaskCancelledException extends Exception {
        private final File file;

        TaskCancelledException(File file) {
            super("Task was cancelled during processing of " + file);
            this.file = file;
        }

        TaskCancelledException() {
            super("Task was cancelled");
            this.file = null;
        }
    }

    @Override
    public HashMap<FormDetails, String> doInBackground(ArrayList<FormDetails>... values) {   // smap make public
        ArrayList<FormDetails> toDownload = values[0];

        formsDao = new FormsDao();
        int total = toDownload.size();
        int count = 1;
        Collect.getInstance().getActivityLogger().logAction(this, "downloadForms",
                String.valueOf(total));

        final HashMap<FormDetails, String> result = new HashMap<>();

        for (FormDetails fd : toDownload) {
            try {
                String message = processOneForm(total, count++, fd);
                result.put(fd, message.isEmpty() ?
                        Collect.getInstance().getString(R.string.success) : message);
            } catch (TaskCancelledException cd) {
                break;
            }
        }

        return result;
    }

    @Override
    protected void onCancelled(HashMap<FormDetails, String> formDetailsStringHashMap) {
        synchronized (this) {
            if (stateListener != null) {
                stateListener.formsDownloadingCancelled();
            }
        }
    }

    /**
     * Processes one form download.
     *
     * @param total the total number of forms being downloaded by this task
     * @param count the number of this form
     * @param fd    the FormDetails
     * @return an empty string for success, or a nonblank string with one or more error messages
     * @throws TaskCancelledException to signal that form downloading is to be canceled
     */
    private String processOneForm(int total, int count, FormDetails fd) throws TaskCancelledException {
        publishProgress(fd.getFormName(), String.valueOf(count), String.valueOf(total));
        String message = "";
        if (isCancelled()) {
            throw new TaskCancelledException();
        }

        String tempMediaPath = null;
        String orgTempMediaPath = null;     // smap
        final String finalMediaPath;
        String orgMediaPath = null;          // smap
        FileResult fileResult = null;
        try {
            String deviceId = new PropertyManager(Collect.getInstance().getApplicationContext())
                    .getSingularProperty(PropertyManager.PROPMGR_DEVICE_ID);        // smap

            // get the xml file
            // if we've downloaded a duplicate, this gives us the file
            fileResult = downloadXform(fd.getFormName(), fd.getDownloadUrl() + "&deviceID=" + deviceId,
                    fd.isNewerFormVersionAvailable());  // smap add flag on newer form version available

            if(fileResult.isNew()) {    // smap
                message += Collect.getInstance().getString(R.string.success);
            }
            message += "\n";

            if (fd.getManifestUrl() != null) {
                // use a temporary media path until everything is ok.
                tempMediaPath = new File(Collect.CACHE_PATH,
                        String.valueOf(System.currentTimeMillis())).getAbsolutePath();
                orgTempMediaPath = new File(tempMediaPath + "_org").getAbsolutePath();      // smap
                finalMediaPath = FileUtils.constructMediaPath(
                        fileResult.getFile().getAbsolutePath());
                orgMediaPath = Utilities.getOrgMediaPath();                                         // smap
                String error = downloadManifestAndMediaFiles(tempMediaPath, finalMediaPath, fd,
                        count, total, orgTempMediaPath, orgMediaPath);                              // smap added org paths
                if (error != null) {
                    message += error;
                }
            } else {
                Timber.i("No Manifest for: %s", fd.getFormName());
            }
        } catch (TaskCancelledException e) {
            Timber.i(e.getMessage());
            cleanUp(fileResult, e.file, tempMediaPath, orgTempMediaPath);   // smap

            // do not download additional forms.
            throw e;
        } catch (Exception e) {
            message += getExceptionMessage(e);
        }

        if (isCancelled()) {
            cleanUp(fileResult, null, tempMediaPath, orgTempMediaPath);     // smap
            fileResult = null;
        }

        Map<String, String> parsedFields = null;
        if (fileResult != null && fileResult.file.exists()) {       // smap add check for exists
            try {
                final long start = System.currentTimeMillis();
                Timber.w("Parsing document %s", fileResult.file.getAbsolutePath());
                parsedFields = FileUtils.parseXML(fileResult.file);
                Timber.i("Parse finished in %.3f seconds.",
                        (System.currentTimeMillis() - start) / 1000F);
            } catch (RuntimeException e) {
                message += e.getMessage();
            }
        }

        boolean installed = false;

        if (!isCancelled() && parsedFields != null) {       // smap remove check on empty message
            if (isSubmissionOk(parsedFields)) {
                installEverything(tempMediaPath, fileResult, parsedFields, fd, orgTempMediaPath, orgMediaPath);     // Added organisation paths
                installed = true;
            } else {
                message += Collect.getInstance().getString(R.string.xform_parse_error,
                        fileResult.file.getName(), "submission url");
            }
        }
        if (!installed) {
            cleanUp(fileResult, null, tempMediaPath, orgTempMediaPath);     // smap
        }
        return message;
    }

    private boolean isSubmissionOk(Map<String, String> parsedFields) {
        String submission = parsedFields.get(FileUtils.SUBMISSIONURI);
        return submission == null || Validator.isUrlValid(submission);
    }

    private void installEverything(String tempMediaPath, FileResult fileResult, Map<String,
            String> parsedFields, FormDetails fd, String orgTempMediaPath, String orgMediaPath)   // smap added organisational paths
            throws TaskCancelledException {     // smap add fd
        UriResult uriResult = null;
        try {
            uriResult = findExistingOrCreateNewUri(fileResult.file, parsedFields, STFileUtils.getSource(fd.getDownloadUrl()), fd.getTasksOnly());  // smap add source and tasks_only
            Timber.w("Form uri = %s, isNew = %b", uriResult.getUri().toString(), uriResult.isNew());

            // move the media files in the media folder
            if (tempMediaPath != null) {

                File orgMediaDir = new File(orgMediaPath);                      // smap
                File orgTempMediaDir = new File(orgTempMediaPath);              // smap
                File[] orgTempFiles = orgTempMediaDir.listFiles();              // smap
                if(orgTempFiles != null && orgTempFiles.length > 0) {           // smap Save a copy the media files in the org media directory
                    for (File mf : orgTempFiles) {
                        try {
                            FileUtils.deleteOldFile(mf.getName(), orgMediaDir); // smap
                            if (mf.getName().endsWith(".json")) {
                                org.apache.commons.io.FileUtils.moveFileToDirectory(mf, orgMediaDir, true);     // Move json files
                            } else {
                                org.apache.commons.io.FileUtils.copyFileToDirectory(mf, orgMediaDir, true);     // For other files only a copy is saved
                            }
                        } catch (Exception e) {
                        }
                    }
                }

                File formMediaPath = new File(uriResult.getMediaPath());
                FileUtils.moveMediaFiles(orgTempMediaPath, formMediaPath);      // smap Move org files first and overwrite with form level
                FileUtils.moveMediaFiles(tempMediaPath, formMediaPath);
            }
        } catch (IOException e) {
            Timber.e(e);

            if (uriResult != null && uriResult.isNew() && fileResult.isNew()) {
                // this means we should delete the entire form together with the metadata
                Uri uri = uriResult.getUri();
                Timber.w("The form is new. We should delete the entire form.");
                int deletedCount = Collect.getInstance().getContentResolver().delete(uri,
                        null, null);
                Timber.w("Deleted %d rows using uri %s", deletedCount, uri.toString());
            }

            cleanUp(fileResult, null, tempMediaPath, orgTempMediaPath);     // smap add organisation
        }
    }

    private void cleanUp(FileResult fileResult, File fileOnCancel, String tempMediaPath, String orgTempMediaPath) {     // smap add org
        if (fileResult == null) {
            Timber.w("The user cancelled (or an exception happened) the download of a form at the "
                    + "very beginning.");
        } else {
            if(fileResult.file.exists()) {  // smap
                formsDao.deleteFormsFromMd5Hash(FileUtils.getMd5Hash(fileResult.file));
                FileUtils.deleteAndReport(fileResult.getFile());
            }
        }

        FileUtils.deleteAndReport(fileOnCancel);

        if (tempMediaPath != null) {
            FileUtils.purgeMediaPath(tempMediaPath);
        }
        if (orgTempMediaPath != null) {     // smap
            FileUtils.purgeMediaPath(orgTempMediaPath);
        }
    }

    private String getExceptionMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            msg = e.toString();
        }
        Timber.e(msg);

        if (e.getCause() != null) {
            msg = e.getCause().getMessage();
            if (msg == null) {
                msg = e.getCause().toString();
            }
        }
        return msg;
    }

    /**
     * Creates a new form in the database, if none exists with the same absolute path. Returns
     * information with the URI, media path, and whether the form is new.
     *
     * @param formFile the form definition file
     * @param formInfo certain fields extracted from the parsed XML form, such as title and form ID
     * @return a {@link FormDownloaderOld.UriResult} object
     */
    private UriResult findExistingOrCreateNewUri(File formFile, Map<String, String> formInfo, String source, boolean tasks_only)   // smap add source as a parameter
            throws TaskCancelledException {
        Cursor cursor = null;
        final Uri uri;
        final String formFilePath = formFile.getAbsolutePath();
        String mediaPath = FileUtils.constructMediaPath(formFilePath);
        final boolean isNew;

        FileUtils.checkMediaPath(new File(mediaPath));

        try {
            cursor = formsDao.getFormsCursorForFormFilePath(formFile.getAbsolutePath());
            isNew = cursor.getCount() <= 0;

            if (isNew) {
                uri = saveNewForm(formInfo, formFile, mediaPath, tasks_only, source);              // smap add tasks_only and source
            } else {
                cursor.moveToFirst();
                uri = Uri.withAppendedPath(FormsColumns.CONTENT_URI,
                        cursor.getString(cursor.getColumnIndex(FormsColumns._ID)));
                mediaPath = cursor.getString(cursor.getColumnIndex(FormsColumns.FORM_MEDIA_PATH));
                Collect.getInstance().getActivityLogger().logAction(this, "refresh",
                        formFilePath);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return new UriResult(uri, mediaPath, isNew);
    }

    private Uri saveNewForm(Map<String, String> formInfo, File formFile, String mediaPath,
                            boolean tasks_only, String source) {    // smap add tasks_only and source
        final ContentValues v = new ContentValues();
        v.put(FormsColumns.FORM_FILE_PATH,          formFile.getAbsolutePath());
        v.put(FormsColumns.FORM_MEDIA_PATH,         mediaPath);
        v.put(FormsColumns.DISPLAY_NAME,            formInfo.get(FileUtils.TITLE));
        v.put(FormsColumns.JR_VERSION,              formInfo.get(FileUtils.VERSION));
        v.put(FormsColumns.JR_FORM_ID,              formInfo.get(FileUtils.FORMID));
        v.put(FormsColumns.PROJECT,                 formInfo.get(FileUtils.PROJECT));      // smap
        v.put(FormsColumns.TASKS_ONLY,              tasks_only ? "yes" : "no");            // smap
        v.put(FormsColumns.SOURCE,                  source);                               // smap
        v.put(FormsColumns.SUBMISSION_URI,          formInfo.get(FileUtils.SUBMISSIONURI));
        v.put(FormsColumns.BASE64_RSA_PUBLIC_KEY,   formInfo.get(FileUtils.BASE64_RSA_PUBLIC_KEY));
        v.put(FormsColumns.AUTO_DELETE,             formInfo.get(FileUtils.AUTO_DELETE));
        v.put(FormsColumns.AUTO_SEND,             formInfo.get(FileUtils.AUTO_SEND));
        Uri uri = formsDao.saveForm(v);
        Collect.getInstance().getActivityLogger().logAction(this, "insert",
                formFile.getAbsolutePath());
        return uri;
    }

    /**
     * Takes the formName and the URL and attempts to download the specified file. Returns a file
     * object representing the downloaded file.
     */
    public FileResult downloadXform(String formName, String url, boolean download)      // smap add download flag
            throws IOException, TaskCancelledException, Exception {
        // clean up friendly form name...
        String rootName = formName.replaceAll("[^\\p{L}\\p{Digit}]", " ");
        rootName = rootName.replaceAll("\\p{javaWhitespace}+", " ");
        rootName = rootName.trim();

        // proposed name of xml file...
        String path = Collect.FORMS_PATH + File.separator + rootName + ".xml";
        int i = 2;
        File f = new File(path);

        boolean isNew = false;      // smap
        if(download) {              // smap
            while (f.exists()) {
                path = Collect.FORMS_PATH + File.separator + rootName + "_" + i + ".xml";
                f = new File(path);
                i++;
            }

            downloadFile(f, url);

            isNew = true;

            // we've downloaded the file, and we may have renamed it
            // make sure it's not the same as a file we already have
            Cursor c = null;
            try {
                c = formsDao.getFormsCursorForMd5Hash(FileUtils.getMd5Hash(f));
                if (c.getCount() > 0) {
                    // Should be at most, 1
                    c.moveToFirst();

                    isNew = false;

                    // delete the file we just downloaded, because it's a duplicate
                    Timber.w("A duplicate file has been found, we need to remove the downloaded file "
                            + "and return the other one.");
                    FileUtils.deleteAndReport(f);

                    // set the file returned to the file we already had
                    String existingPath = c.getString(c.getColumnIndex(FormsColumns.FORM_FILE_PATH));
                    f = new File(existingPath);
                    Timber.w("Will use %s", existingPath);
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }

        }
        return new FileResult(f, isNew);    // smap


    }

    /**
     * Common routine to download a document from the downloadUrl and save the contents in the file
     * 'file'. Shared by media file download and form file download.
     * <p>
     * SurveyCTO: The file is saved into a temp folder and is moved to the final place if everything
     * is okay, so that garbage is not left over on cancel.
     *
     * @param file        the final file
     * @param downloadUrl the url to get the contents from.
     */
    public void downloadFile(File file, String downloadUrl)     // smap made public
            throws IOException, TaskCancelledException, URISyntaxException, Exception {
        File tempFile = File.createTempFile(file.getName(), TEMP_DOWNLOAD_EXTENSION,
                new File(Collect.CACHE_PATH));

        URI uri;
        try {
            // assume the downloadUrl is escaped properly
            URL url = new URL(downloadUrl);
            uri = url.toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            Timber.e(e, "Unable to get a URI for download URL : %s  due to %s : ", downloadUrl, e.getMessage());
            throw e;
        }

        // WiFi network connections can be renegotiated during a large form download sequence.
        // This will cause intermittent download failures.  Silently retry once after each
        // failure.  Only if there are two consecutive failures do we abort.
        boolean success = false;
        int attemptCount = 0;
        final int MAX_ATTEMPT_COUNT = 2;
        while (!success && ++attemptCount <= MAX_ATTEMPT_COUNT) {
            if (isCancelled()) {
                throw new TaskCancelledException(tempFile);
            }
            Timber.i("Started downloading to %s from %s", tempFile.getAbsolutePath(), downloadUrl);

            // get shared HttpContext so that authentication and cookies are retained.
            HttpContext localContext = Collect.getInstance().getHttpContext();

            HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

            // ---------------- Smap Start
            // Add credentials
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(Collect.getInstance());

            String username = sharedPreferences.getString(PreferenceKeys.KEY_USERNAME, null);
            String password = sharedPreferences.getString(PreferenceKeys.KEY_PASSWORD, null);

            String server =
                    sharedPreferences.getString(PreferenceKeys.KEY_SERVER_URL, null);

            if (username != null && password != null) {
                Uri u = Uri.parse(downloadUrl);
                WebUtils.addCredentials(username, password, u.getHost());
            }
            // Smap End
            // set up request...
            HttpGet req = WebUtils.createOpenRosaHttpGet(uri);
            req.addHeader(WebUtils.ACCEPT_ENCODING_HEADER, WebUtils.GZIP_CONTENT_ENCODING);

            HttpResponse response;
            try {
                response = httpclient.execute(req, localContext);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode != HttpStatus.SC_OK) {
                    WebUtils.discardEntityBytes(response);
                    if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                        // clear the cookies -- should not be necessary?
                        Collect.getInstance().getCookieStore().clear();
                    }
                    String errMsg =
                            Collect.getInstance().getString(R.string.file_fetch_failed, downloadUrl,
                                    response.getStatusLine().getReasonPhrase(), String.valueOf(statusCode));
                    Timber.e(errMsg);
                    throw new Exception(errMsg);
                }

                // write connection to file
                InputStream is = null;
                OutputStream os = null;
                try {
                    HttpEntity entity = response.getEntity();
                    is = entity.getContent();
                    Header contentEncoding = entity.getContentEncoding();
                    if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase(
                            WebUtils.GZIP_CONTENT_ENCODING)) {
                        is = new GZIPInputStream(is);
                    }
                    os = new FileOutputStream(tempFile);
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = is.read(buf)) > 0 && !isCancelled()) {
                        os.write(buf, 0, len);
                    }
                    os.flush();
                    success = true;
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (Exception e) {
                            Timber.e(e);
                        }
                    }
                    if (is != null) {
                        try {
                            // ensure stream is consumed...
                            final long count = 1024L;
                            while (is.skip(count) == count) {
                                // skipping to the end of the http entity
                            }
                        } catch (Exception e) {
                            // no-op
                        }
                        try {
                            is.close();
                        } catch (Exception e) {
                            Timber.e(e);
                        }
                    }
                }
            } catch (Exception e) {
                Timber.e(e.toString());
                // silently retry unless this is the last attempt,
                // in which case we rethrow the exception.

                FileUtils.deleteAndReport(tempFile);

                if (attemptCount == MAX_ATTEMPT_COUNT) {
                    throw e;
                }
            }

            if (isCancelled()) {
                FileUtils.deleteAndReport(tempFile);
                throw new TaskCancelledException(tempFile);
            }
        }

        Timber.d("Completed downloading of %s. It will be moved to the proper path...",
                tempFile.getAbsolutePath());

        FileUtils.deleteAndReport(file);

        String errorMessage = FileUtils.copyFile(tempFile, file);

        if (file.exists()) {
            Timber.w("Copied %s over %s", tempFile.getAbsolutePath(), file.getAbsolutePath());
            FileUtils.deleteAndReport(tempFile);
        } else {
            String msg = Collect.getInstance().getString(R.string.fs_file_copy_error,
                    tempFile.getAbsolutePath(), file.getAbsolutePath(), errorMessage);
            Timber.w(msg);
            throw new RuntimeException(msg);
        }
    }

    private static class UriResult {

        private final Uri uri;
        private final String mediaPath;
        private final boolean isNew;

        private UriResult(Uri uri, String mediaPath, boolean isNew) {
            this.uri = uri;
            this.mediaPath = mediaPath;
            this.isNew = isNew;
        }

        private Uri getUri() {
            return uri;
        }

        private String getMediaPath() {
            return mediaPath;
        }

        private boolean isNew() {
            return isNew;
        }
    }

    public static class FileResult {   // smap make public

        private final File file;
        private final boolean isNew;

        public FileResult(File file, boolean isNew) {   // smap make public
            this.file = file;
            this.isNew = isNew;
        }

        public File getFile() {    // smap make public
            return file;
        }

        public boolean isNew() {    // smap make public
            return isNew;
        }
    }

    private String downloadManifestAndMediaFiles(String tempMediaPath, String finalMediaPath,
                                                 FormDetails fd, int count,
                                                 int total,
                                                 String orgTempMediaPath,   // smap
                                                 String orgMediaPath        // smap
                ) throws Exception {
        if (fd.getManifestUrl() == null) {
            return null;
        }

        StringBuffer downloadMsg = new StringBuffer("");        // smap

        publishProgress(Collect.getInstance().getString(R.string.fetching_manifest, fd.getFormName()),
                String.valueOf(count), String.valueOf(total));


        List<MediaFile> files = new ArrayList<MediaFile>();
        // get shared HttpContext so that authentication and cookies are retained.
        HttpContext localContext = Collect.getInstance().getHttpContext();

        HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

        DocumentFetchResult result =
                WebUtils.getXmlDocument(fd.getManifestUrl(), localContext, httpclient);

        if (result.errorMessage != null) {
            return result.errorMessage;
        }

        String errMessage = Collect.getInstance().getString(R.string.access_error, fd.getManifestUrl());

        if (!result.isOpenRosaResponse) {
            errMessage += Collect.getInstance().getString(R.string.manifest_server_error);
            Timber.e(errMessage);
            return errMessage;
        }

        // Attempt OpenRosa 1.0 parsing
        Element manifestElement = result.doc.getRootElement();
        if (!manifestElement.getName().equals("manifest")) {
            errMessage +=
                    Collect.getInstance().getString(R.string.root_element_error,
                            manifestElement.getName());
            Timber.e(errMessage);
            return errMessage;
        }
        String namespace = manifestElement.getNamespace();
        if (!isXformsManifestNamespacedElement(manifestElement)) {
            errMessage += Collect.getInstance().getString(R.string.root_namespace_error, namespace);
            Timber.e(errMessage);
            return errMessage;
        }
        int elements = manifestElement.getChildCount();
        for (int i = 0; i < elements; ++i) {
            if (manifestElement.getType(i) != Element.ELEMENT) {
                // e.g., whitespace (text)
                continue;
            }
            Element mediaFileElement = manifestElement.getElement(i);
            if (!isXformsManifestNamespacedElement(mediaFileElement)) {
                // someone else's extension?
                continue;
            }
            String name = mediaFileElement.getName();
            if (name.equalsIgnoreCase("mediaFile")) {
                String filename = null;
                String hash = null;
                String downloadUrl = null;
                // don't process descriptionUrl
                int childCount = mediaFileElement.getChildCount();
                for (int j = 0; j < childCount; ++j) {
                    if (mediaFileElement.getType(j) != Element.ELEMENT) {
                        // e.g., whitespace (text)
                        continue;
                    }
                    Element child = mediaFileElement.getElement(j);
                    if (!isXformsManifestNamespacedElement(child)) {
                        // someone else's extension?
                        continue;
                    }
                    String tag = child.getName();
                    switch (tag) {
                        case "filename":
                            filename = XFormParser.getXMLText(child, true);
                            if (filename != null && filename.length() == 0) {
                                filename = null;
                            }
                            break;
                        case "hash":
                            hash = XFormParser.getXMLText(child, true);
                            if (hash != null && hash.length() == 0) {
                                hash = null;
                            }
                            break;
                        case "downloadUrl":
                            downloadUrl = XFormParser.getXMLText(child, true);
                            if (downloadUrl != null && downloadUrl.length() == 0) {
                                downloadUrl = null;
                            }
                            break;
                    }
                }
                if (filename == null || downloadUrl == null || hash == null) {
                    errMessage +=
                            Collect.getInstance().getString(R.string.manifest_tag_error,
                                    Integer.toString(i));
                    Timber.e(errMessage);
                    return errMessage;
                }
                files.add(new MediaFile(filename, hash, downloadUrl));
            }
        }

        // OK we now have the full set of files to download...
        Timber.i("Downloading %d media files.", files.size());
        int mediaCount = 0;
        if (files.size() > 0) {
            File tempMediaDir = new File(tempMediaPath);
            File orgTempMediaDir = new File(orgTempMediaPath);  // smap organisational media
            File finalMediaDir = new File(finalMediaPath);
            File orgMediaDir = new File (orgMediaPath);         // smap organisational media

            FileUtils.checkMediaPath(tempMediaDir);
            FileUtils.checkMediaPath(orgTempMediaDir);          // smap
            FileUtils.checkMediaPath(finalMediaDir);
            FileUtils.checkMediaPath(orgMediaDir);              // smap

            for (MediaFile toDownload : files) {
                ++mediaCount;
                publishProgress(
                        Collect.getInstance().getString(R.string.form_download_progress,
                                fd.getFormName(),
                                String.valueOf(mediaCount), String.valueOf(files.size())),
                                String.valueOf(count), String.valueOf(total));
                //try {
                // start smap organisational media
                File finalMediaFile = null;
                File finalImportedMediaFile = null;
                File tempMediaFile = null;
                if(toDownload.getDownloadUrl().endsWith("organisation")) {
                    finalMediaFile = new File(orgMediaDir, toDownload.getFilename());
                    tempMediaFile = new File(orgTempMediaDir, toDownload.getFilename());
                } else {
                    finalMediaFile = new File(finalMediaDir, toDownload.getFilename());
                    tempMediaFile = new File(tempMediaDir, toDownload.getFilename());
                    if(finalMediaFile.getName().endsWith(".csv")) {
                        finalImportedMediaFile = new File(finalMediaDir, toDownload.getFilename() + ".imported");
                    }
                }

                // end smap

                if(finalMediaFile.getName().endsWith(".json")) {      // smap
                    // Process incremental json files
                    // 2. Ensure final form directory has an empty file of this name to record that it is in use
                    File jsonFile = new File(finalMediaDir.getPath() + "/" + finalMediaFile.getName());
                    jsonFile.createNewFile();
                } else if (!finalMediaFile.exists() && (finalImportedMediaFile == null || !finalImportedMediaFile.exists())) { // smap for csv files the final media file may have had imported added to its name
                    downloadMsg.append(Collect.getInstance().getString(R.string.smap_get_media, tempMediaFile.getName())).append("\n");     // smap
                    downloadFile(tempMediaFile, toDownload.getDownloadUrl());
                } else {
                    String currentFileHash = FileUtils.getMd5Hash(finalMediaFile);
                    if(currentFileHash == null && finalImportedMediaFile != null && finalImportedMediaFile.exists()) { // smap get hash from imported if necessary
                        currentFileHash = FileUtils.getMd5Hash(finalImportedMediaFile);
                    }
                    String downloadFileHash = getMd5Hash(toDownload.getHash());

                    if (currentFileHash != null && downloadFileHash != null && !currentFileHash.contentEquals(downloadFileHash)) {
                        // if the hashes match, it's the same file
                        // otherwise delete our current one and replace it with the new one
                        FileUtils.deleteAndReport(finalMediaFile);
                        downloadMsg.append(Collect.getInstance().getString(R.string.smap_get_media, tempMediaFile.getName())).append("\n");     // smap
                        downloadFile(tempMediaFile, toDownload.getDownloadUrl());
                    } else {
                        // exists, and the hash is the same
                        // no need to download it again
                        Timber.i("Skipping media file fetch -- file hashes identical: %s",
                                finalMediaFile.getAbsolutePath());

                        if(toDownload.getDownloadUrl().endsWith("organisation")) {  // smap Lets get this file and copy it to the form
                            try {
                                org.apache.commons.io.FileUtils.copyFileToDirectory(finalMediaFile, finalMediaDir, true);
                            } catch(Exception e) {
                                // Ignore an error if the file already exists
                            }
                        }
                    }
                }
                //  } catch (Exception e) {
                //  return e.getLocalizedMessage();
                //}
            }
        }
        if(downloadMsg.length() > 0) {      // smap
            return downloadMsg.toString();
        } else {
            return null;
        }
    }

    @Override
    protected void onPostExecute(HashMap<FormDetails, String> value) {
        synchronized (this) {
            if (stateListener != null) {
                stateListener.formsDownloadingComplete(value);
            }
        }
    }

    @Override
    protected void onProgressUpdate(String... values) {
        synchronized (this) {
            if (stateListener != null) {
                // update progress and total
                stateListener.progressUpdate(values[0],
                        Integer.parseInt(values[1]),
                        Integer.parseInt(values[2]));
            }
        }

    }

    public void setDownloaderListener(DownloadFormsTaskListener sl) {
        synchronized (this) {
            stateListener = sl;
        }
    }

    static String getMd5Hash(String hash) {
        return hash == null ? null : hash.substring(MD5_COLON_PREFIX.length());
    }
}