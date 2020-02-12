
package org.odk.collect.android.tasks;

import android.os.AsyncTask;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.external.handler.SmapRemoteDataItem;
import org.odk.collect.android.http.openrosa.OpenRosaHttpInterface;
import org.odk.collect.android.listeners.SmapRemoteListener;
import org.odk.collect.android.utilities.FormDownloader;
import org.odk.collect.android.utilities.WebCredentialsUtils;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;

import javax.inject.Inject;

import timber.log.Timber;

import static java.lang.Thread.sleep;

/**
 * Background task for getting values from the server
 */
public class SmapRemoteWebServiceTask extends AsyncTask<String, Void, SmapRemoteDataItem> {

    private SmapRemoteListener remoteListener;

    @Inject
    OpenRosaHttpInterface httpInterface;

    @Inject
    WebCredentialsUtils webCredentialsUtils;

    public SmapRemoteWebServiceTask(){
        Collect.getInstance().getComponent().inject(this);};

    @Override
    protected SmapRemoteDataItem doInBackground(String... params) {

        String lookupUrl = params[0];
        String timeoutValue = params[1];
        String choices = params[2];
        String imagePath = params[3];
        String imageName = params[4];

        int timeout = 0;
        try {
            timeout = Integer.valueOf(timeoutValue);
        } catch (Exception e) {

        }

        SmapRemoteDataItem item = new SmapRemoteDataItem();
        item.key = params[0];
        item.data = null;
        if(timeout == 0) {
            item.perSubmission = true;
        }
        if(choices != null && choices.equals("true")) {
            item.choices = true;
        }

        try {

            URL url = new URL(lookupUrl);
            URI uri = url.toURI();

            HashMap<String, String> headers = new HashMap<String, String> ();

            if(imagePath != null) {
                FormDownloader fd = new FormDownloader();
                File f = new File(imagePath);
                if(!f.exists()) {
                    fd.downloadFile(f, lookupUrl);
                }
                item.data = imageName;
            } else {
                item.data = httpInterface.getRequest(uri, "application/json", webCredentialsUtils.getCredentials(uri), headers);
            }

        } catch (Exception e) {
            item.data = e.getLocalizedMessage();
            Timber.e(e);

        } finally {


        }

        return item;
    }

    @Override
    protected void onPostExecute(SmapRemoteDataItem data) {
        synchronized (this) {
            try {
                if (remoteListener != null) {
                    remoteListener.remoteComplete(data);
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }

    public void setSmapRemoteListener(SmapRemoteListener sl) {
        synchronized (this) {
            remoteListener = sl;
        }
    }
}