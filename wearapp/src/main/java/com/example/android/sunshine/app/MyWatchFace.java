/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "sunshine watch";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {

        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private static final String WEATHER_PATH = "/weather";

        private static final String WEATHER_TEMP_HIGH_KEY = "weather_temp_high_key";

        private static final String WEATHER_TEMP_LOW_KEY = "weather_temp_low_key";

        private static final String WEATHER_TEMP_ICON_KEY = "weather_temp_icon_key";


        final Handler mUpdateTimeHandler = new EngineHandler(this);

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;

        Paint mWhiteBackgroundPaint;

        Paint mTextPaintTime;

        Paint mTextPaintDate;

        Paint mTextPaintHighTemp;

        Paint mTextPaintLowTemp;

        boolean mAmbient;

        Calendar mCalendar;

        SimpleDateFormat mDateFormat;

        Date mDate;

        String weatherTempHigh = "29°";

        String weatherTempLow = "21°";

        Bitmap mWeatherIcon = null;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat.setCalendar(mCalendar);
            }
        };

        int mTapCount;

        float mXOffset;

        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        GoogleApiClient googleApiClient;

        DataApi.DataListener dataListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
                Log.d(TAG, "onDataChanged(): " + dataEvents);

                for (DataEvent event : dataEvents) {
                    Log.d(TAG, "Unknown data event type   " + event.getType());
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        String path = event.getDataItem().getUri().getPath();
                        Log.d(TAG, "Path: " + path);
                        if (WEATHER_PATH.equals(path)) {
                            try {
                                DataMapItem dataMapItem = DataMapItem
                                        .fromDataItem(event.getDataItem());
                                weatherTempHigh = dataMapItem.getDataMap()
                                        .getString(WEATHER_TEMP_HIGH_KEY);
                                weatherTempLow = dataMapItem.getDataMap()
                                        .getString(WEATHER_TEMP_LOW_KEY);
                                Asset weatherIcon = dataMapItem.getDataMap()
                                        .getAsset(WEATHER_TEMP_ICON_KEY);
                                loadBitmapIntoWeatherIcon(googleApiClient, weatherIcon);
                            } catch (Exception e) {
                                Log.d(TAG, "Exception ", e);
                                mWeatherIcon = null;
                            }
                        }
                    }
                }
            }
        };

        private void loadBitmapIntoWeatherIcon(GoogleApiClient apiClient, Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }

            Wearable.DataApi.getFdForAsset(apiClient, asset).setResultCallback(
                    new ResultCallback<DataApi.GetFdForAssetResult>() {
                        @Override
                        public void onResult(
                                @NonNull DataApi.GetFdForAssetResult getFdForAssetResult) {
                            InputStream assetInputStream = getFdForAssetResult.getInputStream();
                            if (assetInputStream == null) {
                                Log.w(TAG, "Requested an unknown Asset.");
                            }
                            mWeatherIcon = BitmapFactory.decodeStream(assetInputStream);
                        }
                    });
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();

            mWeatherIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mWhiteBackgroundPaint = new Paint();
            mWhiteBackgroundPaint.setColor(resources.getColor(R.color.white));

            mTextPaintTime = createTextPaint(resources.getColor(R.color.white_text), "roboto_thin.ttf");
            mTextPaintDate = createTextPaint(resources.getColor(R.color.secondary_white_text), "RobotoCondensed-Regular.ttf");
            mTextPaintHighTemp = createTextPaint(resources.getColor(R.color.primary_text), "Roboto-Light.ttf");
            mTextPaintLowTemp = createTextPaint(resources.getColor(R.color.secondary_text), "Roboto-Light.ttf");

            mCalendar = Calendar.getInstance();
            mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
            mDate = new Date();

            googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            Log.d(TAG, "onConnected: Successfully connected to Google API client");
                            Wearable.DataApi.addListener(googleApiClient, dataListener);

                            Wearable.NodeApi.getConnectedNodes(googleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                                @Override
                                public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                                    if (getConnectedNodesResult.getStatus().isSuccess() && getConnectedNodesResult.getNodes().size() > 0) {
                                        String remoteNodeId = getConnectedNodesResult.getNodes().get(0).getId();
                                        Wearable.MessageApi.sendMessage(googleApiClient, remoteNodeId, "/provide-weather-data", null);
                                    }
                                }
                            });
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            Log.d(TAG, "onConnectionSuspended");
                        }
                    })
                    .addOnConnectionFailedListener(
                            new GoogleApiClient.OnConnectionFailedListener() {
                                @Override
                                public void onConnectionFailed(ConnectionResult connectionResult) {
                                    Log.d(TAG,
                                            "onConnectionFailed(): Failed to connect, with result : "
                                                    + connectionResult);
                                }
                            })
                    .build();
            googleApiClient.connect();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, String font) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setAntiAlias(true);
            if (font != null) {
                paint.setTypeface(Typeface.createFromAsset(getAssets(), font));
            } else {
                paint.setTypeface(NORMAL_TYPEFACE);
            }
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat.setCalendar(mCalendar);
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mYOffset = resources
                    .getDimension(
                            isRound ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaintTime.setTextSize(textSize);
            mTextPaintTime.setTextAlign(Paint.Align.CENTER);

            mTextPaintDate.setTextSize(textSize / 2.5f);
            mTextPaintDate.setTextAlign(Paint.Align.CENTER);

            mTextPaintHighTemp.setTextSize(textSize);
            mTextPaintLowTemp.setTextSize(textSize / 2);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaintTime.setAntiAlias(!inAmbientMode);
                    mTextPaintDate.setAntiAlias(!inAmbientMode);
                    mTextPaintHighTemp.setAntiAlias(!inAmbientMode);
                    mTextPaintLowTemp.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
//                    mTextPaintHighTemp.setColor(resources.getColor(mTapCount % 2 == 0 ?
//                            R.color.theme_text : R.color.black_text));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                canvas.drawRect(0, bounds.height() / 2, bounds.width(), bounds.height(),
                        mWhiteBackgroundPaint);
            }

