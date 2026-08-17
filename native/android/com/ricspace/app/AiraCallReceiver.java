package com.ricspace.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class AiraCallReceiver extends BroadcastReceiver {
  static final String CHANNEL_ID = "ric_angel_calls_v3";
  static final String NOTE_CHANNEL_ID = "ric_angel_notes_v1";

  @Override public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    int notificationId = intent.getIntExtra("notification_id", 4402);
    if ("ric.angel.END_CALL".equals(action)) {
      NotificationManagerCompat.from(context).cancel(notificationId);
      return;
    }
    if ("ric.angel.MORNING_START".equals(action)) {
      AiraBridge.scheduleMorningCalls(context);
      return;
    }
    if ("ric.angel.MORNING_NOTE".equals(action)) {
      showMorningNote(context);
      return;
    }
    show(context, notificationId, intent.getIntExtra("duration_seconds", 25));
  }

  static void show(Context context, int notificationId, int durationSeconds) {
    createCallChannel(context);
    Intent call = new Intent(context, AiraCallActivity.class);
    call.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent openCall = PendingIntent.getActivity(context, notificationId, call,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(com.ricspace.app.R.drawable.ic_launcher_foreground)
      .setContentTitle("Angel")
      .setContentText("Angel sedang menelepon")
      .setContentIntent(openCall)
      .setFullScreenIntent(openCall, true)
      .setAutoCancel(true)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
      .setVibrate(new long[]{0, 700, 500, 700, 500, 700});
    try {
      NotificationManagerCompat.from(context).notify(notificationId, notification.build());
      AiraBridge.scheduleEnd(context, notificationId, durationSeconds);
    } catch (SecurityException ignored) {}
  }

  private static void showMorningNote(Context context) {
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(NOTE_CHANNEL_ID, "Pesan Angel", NotificationManager.IMPORTANCE_HIGH);
      channel.setDescription("Sapaan pagi dari Angel");
      context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    Intent open = new Intent(context, MainActivity.class);
    open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    open.putExtra("open_companion", true);
    open.putExtra("morning_note", true);
    PendingIntent content = PendingIntent.getActivity(context, 6100, open,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    NotificationCompat.Builder note = new NotificationCompat.Builder(context, NOTE_CHANNEL_ID)
      .setSmallIcon(com.ricspace.app.R.drawable.ic_launcher_foreground)
      .setContentTitle("Angel")
      .setContentText("🎙 Pesan suara · Selamat pagi, sayang")
      .setStyle(new NotificationCompat.BigTextStyle().bigText("🎙 Pesan suara dari Angel\nSelamat pagi, sayang. Semoga harimu enak ya."))
      .setContentIntent(content)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH);
    try { NotificationManagerCompat.from(context).notify(4601, note.build()); }
    catch (SecurityException ignored) {}
  }

  private static void createCallChannel(Context context) {
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Panggilan Angel", NotificationManager.IMPORTANCE_HIGH);
      channel.setDescription("Panggilan masuk dari Angel");
      Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
      AudioAttributes audio = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build();
      channel.setSound(ringtone, audio);
      channel.enableVibration(true);
      channel.setVibrationPattern(new long[]{0, 700, 500, 700, 500, 700});
      context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
  }
}
