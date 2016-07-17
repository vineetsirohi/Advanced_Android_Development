package com.example.android.sunshine.app;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import com.example.android.sunshine.app.sync.SyncWithWear;

import android.util.Log;

/**
 * Created by vineet on 17-Jul-16.
 */
public class WearRequestForWeatherDataService extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        Log.d(WearRequestForWeatherDataService.class.getSimpleName(), "onMessageReceived: ");

        new SyncWithWear().syncWithWear(getBaseContext());
    }
}
