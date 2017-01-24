package com.example.android.sunshine.app.wear;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;

public class WearServiceListener extends WearableListenerService implements DataApi.DataListener, MessageApi.MessageListener {


    private static final int WEATHER_ID = 0;
    private static final int MAX_TEMP = 1;
    private static final int MIN_TEMP = 2;
    private static final String WEATHER_REQUEST = "/weather-request";
    private static final String WEATHER_INFO = "/weather-info";
    private static final String TEMP_HIGH = "w_high";
    private static final String TEMP_LOW = "w_low";
    private static final String ICON = "w_icon";
    private static GoogleApiClient googleApiClient;

    private static final String[] WEATHER_PROJECTION = new String[]{
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };



    public WearServiceListener() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initializeGoogleApiClient();
        sendUpdatedDataToWear();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        if (messageEvent.getPath().equals(WEATHER_REQUEST)) {
            SunshineSyncAdapter.syncImmediately(getApplicationContext());
        }
    }

    private void initializeGoogleApiClient() {
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle bundle) {
                    Wearable.DataApi.addListener(googleApiClient, WearServiceListener.this);
                    Wearable.MessageApi.addListener(googleApiClient, WearServiceListener.this);
                    sendData();
                }
                @Override
                public void onConnectionSuspended(int i) {
                }
            }).addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult connectionResult) {
                }
            }).addApi(Wearable.API).build();
        }
    }

    private void sendUpdatedDataToWear() {
        if (!googleApiClient.isConnected()) {
            googleApiClient.connect();
        } else {
            sendData();
        }

    }

    private void sendData() {
        Context context = getApplicationContext();
        String locationQuery = Utility.getPreferredLocation(context);
        Cursor c = context.getContentResolver().query(WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis()), WEATHER_PROJECTION, null, null, null);
        if (c != null && c.moveToFirst()) {
            if (googleApiClient.isConnected()) {
                PutDataMapRequest putRequest = PutDataMapRequest.create(WEATHER_INFO);
                putRequest.getDataMap().putString(TEMP_HIGH, Utility.temperatureFormat(getApplicationContext(), c.getDouble(MAX_TEMP)));
                putRequest.getDataMap().putAsset(ICON, createAsset(BitmapFactory.decodeResource(context.getResources(), Utility.getIconResourceForWeatherCondition(c.getInt(WEATHER_ID)))));
                putRequest.getDataMap().putString(TEMP_LOW, Utility.temperatureFormat(getApplicationContext(), c.getDouble(MIN_TEMP)));
                PutDataRequest putDataRequest = putRequest.asPutDataRequest();
                Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        googleApiClient.disconnect();
                    }
                });
            }
        }
    }

    private static Asset createAsset(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream);
        return Asset.createFromBytes(outputStream.toByteArray());
    }
}
