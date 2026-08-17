package com.ricspace.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AiraCheckinWorker extends Worker {
  private static final String CHANNEL_ID = "ric_aira_calls";

  public AiraCheckinWorker(@NonNull Context context, @NonNull WorkerParameters params) {
    super(context, params);
  }

  @NonNull @Override
  public Result doWork() {
    createChannel();
    Context context = getApplicationContext();
    Intent call = new Intent(context, AiraCallActivity.class);
    call.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent openCall = PendingIntent.getActivity(context, 88, call,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

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
    return Result.success();
  }

  private void createChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Panggilan Aira", NotificationManager.IMPORTANCE_HIGH);
      channel.setDescription("Panggilan masuk dari Aira");
      getApplicationContext().getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
  }
}
