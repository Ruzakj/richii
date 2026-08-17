package com.ricspace.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class AiraBridge {
  static final int NOTIFICATION_PERMISSION_REQUEST = 912;
  private static Activity activity;

  static void attach(Activity host, WebView view) {
    activity = host;
    view.addJavascriptInterface(new AiraBridge(), "RicAiraNative");
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(host, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(host, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
    } else {
      scheduleTonightTest(host);
    }
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

  static void scheduleTonightTest(Context context) {
    Calendar now = Calendar.getInstance();
    Calendar target = Calendar.getInstance();
    target.set(2026, Calendar.AUGUST, 18, 2, 10, 0);
    long when = target.getTimeInMillis();
    if (when <= now.getTimeInMillis()) return;
    SharedPreferences prefs = context.getSharedPreferences("ric_aira_test", Context.MODE_PRIVATE);
    if (prefs.getBoolean("call_20260818_0210", false)) return;
    Intent intent = new Intent(context, AiraCallReceiver.class).setAction("ric.aira.TEST_20260818_0210");
    PendingIntent alarm = PendingIntent.getBroadcast(context, 210, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (manager == null) return;
    if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, alarm);
    else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, alarm);
    prefs.edit().putBoolean("call_20260818_0210", true).apply();
  }
}
