package com.ricspace.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.LocationServices;

public class RideLocationService extends Service {
  private static final String CHANNEL_ID = "ric_ride_tracking";
  private static final int NOTIFICATION_ID = 4401;
  private FusedLocationProviderClient locationClient;
  private LocationCallback locationCallback;

  static void start(Context context) {
    ContextCompat.startForegroundService(context, new Intent(context, RideLocationService.class));
  }

  static void stop(Context context) {
    context.stopService(new Intent(context, RideLocationService.class));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    createChannel();
    locationClient = LocationServices.getFusedLocationProviderClient(this);
    locationCallback = new LocationCallback() {
      @Override public void onLocationResult(LocationResult result) {
        if (result == null || result.getLastLocation() == null) return;
        android.location.Location l = result.getLastLocation();
        RideNativeStore.append(RideLocationService.this, l);
        RideBridge.pushLocation(l.getLatitude(), l.getLongitude(), l.getSpeed(), l.getBearing(), l.getAccuracy(), l.getTime());
      }
    };
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(com.ricspace.app.R.drawable.ic_launcher_foreground)
      .setContentTitle("Ride aktif")
      .setContentText("Ric Space sedang merekam lokasi")
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .build();
    if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
    else startForeground(NOTIFICATION_ID, notification);
    requestLocations();
    return START_STICKY;
  }

  @SuppressWarnings("MissingPermission")
  private void requestLocations() {
    if (!RideBridge.hasLocationPermission(this)) return;
    LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
      .setMinUpdateIntervalMillis(1000)
      .setMinUpdateDistanceMeters(1)
      .build();
    locationClient.requestLocationUpdates(request, locationCallback, getMainLooper());
  }

  @Override public void onDestroy() {
    if (locationClient != null && locationCallback != null) locationClient.removeLocationUpdates(locationCallback);
    super.onDestroy();
  }

  @Nullable @Override public IBinder onBind(Intent intent) { return null; }

  private void createChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Ride tracking", NotificationManager.IMPORTANCE_LOW);
      channel.setDescription("Status GPS saat Ride Tracking aktif");
      getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
  }
}
