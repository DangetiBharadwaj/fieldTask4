/*
 * Copyright (C) 2017 Smap Consulting Pty Ltd
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

package org.odk.collect.android.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import org.odk.collect.android.R;
import org.odk.collect.android.adapters.ViewPagerAdapter;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.fragments.SmapFormListFragment;
import org.odk.collect.android.fragments.SmapTaskListFragment;
import org.odk.collect.android.fragments.SmapTaskMapFragment;
import org.odk.collect.android.listeners.DownloadFormsTaskListener;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.listeners.NFCListener;
import org.odk.collect.android.listeners.TaskDownloaderListener;
import org.odk.collect.android.loaders.MapDataLoader;
import org.odk.collect.android.loaders.MapEntry;
import org.odk.collect.android.loaders.TaskEntry;
import org.odk.collect.android.logic.FormDetails;
import org.odk.collect.android.preferences.AdminKeys;
import org.odk.collect.android.preferences.AdminPreferencesActivity;
import org.odk.collect.android.preferences.AutoSendPreferenceMigrator;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.provider.FormsProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.receivers.NetworkReceiver;
import org.odk.collect.android.services.LocationService;
import org.odk.collect.android.services.NotificationRegistrationService;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.storage.StorageStateProvider;
import org.odk.collect.android.storage.StorageSubdirectory;
import org.odk.collect.android.storage.migration.StorageMigrationDialog;
import org.odk.collect.android.storage.migration.StorageMigrationRepository;
import org.odk.collect.android.storage.migration.StorageMigrationResult;
import org.odk.collect.android.taskModel.FormLaunchDetail;
import org.odk.collect.android.taskModel.FormRestartDetails;
import org.odk.collect.android.taskModel.NfcTrigger;
import org.odk.collect.android.tasks.DownloadTasksTask;
import org.odk.collect.android.tasks.NdefReaderTask;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.DialogUtils;
import org.odk.collect.android.utilities.ManageForm;
import org.odk.collect.android.utilities.SharedPreferencesUtils;
import org.odk.collect.android.utilities.SnackbarUtils;
import org.odk.collect.android.utilities.ToastUtils;
import org.odk.collect.android.utilities.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;
import butterknife.BindView;
import timber.log.Timber;

public class SmapMain extends CollectAbstractActivity implements TaskDownloaderListener,
        NFCListener,
        InstanceUploaderListener,
        DownloadFormsTaskListener {

    private static final int PROGRESS_DIALOG = 1;
    private static final int ALERT_DIALOG = 2;
    private static final int PASSWORD_DIALOG = 3;
    private static final int COMPLETE_FORM = 4;

    private AlertDialog mAlertDialog;
    private ProgressDialog mProgressDialog;
    private String mAlertMsg;
    private boolean mPaused = false;

    private MapEntry mData;

    public static final String EXTRA_REFRESH = "refresh";
    public static final String LOGIN_STATUS = "login_status";

    private SmapFormListFragment formManagerList = SmapFormListFragment.newInstance();
    private SmapTaskListFragment taskManagerList = SmapTaskListFragment.newInstance();
    private SmapTaskMapFragment taskManagerMap = SmapTaskMapFragment.newInstance();

    private MapDataLoader mTaskLoader = null;

    private NfcAdapter mNfcAdapter;        // NFC
    public PendingIntent mNfcPendingIntent;
    public IntentFilter[] mNfcFilters;
    public NdefReaderTask mReadNFC;
    public ArrayList<NfcTrigger> nfcTriggersList;   // nfcTriggers (geofence should have separate list)

    private String mProgressMsg;
    public DownloadTasksTask mDownloadTasks;

    private MainTaskListener listener = null;
    private NetworkReceiver networkReceiver = null;

    boolean listenerRegistered = false;
    private static List<TaskEntry> mTasks = null;

    private Intent mLocationServiceIntent = null;
    private LocationService mLocationService = null;

    /*
     * Start scoped storage
     */
    private int savedCount;
    private final SmapMain.IncomingHandler handler = new SmapMain.IncomingHandler();
    private final SmapMain.MyContentObserver contentObserver = new SmapMain.MyContentObserver();
    @BindView(R.id.storageMigrationBanner)
    LinearLayout storageMigrationBanner;

    @BindView(R.id.storageMigrationBannerText)
    TextView storageMigrationBannerText;

    @BindView(R.id.storageMigrationBannerDismissButton)
    Button storageMigrationBannerDismissButton;

    @BindView(R.id.storageMigrationBannerLearnMoreButton)
    Button storageMigrationBannerLearnMoreButton;

    @BindView(R.id.version_sha)
    TextView versionSHAView;

    @Inject
    StorageMigrationRepository storageMigrationRepository;

    @Inject
    StorageStateProvider storageStateProvider;

    @Inject
    StoragePathProvider storagePathProvider;

    // End scoped storage

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setTitle(getString(R.string.app_name));
        toolbar.setNavigationIcon(R.mipmap.ic_launcher);
        setSupportActionBar(toolbar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.smap_main_layout);
        initToolbar();

        String[] tabNames = {getString(R.string.smap_forms), getString(R.string.smap_tasks), getString(R.string.smap_map)};
        // Get the ViewPager and set its PagerAdapter so that it can display items
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);

        ArrayList<Fragment> fragments = new ArrayList<>();
        fragments.add(formManagerList);
        fragments.add(taskManagerList);
        fragments.add(taskManagerMap);

        viewPager.setAdapter(new ViewPagerAdapter(
                getSupportFragmentManager(), tabNames, fragments));

        // Give the SlidingTabLayout the ViewPager
        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        // Attach the view pager to the tab strip
        tabLayout.setBackgroundColor(getResources().getColor(R.color.tabBackground));

        tabLayout.setTabTextColors(Color.LTGRAY, Color.WHITE);
        tabLayout.setupWithViewPager(viewPager);

        // get notification registration token
        Intent intent = new Intent(this, NotificationRegistrationService.class);
        startService(intent);

        // Start the location service
        mLocationService = new LocationService(this);
        mLocationServiceIntent = new Intent(this, mLocationService.getClass());
        startService(mLocationServiceIntent);

        // Show login status if it was set
        String login_status = getIntent().getStringExtra(LOGIN_STATUS);
        if(login_status != null) {
            if(login_status.equals("success")) {
                SnackbarUtils.showShortSnackbar(findViewById(R.id.pager), Collect.getInstance().getString(R.string.smap_login_success));
            } else if(login_status.equals("failed")) {
                SnackbarUtils.showShortSnackbar(findViewById(R.id.pager), Collect.getInstance().getString(R.string.smap_login_failed));
            }
        }
        // Initiate a refresh if requested in start parameters
        String refresh = getIntent().getStringExtra(EXTRA_REFRESH);
        if(refresh != null && refresh.equals("yes")) {
            processGetTask();
        }

        // Get settings if available in a file
        StoragePathProvider storagePathProvider = new StoragePathProvider();
        File f = new File(storagePathProvider.getStorageRootDirPath() + "/collect.settings");
        File j = new File(storagePathProvider.getStorageRootDirPath() + "/collect.settings.json");
        // Give JSON file preference
        if (j.exists()) {
            SharedPreferencesUtils sharedPrefs = new SharedPreferencesUtils();
            boolean success = sharedPrefs.loadSharedPreferencesFromJSONFile(j);
            if (success) {
                ToastUtils.showLongToast(R.string.settings_successfully_loaded_file_notification);
                j.delete();

                // Delete settings file to prevent overwrite of settings from JSON file on next startup
                if (f.exists()) {
                    f.delete();
                }
            } else {
                ToastUtils.showLongToast(R.string.corrupt_settings_file_notification);
            }
        } else if (f.exists()) {
            boolean success = loadSharedPreferencesFromFile(f);
            if (success) {
                ToastUtils.showLongToast(R.string.settings_successfully_loaded_file_notification);
                f.delete();
            } else {
                ToastUtils.showLongToast(R.string.corrupt_settings_file_notification);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.mipmap.ic_launcher);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mPaused = false;

        if (!listenerRegistered) {
            listener = new MainTaskListener(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction("startTask");
            registerReceiver(listener, filter);

            networkReceiver = new NetworkReceiver();
            filter = new IntentFilter();
            filter.addAction("org.odk.collect.android.FormSaved");
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(networkReceiver, filter);

            listenerRegistered = true;
        }

        // NFC
        boolean nfcAuthorised = false;
        SharedPreferences sharedPreferences = this.getSharedPreferences(
                AdminPreferencesActivity.ADMIN_PREFERENCES, 0);

        if (sharedPreferences.getBoolean(GeneralKeys.KEY_SMAP_LOCATION_TRIGGER, true)) {
            if(mNfcAdapter == null) {
                mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            }

            if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {

                // Pending intent
                Intent nfcIntent = new Intent(getApplicationContext(), getClass());
                nfcIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                if(mNfcPendingIntent == null) {
                    mNfcPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, nfcIntent, 0);
                }

                if(mNfcFilters == null) {
                    // Filter
                    IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
                    mNfcFilters = new IntentFilter[]{
                            filter
                    };
                }

                mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mNfcFilters, null);

            }
        }

    }

    @Override
    protected void onStop() {
        if (listener != null) {
            try {
                unregisterReceiver(listener);
                unregisterReceiver(networkReceiver);
                listener = null;
            } catch (Exception e) {
                Timber.e("Error on unregister: " + e.getMessage());
                // Ignore - preumably already unregistered
            }
        }
        listenerRegistered = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopService(mLocationServiceIntent);
        super.onDestroy();

    }

    public void processAdminMenu() {
        showDialog(PASSWORD_DIALOG);
    }

    // Get tasks and forms from the server
    public void processGetTask() {

        mProgressMsg = getString(R.string.smap_synchronising);
        if(!this.isFinishing()) {
            showDialog(PROGRESS_DIALOG);
        }
        mDownloadTasks = new DownloadTasksTask();
        mDownloadTasks.setDownloaderListener(this, this);
        mDownloadTasks.execute();
    }

    public void processHistory() {
        if (Collect.allowClick(getClass().getName())) {
            Intent i = new Intent(getApplicationContext(), HistoryActivity.class);
            i.putExtra(ApplicationConstants.BundleKeys.FORM_MODE,
                    ApplicationConstants.FormModes.VIEW_SENT);
            startActivity(i);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener loadingButtonListener =
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                if (mDownloadTasks != null) {
                                    mDownloadTasks.setDownloaderListener(null, SmapMain.this);
                                    mDownloadTasks.cancel(true);
                                }
                                // Refresh the task list
                                Intent intent = new Intent("org.smap.smapTask.refresh");
                                LocalBroadcastManager.getInstance(Collect.getInstance()).sendBroadcast(intent);
                                Timber.i("######## send org.smap.smapTask.refresh from smapMain");  // smap
                            }
                        };
                mProgressDialog.setTitle(getString(R.string.downloading_data));
                mProgressDialog.setMessage(mProgressMsg);
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel), loadingButtonListener);
                return mProgressDialog;
            case ALERT_DIALOG:
                mAlertDialog = new AlertDialog.Builder(this).create();
                mAlertDialog.setMessage(mAlertMsg);
                mAlertDialog.setTitle(getString(R.string.smap_get_tasks));
                DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                };
                mAlertDialog.setCancelable(false);
                mAlertDialog.setButton(getString(R.string.ok), quitListener);
                mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
                return mAlertDialog;
            case PASSWORD_DIALOG:

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                final AlertDialog passwordDialog = builder.create();
                final SharedPreferences adminPreferences = this.getSharedPreferences(
                        AdminPreferencesActivity.ADMIN_PREFERENCES, 0);

                passwordDialog.setTitle(getString(R.string.enter_admin_password));
                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                input.setTransformationMethod(PasswordTransformationMethod
                        .getInstance());
                passwordDialog.setView(input, 20, 10, 20, 10);

                passwordDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                        getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                String value = input.getText().toString();
                                String pw = adminPreferences.getString(
                                        AdminKeys.KEY_ADMIN_PW, "");
                                if (pw.compareTo(value) == 0) {
                                    Intent i = new Intent(getApplicationContext(),
                                            AdminPreferencesActivity.class);
                                    startActivity(i);
                                    input.setText("");
                                    passwordDialog.dismiss();
                                } else {
                                    Toast.makeText(
                                            SmapMain.this,
                                            getString(R.string.admin_password_incorrect),
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        });

                passwordDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                        getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                input.setText("");
                                return;
                            }
                        });

                passwordDialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                return passwordDialog;
        }
        return null;
    }

    /*
     * Forms Downloading Overrides
     */
    @Override
    public void formsDownloadingComplete(HashMap<FormDetails, String> result) {
        // TODO Auto-generated method stub
        // Ignore formsDownloading is called synchronously from taskDownloader
    }

    @Override
    public void progressUpdate(String currentFile, int progress, int total) {
        // TODO Auto-generated method stub
        mProgressMsg = getString(R.string.fetching_file, currentFile, String.valueOf(progress), String.valueOf(total));
        if(mProgressDialog != null) {
            mProgressDialog.setMessage(mProgressMsg);
        }
    }

    @Override
    public void formsDownloadingCancelled() {
       // ignore
    }

    /*
     * Task Download overrides
     */
    @Override
    // Download tasks progress update
    public void progressUpdate(String progress) {
        if(mProgressMsg != null && mProgressDialog != null) {
            mProgressMsg = progress;
            mProgressDialog.setMessage(mProgressMsg);
        }
    }

    public void taskDownloadingComplete(HashMap<String, String> result) {

        Timber.i("Complete - Send intent");

        // Refresh task list
        //Intent intent = new Intent("org.smap.smapTask.refresh");
        //intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        //LocalBroadcastManager.getInstance(Collect.getInstance()).sendBroadcast(intent);
        //Timber.i("######## send org.smap.smapTask.refresh from smapMain2");  // smap

        try {
            dismissDialog(PROGRESS_DIALOG);
            removeDialog(PROGRESS_DIALOG);
        } catch (Exception e) {
            // tried to close a dialog not open. don't care.
        }
        try {
            dismissDialog(ALERT_DIALOG);
            removeDialog(ALERT_DIALOG);
        } catch (Exception e) {
            // tried to close a dialog not open. don't care.
        }

        if (result != null) {
            StringBuilder message = new StringBuilder();
            Set<String> keys = result.keySet();
            Iterator<String> it = keys.iterator();

            while (it.hasNext()) {
                String key = it.next();
                if (key.equals("err_not_enabled")) {
                    message.append(this.getString(R.string.smap_tasks_not_enabled));
                } else if (key.equals("err_no_tasks")) {
                    // No tasks is fine, in fact its the most common state
                    //message.append(this.getString(R.string.smap_no_tasks));
                } else {
                    message.append(key + " - " + result.get(key) + "\n\n");
                }
            }

            mAlertMsg = message.toString().trim();
            if (mAlertMsg.length() > 0) {
                try {
                    showDialog(ALERT_DIALOG);
                } catch (Exception e) {
                    // Tried to show a dialog but the activity may have been closed don't care
                    // However presumably this dialog showing should be replaced by use of progress bar
                }
            }

        }
    }

    /*
     * Uploading overrides
     */
    @Override
    public void uploadingComplete(HashMap<String, String> result) {
        // TODO Auto-generated method stub

    }

    @Override
    public void progressUpdate(int progress, int total) {
        mAlertMsg = getString(R.string.sending_items, String.valueOf(progress), String.valueOf(total));
        mProgressDialog.setMessage(mAlertMsg);
    }

    @Override
    public void authRequest(Uri url, HashMap<String, String> doneSoFar) {
        // TODO Auto-generated method stub

    }

    /*
     * NFC Reading Overrides
     */


    /**
     * @param activity The corresponding {@link Activity} requesting to stop the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopNFCDispatch(final Activity activity, NfcAdapter adapter) {

        if (adapter != null) {
            adapter.disableForegroundDispatch(activity);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNFCIntent(intent);
    }

    /*
     * NFC detected
     */
    private void handleNFCIntent(Intent intent) {

        if (nfcTriggersList != null && nfcTriggersList.size() > 0) {
            Timber.i("tag discovered");
            String action = intent.getAction();
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            mReadNFC = new NdefReaderTask();
            mReadNFC.setDownloaderListener(this);
            mReadNFC.execute(tag);
        } else {
            Toast.makeText(
                    this,
                    R.string.smap_no_tasks_nfc,
                    Toast.LENGTH_LONG).show();
        }

    }


    @Override
    public void readComplete(String result) {

        boolean foundTask = false;

        if (nfcTriggersList != null) {
            for (NfcTrigger trigger : nfcTriggersList) {
                if (trigger.uid.equals(result)) {
                    foundTask = true;

                    Intent i = new Intent();
                    i.setAction("startTask");
                    i.putExtra("position", trigger.position);
                    sendBroadcast(i);

                    Toast.makeText(
                            SmapMain.this,
                            getString(R.string.smap_starting_task_from_nfc, result),
                            Toast.LENGTH_LONG).show();

                    break;
                }
            }
        }
        if (!foundTask) {
            Toast.makeText(
                    SmapMain.this,
                    getString(R.string.smap_no_matching_tasks_nfc, result),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if(requestCode == COMPLETE_FORM && intent != null) {
            String instanceId = intent.getStringExtra("instanceid");
            String formStatus = intent.getStringExtra("status");
            String formURI = intent.getStringExtra("uri");

            formCompleted(instanceId, formStatus, formURI);
        }
    }

    /*
     * Copied from Collect Main Menu Activity
     */
    private boolean loadSharedPreferencesFromFile(File src) {
        // this should probably be in a thread if it ever gets big
        boolean res = false;
        ObjectInputStream input = null;
        try {
            input = new ObjectInputStream(new FileInputStream(src));
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(
                    this).edit();
            prefEdit.clear();
            // first object is preferences
            Map<String, ?> entries = (Map<String, ?>) input.readObject();

            AutoSendPreferenceMigrator.migrate(entries);

            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean) {
                    prefEdit.putBoolean(key, (Boolean) v);
                } else if (v instanceof Float) {
                    prefEdit.putFloat(key, (Float) v);
                } else if (v instanceof Integer) {
                    prefEdit.putInt(key, (Integer) v);
                } else if (v instanceof Long) {
                    prefEdit.putLong(key, (Long) v);
                } else if (v instanceof String) {
                    prefEdit.putString(key, ((String) v));
                }
            }
            prefEdit.apply();
            //AuthDialogUtility.setWebCredentialsFromPreferences();  // Looks like this is no longer needed

            // second object is admin options
            SharedPreferences.Editor adminEdit = getSharedPreferences(AdminPreferencesActivity.ADMIN_PREFERENCES,
                    0).edit();
            adminEdit.clear();
            // first object is preferences
            Map<String, ?> adminEntries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : adminEntries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean) {
                    adminEdit.putBoolean(key, (Boolean) v);
                } else if (v instanceof Float) {
                    adminEdit.putFloat(key, (Float) v);
                } else if (v instanceof Integer) {
                    adminEdit.putInt(key, (Integer) v);
                } else if (v instanceof Long) {
                    adminEdit.putLong(key, (Long) v);
                } else if (v instanceof String) {
                    adminEdit.putString(key, ((String) v));
                }
            }
            adminEdit.apply();

            res = true;
        } catch (IOException | ClassNotFoundException e) {
            Timber.e(e, "Exception while loading preferences from file due to : %s ", e.getMessage());
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException ex) {
                Timber.e(ex, "Exception thrown while closing an input stream due to: %s ", ex.getMessage());
            }
        }
        return res;
    }

    /*
     * The user has selected an option to edit / complete a task
     * If the activity has been paused then a task has already been launched so ignore
     * Unless this request comes not from a user click but from code in which case force the launch
     */
    public void completeTask(TaskEntry entry, boolean force) {

        if(!mPaused || force) {
            String surveyNotes = null;
            String formPath = new StoragePathProvider().getDirPath(StorageSubdirectory.FORMS) + entry.taskForm;
            String instancePath = entry.instancePath;
            long taskId = entry.id;
            String status = entry.taskStatus;

            // set the adhoc location
            boolean canUpdate = Utilities.canComplete(status);
            boolean isSubmitted = Utilities.isSubmitted(status);
            boolean isSelfAssigned = Utilities.isSelfAssigned(status);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean reviewFinal = sharedPreferences.getBoolean(GeneralKeys.KEY_SMAP_REVIEW_FINAL, true);

            if (isSubmitted) {
                Toast.makeText(
                        SmapMain.this,
                        getString(R.string.smap_been_submitted),
                        Toast.LENGTH_LONG).show();
            } else if (!canUpdate && reviewFinal) {
                // Show a message if this task is read only
                if(isSelfAssigned) {
                    Toast.makeText(
                            SmapMain.this,
                            getString(R.string.smap_self_select),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(
                            SmapMain.this,
                            getString(R.string.read_only),
                            Toast.LENGTH_LONG).show();
                }
            } else if (!canUpdate && !reviewFinal) {
                // Show a message if this task is read only and cannot be reviewed
                Toast.makeText(
                        SmapMain.this,
                        getString(R.string.no_review),
                        Toast.LENGTH_LONG).show();
            }

            // Open the task if it is editable or reviewable
            if ((canUpdate || reviewFinal) && !isSubmitted && !isSelfAssigned) {
                // Get the provider URI of the instance
                String where = InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH + "=?";
                String[] whereArgs = {
                        instancePath
                };

                Timber.i("Complete Task: " + entry.id + " : " + entry.name + " : "
                        + entry.taskStatus + " : " + instancePath);

                Cursor cInstanceProvider = Collect.getInstance().getContentResolver().query(InstanceProviderAPI.InstanceColumns.CONTENT_URI,
                        null, where, whereArgs, null);

                if (entry.repeat) {
                    entry.instancePath = duplicateInstance(formPath, entry.instancePath, entry);
                }

                if (cInstanceProvider.moveToFirst()) {
                    long idx = cInstanceProvider.getLong(cInstanceProvider.getColumnIndex(InstanceProviderAPI.InstanceColumns._ID));
                    if (idx > 0) {
                        Uri instanceUri = ContentUris.withAppendedId(InstanceProviderAPI.InstanceColumns.CONTENT_URI, idx);
                        surveyNotes = cInstanceProvider.getString(
                                cInstanceProvider.getColumnIndex(InstanceProviderAPI.InstanceColumns.T_SURVEY_NOTES));
                        // Start activity to complete form

                        // Use an explicit intent
                        Intent i = new Intent(this, org.odk.collect.android.activities.FormEntryActivity.class);
                        i.setData(instanceUri);

                        //Intent i = new Intent(Intent.ACTION_EDIT, instanceUri);

                        i.putExtra(FormEntryActivity.KEY_TASK, taskId);
                        i.putExtra(FormEntryActivity.KEY_SURVEY_NOTES, surveyNotes);
                        i.putExtra(FormEntryActivity.KEY_CAN_UPDATE, canUpdate);
                        i.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.EDIT_SAVED);
                        if (entry.formIndex != null) {
                            FormRestartDetails frd = new FormRestartDetails();
                            frd.initiatingQuestion = entry.formIndex;
                            frd.launchedFormStatus = entry.formStatus;
                            frd.launchedFormInstanceId = entry.instanceId;
                            frd.launchedFormURI = entry.formURI;
                            Collect.getInstance().setFormRestartDetails(frd);
                        }
                        if (instancePath != null) {    // TODO Don't think this is needed
                            i.putExtra(FormEntryActivity.KEY_INSTANCEPATH, instancePath);
                        }
                        startActivityForResult(i, COMPLETE_FORM);

                        // If More than one instance is found pointing towards a single file path then report the error and delete the extrat
                        int instanceCount = cInstanceProvider.getCount();
                        if (instanceCount > 1) {
                            Timber.e(new Exception("Unique instance not found: deleting extra, count is:" +
                                    cInstanceProvider.getCount()));
                            /*
                            cInstanceProvider.moveToNext();
                            while(!cInstanceProvider.isAfterLast()) {

                                Long id = cInstanceProvider.getLong(cInstanceProvider.getColumnIndex(InstanceProviderAPI.InstanceColumns._ID));
                                Uri taskUri = Uri.withAppendedPath(InstanceProviderAPI.InstanceColumns.CONTENT_URI, id.toString());
                                Collect.getInstance().getContentResolver().delete(taskUri, null, null);

                                cInstanceProvider.moveToNext();
                            }
                            */
                        }
                    }
                } else {
                    Timber.e(new Exception("Task not found for instance path:" + instancePath));
                }

                cInstanceProvider.close();
            }
        } else {
            Timber.i("##################: Task launch blocked");
        }

    }

    /*
     * The user has selected an option to edit / complete a form
     * The force parameter can be used to force launching of the new form ven with the smap activity is paused
     */
    public void completeForm(TaskEntry entry, boolean force) {
        if(!mPaused || force) {
            Uri formUri = ContentUris.withAppendedId(FormsProviderAPI.FormsColumns.CONTENT_URI, entry.id);

            // Use an explicit intent
            Intent i = new Intent(this, org.odk.collect.android.activities.FormEntryActivity.class);
            i.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.EDIT_SAVED);
            i.setData(formUri);
            startActivityForResult(i, COMPLETE_FORM);
        } else {
            Timber.i("################# form launch blocked");
        }
    }

    /*
     * respond to completion of a form
     */
    public void formCompleted(String instanceId, String formStatus, String formURI) {
        Timber.i("Form completed");
        FormLaunchDetail fld = Collect.getInstance().popFromFormStack();
        TaskEntry te = new TaskEntry();
        if(fld != null) {
            if(fld.id > 0) {
                // Start a form
                te.id = fld.id;

                SnackbarUtils.showLongSnackbar(findViewById(R.id.llParent),
                        Collect.getInstance().getString(R.string.smap_starting_form, fld.formName));
                //Toast.makeText(
                //        SmapMain.this,
                //        getString(R.string.smap_starting_form, fld.formName),
                //        Toast.LENGTH_LONG).show();

                completeForm(te, true);
            } else if(fld.instancePath != null) {
                // Start a task or saved instance
                te.id = 0;
                te.instancePath = fld.instancePath;
                te.taskStatus = Utilities.STATUS_T_ACCEPTED;
                te.repeat = false;
                te.formIndex = fld.formIndex;
                te.instanceId = instanceId;
                te.formStatus = formStatus;
                te.formURI = formURI;

                SnackbarUtils.showLongSnackbar(findViewById(R.id.pager),
                        Collect.getInstance().getString(R.string.smap_restarting_form, fld.formName));

                //Toast.makeText(
                //        SmapMain.this,
                //        getString(R.string.smap_restarting_form, fld.formName),
                //        Toast.LENGTH_LONG).show();
                completeTask(te, true);
            }
        }
    }

    /*
     * Duplicate the instance
     * Call this if the instance repeats
     */
    public String duplicateInstance(String formPath, String originalPath, TaskEntry entry) {
        String newPath = null;

        // 1. Get a new instance path
        ManageForm mf = new ManageForm();
        newPath = mf.getInstancePath(formPath, entry.assId);

        // 2. Duplicate the instance entry and get the new path
        Utilities.duplicateTask(originalPath, newPath, entry);

        // 3. Copy the instance files
        Utilities.copyInstanceFiles(originalPath, newPath);
        return newPath;
    }

    /*
     * Get the tasks shown on the map
     */
    public List<TaskEntry> getTasks() {
        return mTasks;
    }

    /*
     * Manage location triggers
     */
    public void setLocationTriggers(List<TaskEntry> data) {

        mTasks = data;
        nfcTriggersList = new ArrayList<NfcTrigger>();

        /*
         * Set NFC triggers
         */

        int position = 0;
        for (TaskEntry t : data) {
            if (t.type.equals("task") && t.locationTrigger != null && t.locationTrigger.trim().length() > 0
                    && t.taskStatus.equals(Utilities.STATUS_T_ACCEPTED)) {
                nfcTriggersList.add(new NfcTrigger(t.id, t.locationTrigger, position));
            }
            position++;
        }

    }

    /*
     * Update fragments that use data sourced from the loader that called this method
     */
    public void updateData(MapEntry data) {
        mData = data;
        formManagerList.setData(data);
        taskManagerList.setData(data);
        taskManagerMap.setData(data);
        if(data != null) {
            setLocationTriggers(data.tasks);      // NFC and geofence triggers
        }
    }

    public MapEntry getData() {
        return mData;
    }

    protected class MainTaskListener extends BroadcastReceiver {

        private SmapMain mActivity = null;

        public MainTaskListener(SmapMain activity) {
            mActivity = activity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            Timber.i("Intent received: " + intent.getAction());

            if (intent.getAction().equals("startTask")) {

                int position = intent.getIntExtra("position", -1);
                if (position >= 0) {
                    TaskEntry entry = (TaskEntry) mTasks.get(position);

                    mActivity.completeTask(entry, true);
                }
            }
        }
    }

    /*
     * The user has chosen to exit the application
     */
    public void exit() {
        boolean continueTracking = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(GeneralKeys.KEY_SMAP_EXIT_TRACK_MENU, false);
        if(!continueTracking) {
            GeneralSharedPreferences.getInstance().save(GeneralKeys.KEY_SMAP_USER_LOCATION, false);
            this.finish();
        } else {
            SnackbarUtils.showLongSnackbar(findViewById(R.id.pager), Collect.getInstance().getString(R.string.smap_continue_tracking));
        }

    }

    /*
     * Get / Set task loader
     */
    public void setTaskLoader(MapDataLoader taskLoader) {
       mTaskLoader = taskLoader;
    }
    public MapDataLoader getTaskLoader() {
        return mTaskLoader;
    }

    @Override
    protected void onPause() {
        mPaused = true;
        super.onPause();
    }

    /*
     * Migration to scoped storage
     */
    /**
     * notifies us that something changed
     */
    private class MyContentObserver extends ContentObserver {

        MyContentObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            handler.sendEmptyMessage(0);
        }
    }

    static class IncomingHandler extends Handler {
    }

    public void onStorageMigrationBannerDismiss(View view) {
        storageMigrationBanner.setVisibility(View.GONE);
        storageMigrationRepository.clearResult();
    }

    public void onStorageMigrationBannerLearnMoreClick(View view) {
        DialogUtils.showIfNotShowing(StorageMigrationDialog.create(savedCount), getSupportFragmentManager());
        getContentResolver().unregisterContentObserver(contentObserver);
    }

    private void onStorageMigrationFinish(StorageMigrationResult result) {
        if (result == StorageMigrationResult.SUCCESS) {
            DialogUtils.dismissDialog(StorageMigrationDialog.class, getSupportFragmentManager());
            displayBannerWithSuccessStorageMigrationResult();
        } else {
            DialogUtils
                    .showIfNotShowing(StorageMigrationDialog.create(savedCount), getSupportFragmentManager())
                    .handleMigrationError(result);
        }
    }

    private void setUpStorageMigrationBanner() {
        if (!storageStateProvider.isScopedStorageUsed()) {
            displayStorageMigrationBanner();
        }
    }

    private void displayStorageMigrationBanner() {
        storageMigrationBanner.setVisibility(View.VISIBLE);
        storageMigrationBannerText.setText(R.string.scoped_storage_banner_text);
        storageMigrationBannerLearnMoreButton.setVisibility(View.VISIBLE);
        storageMigrationBannerDismissButton.setVisibility(View.GONE);
    }

    private void displayBannerWithSuccessStorageMigrationResult() {
        storageMigrationBanner.setVisibility(View.VISIBLE);
        storageMigrationBannerText.setText(R.string.storage_migration_completed);
        storageMigrationBannerLearnMoreButton.setVisibility(View.GONE);
        storageMigrationBannerDismissButton.setVisibility(View.VISIBLE);
    }
}
