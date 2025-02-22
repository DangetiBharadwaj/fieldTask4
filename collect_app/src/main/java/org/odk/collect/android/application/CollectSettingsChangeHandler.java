package org.odk.collect.android.application;

import android.content.SharedPreferences;

import org.odk.collect.android.analytics.Analytics;
import org.odk.collect.android.analytics.AnalyticsEvents;
import org.odk.collect.android.backgroundwork.FormUpdateManager;
import org.odk.collect.android.configure.ServerRepository;
import org.odk.collect.android.configure.SettingsChangeHandler;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.preferences.PreferencesProvider;
import org.odk.collect.android.utilities.FileUtils;

import java.io.ByteArrayInputStream;

import static org.odk.collect.android.preferences.GeneralKeys.KEY_EXTERNAL_APP_RECORDING;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_FORM_UPDATE_MODE;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_PERIODIC_FORM_UPDATES_CHECK;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_PROTOCOL;
import static org.odk.collect.android.preferences.GeneralKeys.KEY_SERVER_URL;

public class CollectSettingsChangeHandler implements SettingsChangeHandler {

    private final PropertyManager propertyManager;
    private final FormUpdateManager formUpdateManager;
    private final ServerRepository serverRepository;
    private final Analytics analytics;
    private final PreferencesProvider preferencesProvider;

    public CollectSettingsChangeHandler(PropertyManager propertyManager, FormUpdateManager formUpdateManager, ServerRepository serverRepository, Analytics analytics, PreferencesProvider preferencesProvider) {
        this.propertyManager = propertyManager;
        this.formUpdateManager = formUpdateManager;
        this.serverRepository = serverRepository;
        this.analytics = analytics;
        this.preferencesProvider = preferencesProvider;
    }

    @Override
    public void onSettingChanged(String changedKey, Object newValue) {
        propertyManager.reload();

        if(changedKey == null) {  // smap
            return;
        }

        if (changedKey.equals(KEY_FORM_UPDATE_MODE) || changedKey.equals(KEY_PERIODIC_FORM_UPDATES_CHECK) || changedKey.equals(KEY_PROTOCOL)) {
            formUpdateManager.scheduleUpdates();
        }

        if (changedKey.equals(KEY_SERVER_URL)) {
            serverRepository.save((String) newValue);
        }

        if (changedKey.equals(KEY_EXTERNAL_APP_RECORDING) && !((Boolean) newValue)) {
            SharedPreferences generalSharedPrefs = preferencesProvider.getGeneralSharedPreferences();
            String currentServerUrl = generalSharedPrefs.getString(KEY_SERVER_URL, "");
            String serverHash = FileUtils.getMd5Hash(new ByteArrayInputStream(currentServerUrl.getBytes()));

            analytics.logServerEvent(AnalyticsEvents.INTERNAL_RECORDING_OPT_IN, serverHash);
        }
    }
}
