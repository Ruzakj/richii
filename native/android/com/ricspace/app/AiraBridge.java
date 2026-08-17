package com.ricspace.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;

public final class AiraBridge {
  static final int NOTIFICATION_PERMISSION_REQUEST = 912;
  private static final String ACTION_MORNING_START = "ric.angel.MORNING_START";
  private static final String ACTION_CALL = "ric.angel.CALL";
  private static final String ACTION_END_CALL = "ric.angel.END_CALL";
  private static final String ACTION_MORNING_NOTE = "ric.angel.MORNING_NOTE";
  private static final AtomicInteger REQUESTS = new AtomicInteger(5100);
  private static Activity activity;

  static void attach(Activity host, WebView view) {
    activity = host;
    view.addJavascriptInterface(new AiraBridge(), "RicAiraNative");
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(host, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(host, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
    } else {
      scheduleDailyRoutine(host);
      scheduleTodayTest(host);
    }
  }

  @JavascriptInterface
  public void scheduleCheckin() {
    if (activity == null) return;
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
      return;
    }
    scheduleDailyRoutine(activity);
  }

  static void scheduleDailyRoutine(Context context) {
    Calendar now = Calendar.getInstance();
    Calendar target = Calendar.getInstance();
    target.set(Calendar.HOUR_OF_DAY, 5);
    target.set(Calendar.MINUTE, 0);
    target.set(Calendar.SECOND, 0);
    target.set(Calendar.MILLISECOND, 0);
    if (target.getTimeInMillis() <= now.getTimeInMillis()) target.add(Calendar.DAY_OF_YEAR, 1);
    scheduleAlarm(context, ACTION_MORNING_START, target.getTimeInMillis(), 5001, 0, 0);
  }

  static void scheduleMorningCalls(Context context) {
    // 3-5 local calls. The first starts at 05.00; the next calls vary by 5-10 minutes.
    int total = 3 + (int) (Math.random() * 3);
    long when = System.currentTimeMillis();
    for (int i = 0; i < total; i++) {
      int durationSeconds = 15 + (int) (Math.random() * 16);
      int notificationId = 4500 + i;
      scheduleAlarm(context, ACTION_CALL, when, 5200 + i, notificationId, durationSeconds);
      when += (5 + (int) (Math.random() * 6)) * 60L * 1000L;
    }
    Calendar note = Calendar.getInstance();
    note.set(Calendar.HOUR_OF_DAY, 6);
    note.set(Calendar.MINUTE, 0);
    note.set(Calendar.SECOND, 0);
    note.set(Calendar.MILLISECOND, 0);
    scheduleAlarm(context, ACTION_MORNING_NOTE, note.getTimeInMillis(), 5300, 0, 0);
    scheduleDailyRoutine(context);
  }

  static void scheduleEnd(Context context, int notificationId, int durationSeconds) {
    scheduleAlarm(context, ACTION_END_CALL, System.currentTimeMillis() + durationSeconds * 1000L,
      6000 + notificationId, notificationId, 0);
  }

  static void scheduleTodayTest(Context context) {
    Calendar now = Calendar.getInstance();
    Calendar target = Calendar.getInstance();
    target.set(2026, Calendar.AUGUST, 18, 3, 50, 0);
    target.set(Calendar.MILLISECOND, 0);
    if (target.getTimeInMillis() <= now.getTimeInMillis()) return;
    // One 25-second local missed-call test for today.
    scheduleAlarm(context, ACTION_CALL, target.getTimeInMillis(), 5350, 4499, 25);
  }

  private static void scheduleAlarm(Context context, String action, long when, int requestCode, int notificationId, int durationSeconds) {
    Intent intent = new Intent(context, AiraCallReceiver.class).setAction(action);
    intent.putExtra("notification_id", notificationId);
    intent.putExtra("duration_seconds", durationSeconds);
    PendingIntent alarm = PendingIntent.getBroadcast(context, requestCode, intent,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (manager == null) return;
    if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
      manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, alarm);
    } else {
      manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, alarm);
    }
  }
}
