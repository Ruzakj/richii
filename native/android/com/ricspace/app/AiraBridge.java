package com.ricspace.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class AiraBridge {
  static final int NOTIFICATION_PERMISSION_REQUEST = 912;
  private static Activity activity;

  static void attach(Activity host, WebView view) {
    activity = host;
    view.addJavascriptInterface(new AiraBridge(), "RicAiraNative");
  }

  @JavascriptInterface
  public void scheduleCheckin() {
    if (activity == null) return;
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
      return;
    }
    schedule(activity);
  }

  static void schedule(Context context) {
    OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(AiraCheckinWorker.class)
      .setInitialDelay(3, TimeUnit.HOURS)
      .build();
    WorkManager.getInstance(context).enqueueUniqueWork("ric-aira-checkin", ExistingWorkPolicy.REPLACE, work);
  }
}
