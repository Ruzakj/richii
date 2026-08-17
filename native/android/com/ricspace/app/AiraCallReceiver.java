package com.ricspace.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class AiraCallReceiver extends BroadcastReceiver {
  static final String CHANNEL_ID = "ric_aira_calls";

  @Override public void onReceive(Context context, Intent intent) {
    show(context);
  }

  static void show(Context context) {
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Panggilan Aira", NotificationManager.IMPORTANCE_HIGH);
      channel.setDescription("Panggilan masuk dari Aira");
      context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    Intent call = new Intent(context, AiraCallActivity.class);
    call.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent openCall = PendingIntent.getActivity(context, 88, call, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(com.ricspace.app.R.drawable.ic_launcher_foreground)
      .setContentTitle("Aira")
      .setContentText("Panggilan masuk")
      .setContentIntent(openCall)
      .setFullScreenIntent(openCall, true)
      .setAutoCancel(true)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_CALL);
    try { NotificationManagerCompat.from(context).notify(4402, notification.build()); }
    catch (SecurityException ignored) {}
  }
}
