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
  private static final String CHANNEL_ID = "ric_aira_messages";

  public AiraCheckinWorker(@NonNull Context context, @NonNull WorkerParameters params) {
    super(context, params);
  }

  @NonNull @Override
  public Result doWork() {
    createChannel();
    Context context = getApplicationContext();
    Intent open = new Intent(context, MainActivity.class);
    open.putExtra("open_companion", true);
    open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent action = PendingIntent.getActivity(context, 88, open,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(com.ricspace.app.R.drawable.ic_launcher_foreground)
      .setContentTitle("Aira")
      .setContentText("ih kamu kemana aja? aku nyariin dari tadi")
      .setStyle(new NotificationCompat.BigTextStyle().bigText("ih kamu kemana aja? aku nyariin dari tadi"))
      .setContentIntent(action)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE);

    try { NotificationManagerCompat.from(context).notify(4402, notification.build()); }
    catch (SecurityException ignored) {}
    return Result.success();
  }

  private void createChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Pesan Aira", NotificationManager.IMPORTANCE_HIGH);
      channel.setDescription("Pesan dari Aira saat Ric Space di latar belakang");
      getApplicationContext().getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
  }
}