//            update text sizes

//            update time and calendar
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            String timeText = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                            mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));

            int centerX = bounds.width() / 2;
            int centerY = bounds.height() / 2;
            Rect textBounds = new Rect();

            mTextPaintTime.getTextBounds(timeText, 0, timeText.length(), textBounds);
            canvas.drawText(timeText, centerX, mYOffset, mTextPaintTime);

            String dateText = mDateFormat.format(mDate).toUpperCase();
            mTextPaintDate.getTextBounds(dateText, 0, dateText.length(), textBounds);
            canvas.drawText(dateText, centerX, mYOffset + mTextPaintDate.getTextSize() + 10,
                    mTextPaintDate);

            if (!mAmbient) {
                if (weatherTempHigh != null && weatherTempLow != null) {
                    int textMargin = 15;

                    String text = weatherTempHigh;
                    mTextPaintHighTemp.getTextBounds(text, 0, text.length(), textBounds);
                    canvas.drawText(text, centerX + 10, centerY * 1.5f - 10,
                            mTextPaintHighTemp);

                    text = weatherTempLow;
                    int highTempWidth = textBounds.width();
                    mTextPaintLowTemp.getTextBounds(text, 0, text.length(), textBounds);
                    canvas.drawText(text, centerX + 10 + (highTempWidth - textBounds.width()) / 2,
                            centerY * 1.5f + textBounds.height() + textMargin,
                            mTextPaintLowTemp);

                    if (mWeatherIcon != null) {
                        // draw weather icon
                        canvas.drawBitmap(mWeatherIcon,
                                centerX - mWeatherIcon.getWidth() - 15,
                                centerY + 15, null);

                    }

                } else {
                    // draw temperature high
                    String text = getString(R.string.weather_data_not_available);
                    mTextPaintLowTemp.getTextBounds(text, 0, text.length(), textBounds);
                    canvas.drawText(text, centerX - textBounds.width() / 2, centerY * 1.5f,
                            mTextPaintLowTemp);

                }
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
