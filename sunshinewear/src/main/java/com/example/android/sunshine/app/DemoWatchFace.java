package com.example.android.sunshine.app;

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
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class DemoWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final String TEMP_LOW = "w_low";
    private static final String ICON = "w_icon";
    private static final String WEATHER_REQUEST = "/weather-request";
    private static final String WEATHER_INFO = "/weather-info";
    private static final String TEMP_HIGH = "w_high";
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final long UPDATE_RATE = TimeUnit.SECONDS.toMillis(1);
    private static final int UPDATE_TIME = 0;
    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<DemoWatchFace.Engine> weakReference;
        @Override
        public void handleMessage(Message msg) {
            DemoWatchFace.Engine engine = weakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case UPDATE_TIME:
                        engine.handleUpdateMessage();
                        break;
                }
            }
        }
        public EngineHandler(DemoWatchFace.Engine reference) {
            weakReference = new WeakReference<>(reference);
        }
    }
    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {
        Paint hourpaint,minutePaint,backgroundPaintColor,datePaint,linePaint,highTempPaint,lowTempPaint;
        Bitmap weatherStatus;
        final Handler mUpdateHandler = new EngineHandler(this);
        String defaultHighTemp = "0°",defaultLowTemp = "0°";
        GoogleApiClient mGoogleClient;
        SimpleDateFormat dateFormat;
        String hourFormater,minuteFormater;
        final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        Calendar calendar;
        boolean iSLowBitAmbient,IsAmbient, isWeatherInfoAvailable,mRegisteredTimeZoneReceiver=false;
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(DemoWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = DemoWatchFace.this.getResources();
            backgroundPaintColor = new Paint();
            backgroundPaintColor.setColor(ContextCompat.getColor(getApplicationContext(),R.color.primary));
            hourpaint =createPaintObject(Color.WHITE,BOLD_TYPEFACE);
            minutePaint =createPaintObject(Color.WHITE,NORMAL_TYPEFACE);
            datePaint =createPaintObject(Color.BLUE,NORMAL_TYPEFACE);
            highTempPaint =createPaintObject(Color.WHITE,BOLD_TYPEFACE);
            lowTempPaint =createPaintObject(Color.BLUE,NORMAL_TYPEFACE);
            calendar = Calendar.getInstance();
            linePaint = createPaintObject(Color.BLUE,NORMAL_TYPEFACE);
            BitmapFactory.Options option = new BitmapFactory.Options();
            option.inSampleSize = 2;
            weatherStatus = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear, option);


            dateFormat = new SimpleDateFormat(resources.getString(R.string.date_format));
            dateFormat.setTimeZone(calendar.getTimeZone());
            hourFormater = resources.getString(R.string.hour_format);
            minuteFormater = resources.getString(R.string.minute_format);
            isWeatherInfoAvailable = false;
            mGoogleClient = new GoogleApiClient.Builder(getApplicationContext()).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(Wearable.API).build();
        }

        @Override
        public void onDestroy() {
            mUpdateHandler.removeMessages(UPDATE_TIME);
            if (mGoogleClient != null && mGoogleClient.isConnected()) {
                mGoogleClient.disconnect();
            }
            super.onDestroy();
            isWeatherInfoAvailable = false;
        }

        private Paint createPaintObject(int color,Typeface typeface) {
            Paint paint = new Paint();
            if(color==Color.WHITE)
            paint.setColor(color);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                registerBroadCastReceiver();
                if (!mGoogleClient.isConnected()) {
                    mGoogleClient.connect();
                }
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterBroadCast();
            }
            updateTimer();
        }
        private void registerBroadCastReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            DemoWatchFace.this.registerReceiver(timeZoneReceiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
        }
        private void unregisterBroadCast() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            DemoWatchFace.this.unregisterReceiver(timeZoneReceiver);
        }
        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            Resources resource = DemoWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            hourpaint.setTextSize(resource.getDimension(isRound
                    ? R.dimen.round_text_size : R.dimen.wear_text_size));
            minutePaint.setTextSize(resource.getDimension(isRound
                    ? R.dimen.round_text_size : R.dimen.wear_text_size));
            datePaint.setTextSize(resource.getDimension(isRound
                    ? R.dimen.round_small_text : R.dimen.small_text_size));
            highTempPaint.setTextSize(resource.getDimension(isRound
                    ? R.dimen.round_medium_text_size : R.dimen.medium_text));
            lowTempPaint.setTextSize(resource.getDimension(isRound
                    ? R.dimen.round_medium_text_size : R.dimen.medium_text));
        }
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            iSLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (IsAmbient != inAmbientMode) {
                IsAmbient = inAmbientMode;
                if (iSLowBitAmbient) {
                    hourpaint.setAntiAlias(!inAmbientMode);
                    minutePaint.setAntiAlias(!inAmbientMode);
                    datePaint.setAntiAlias(!inAmbientMode);
                    highTempPaint.setAntiAlias(!inAmbientMode);
                    lowTempPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }
            updateTimer();
        }
        private boolean shouldTimerRunning() {
            return isVisible() && !isInAmbientMode();
        }
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    break;
                case TAP_TYPE_TAP:
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), backgroundPaintColor);
            }
            Rect rect = new Rect();
            int lineWidth = bounds.width() / 8;
            int x_cord = bounds.centerX();
            int y_cord = bounds.centerY();
            canvas.drawLine((x_cord - lineWidth), y_cord, (x_cord + lineWidth), y_cord, linePaint);
            String CurrentDate = dateFormat.format(calendar.getTime());
            datePaint.getTextBounds(CurrentDate, 0, CurrentDate.length(), rect);
            int dateYPos = y_cord - rect.height() - 2;
            canvas.drawText(CurrentDate, x_cord - (rect.width() / 2), dateYPos, datePaint);
            calendar.setTimeInMillis(System.currentTimeMillis());
            rect.setEmpty();
            String hour_ = String.format(hourFormater, calendar.get(Calendar.HOUR));
            hourpaint.getTextBounds(hour_, 0, hour_.length(), rect);
            int hourPos = dateYPos - rect.height() + 14;
            canvas.drawText(hour_, x_cord - rect.width(), hourPos, hourpaint);
            canvas.drawText(String.format(minuteFormater, calendar.get(Calendar.MINUTE)), x_cord + 2, hourPos, minutePaint);
            if (isWeatherInfoAvailable) {
                int imageXPos = x_cord / 2 - weatherStatus.getWidth() / 2;
                int imageYPos = y_cord + 10;
                canvas.drawBitmap(weatherStatus, imageXPos, imageYPos, null);
                int tempYPos = imageYPos + (weatherStatus.getHeight() / 2) + 8;
                int tempXPos = x_cord + 10;
                canvas.drawText(defaultHighTemp, tempXPos, tempYPos, highTempPaint);
                rect.setEmpty();
                highTempPaint.getTextBounds(defaultHighTemp, 0, defaultHighTemp.length(), rect);
                canvas.drawText(defaultLowTemp, tempXPos + rect.width() + 1, tempYPos, lowTempPaint);
            }
        }
        private void updateTimer() {
            mUpdateHandler.removeMessages(UPDATE_TIME);
            if (shouldTimerRunning()) {
                mUpdateHandler.sendEmptyMessage(UPDATE_TIME);
            }
        }

        private void handleUpdateMessage() {
            invalidate();
            if (shouldTimerRunning()) {
                mUpdateHandler.sendEmptyMessageDelayed(UPDATE_TIME, UPDATE_RATE
                        - (System.currentTimeMillis() % UPDATE_RATE));
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleClient, this);
            getDataFromDevice();
        }
        private void getDataFromDevice() {
            Wearable.NodeApi.getConnectedNodes(mGoogleClient)
                    .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                        @Override
                        public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                            final List<Node> nodesList = getConnectedNodesResult.getNodes();
                            for (Node node : nodesList) {
                                Wearable.MessageApi.sendMessage(mGoogleClient
                                        , node.getId()
                                        , WEATHER_REQUEST
                                        , new byte[0]).setResultCallback(
                                        new ResultCallback<MessageApi.SendMessageResult>() {
                                            @Override
                                            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                            }
                                        }
                                );
                            }
                        }
                    });
        }
        @Override
        public void onConnectionSuspended(int i) {
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        }
        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    processData(event.getDataItem());
                }
            }
        }


        private void processData(DataItem dataItem) {
            DataMap map = DataMapItem.fromDataItem(dataItem).getDataMap();
            String path = dataItem.getUri().getPath();
            if (path.equals(WEATHER_INFO)) {
                isWeatherInfoAvailable = true;
                defaultHighTemp = map.getString(TEMP_HIGH);
                defaultLowTemp = map.getString(TEMP_LOW);
                Asset asset = map.getAsset(ICON);
                loadBitmap(asset);
                invalidate();
            }
        }
        public void loadBitmap(final Asset asset) {
            Wearable.DataApi.getFdForAsset(mGoogleClient, asset).setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
                @Override
                public void onResult(@NonNull DataApi.GetFdForAssetResult getFdForAssetResult) {
                    weatherStatus = BitmapFactory.decodeStream(getFdForAssetResult.getInputStream());
                    invalidate();
                }
            });
        }
    }
}
