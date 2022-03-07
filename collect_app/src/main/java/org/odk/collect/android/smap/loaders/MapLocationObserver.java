package org.odk.collect.android.smap.loaders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.maps.model.LatLng;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.smap.fragments.TaskMapFragment;


public class MapLocationObserver extends BroadcastReceiver {

    private TaskMapFragment mMap = null;
    SharedPreferences sharedPreferences = null;

public MapLocationObserver(Context context, TaskMapFragment map) {
    mMap = map;

    LocalBroadcastManager.getInstance(context).registerReceiver(this,
            new IntentFilter("locationChanged"));
  }

  @Override
  public void onReceive(Context context, Intent intent) {
      Location locn = Collect.getInstance().getLocation();
      LatLng point = new LatLng(locn.getLatitude(), locn.getLongitude());
      mMap.updatePath(point);
  }
}
