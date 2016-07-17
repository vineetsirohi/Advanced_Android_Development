package com.example.android.sunshine.app.sync;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import com.bumptech.glide.Glide;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Created by vineet on 17-Jul-16.
 */
public class SyncWithWear {

    private static final int IMG_WIDTH = 100;

    private static final int IMG_HEIGHT = 100;

    public final String LOG_TAG = SyncWithWear.class.getSimpleName();

    public void syncWithWear(Context context) {
        String locationQuery = Utility.getPreferredLocation(context);
        Uri weatherUri = WeatherContract.WeatherEntry
                .buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());
        Cursor cursor = context.getContentResolver()
                .query(weatherUri, SunshineSyncAdapter.NOTIFY_WEATHER_PROJECTION, null, null, null);

        if (cursor.moveToFirst()) {
            int weatherId = cursor.getInt(SunshineSyncAdapter.INDEX_WEATHER_ID);
            double high = cursor.getDouble(SunshineSyncAdapter.INDEX_MAX_TEMP);
            double low = cursor.getDouble(SunshineSyncAdapter.INDEX_MIN_TEMP);

            Resources resources = context.getResources();
            int artResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
            String artUrl = Utility.getArtUrlForWeatherCondition(context, weatherId);
            Log.d(LOG_TAG, "syncWithWear: artUrl: " + artUrl);

            // Retrieve the large icon
            Bitmap largeIcon;
            try {
                largeIcon = Glide.with(context)
                        .load(artUrl)
                        .asBitmap()
                        .error(artResourceId)
                        .fitCenter()
                        .into(IMG_WIDTH, IMG_HEIGHT)
                        .get();
            } catch (InterruptedException | ExecutionException e) {
                Log.d(LOG_TAG, "Error retrieving large icon from " + artUrl, e);
                largeIcon = BitmapFactory.decodeResource(resources, artResourceId);
            }

            syncWithWear(context, largeIcon, high, low);

        }
        cursor.close();
    }


    private void syncWithWear(Context context, Bitmap largeIcon, double high, double low) {

        final String WEATHER_PATH = "/weather";
        final String WEATHER_TEMP_HIGH_KEY = "weather_temp_high_key";
        final String WEATHER_TEMP_LOW_KEY = "weather_temp_low_key";
        final String WEATHER_TEMP_ICON_KEY = "weather_temp_icon_key";

        final GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Log.v(LOG_TAG, "Google API Client was connected");
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.v(LOG_TAG, "Connection to Google API client was suspended");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(LOG_TAG, "Connection to Google API client has failed");
                    }
                })
                .build();
        mGoogleApiClient.connect();

        Asset asset = toAsset(Bitmap.createScaledBitmap(largeIcon, IMG_WIDTH, IMG_HEIGHT, true));

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_PATH);
        putDataMapRequest.getDataMap()
                .putString(WEATHER_TEMP_HIGH_KEY, Utility.formatTemperature(context, high));
        putDataMapRequest.getDataMap()
                .putString(WEATHER_TEMP_LOW_KEY, Utility.formatTemperature(context, low));
        putDataMapRequest.getDataMap().putAsset(WEATHER_TEMP_ICON_KEY, asset);
        putDataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (dataItemResult.getStatus().isSuccess()) {
                            Log.d(LOG_TAG, "Weather info successfully sent!");
                        } else {
                            Log.d(LOG_TAG, "Sending weather info failed!");
                        }
                        mGoogleApiClient.disconnect();
                    }
                });
    }

    private static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}


