package com.ricspace.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.provider.Settings;
import android.provider.MediaStore;
import android.net.Uri;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.io.InputStream;
import java.io.OutputStream;
import android.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

public final class AiraBridge {
  static final int NOTIFICATION_PERMISSION_REQUEST = 912;
  static final int LOCAL_RESTORE_REQUEST = 914;
  private static final String ACTION_MORNING_START = "ric.angel.MORNING_START";
  private static final String ACTION_CALL = "ric.angel.CALL";
  private static final String ACTION_END_CALL = "ric.angel.END_CALL";
  private static final String ACTION_MORNING_NOTE = "ric.angel.MORNING_NOTE";
  private static final AtomicInteger REQUESTS = new AtomicInteger(5100);
  private static Activity activity;

  static void attach(Activity host, WebView view) {
    activity = host;
    view.addJavascriptInterface(new AiraBridge(), "RicAiraNative");
    installAngelShortcut(view);
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(host, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(host, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
    } else {
      requestExactAlarmAccess(host);
      scheduleDailyRoutine(host);
      scheduleTodayTest(host);
    }
  }

  private static void installAngelShortcut(WebView view) {
    Runnable addShortcut = () -> view.evaluateJavascript(
      "(function(){"
        + "function start(){try{window.RicAiraNative&&window.RicAiraNative.startCall&&window.RicAiraNative.startCall()}catch(e){}}"
        + "function bind(){document.querySelectorAll('button.video').forEach(function(b){if(b.dataset.angelCallBound)return;b.dataset.angelCallBound='1';b.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();start()},true)})}"
        + "function add(){if(document.getElementById('ric-angel-shortcut'))return;var b=document.createElement('button');b.id='ric-angel-shortcut';b.type='button';b.setAttribute('aria-label','Telepon Angel');b.innerHTML='<span>✦</span> Angel';b.style.cssText='position:fixed;right:16px;bottom:88px;z-index:2147483647;border:1px solid rgba(255,255,255,.22);border-radius:999px;padding:11px 14px;background:linear-gradient(135deg,#7c5cff,#b14cff);box-shadow:0 12px 32px rgba(74,47,180,.38);color:#fff;font:700 14px system-ui,-apple-system,sans-serif;letter-spacing:.01em';b.onclick=start;document.body.appendChild(b)}"
        + "function backup(){var box=document.querySelector('.backup-actions');if(!box||document.getElementById('ric-local-backup'))return;var row=document.createElement('div');row.id='ric-local-backup';row.style.cssText='display:flex;gap:8px;margin-top:10px';var save=document.createElement('button'),restore=document.createElement('button');save.type=restore.type='button';save.textContent='Backup ke Download';restore.textContent='Pulihkan dari file';[save,restore].forEach(function(x){x.style.cssText='flex:1;border:0;border-radius:10px;padding:10px;background:#292929;color:#fff;font:600 12px system-ui'});save.onclick=function(){try{var raw=localStorage.getItem('ric-companion-v1')||'';if(!raw){window.dispatchEvent(new CustomEvent('aira-local-backup-status',{detail:'Belum ada data Angel untuk dibackup'}));return}window.RicAiraNative&&window.RicAiraNative.exportLocalBackup&&window.RicAiraNative.exportLocalBackup(btoa(unescape(encodeURIComponent(raw))))}catch(e){window.dispatchEvent(new CustomEvent('aira-local-backup-status',{detail:'Backup Angel gagal'}))}};restore.onclick=function(){try{window.RicAiraNative&&window.RicAiraNative.restoreLocalBackup&&window.RicAiraNative.restoreLocalBackup()}catch(e){}};row.append(save,restore);box.parentNode.appendChild(row);window.addEventListener('aira-local-backup-status',function(e){var s=document.getElementById('backupStatus');if(s)s.textContent=e.detail})}"
        + "bind();add();backup();setTimeout(function(){bind();backup()},1200)})()",
      null);
    view.postDelayed(addShortcut, 1300);
    view.postDelayed(addShortcut, 3500);
    view.postDelayed(addShortcut, 7500);
  }

  private static void requestExactAlarmAccess(Activity host) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
    AlarmManager manager = (AlarmManager) host.getSystemService(Context.ALARM_SERVICE);
    if (manager == null || manager.canScheduleExactAlarms()) return;
    try {
      Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:" + host.getPackageName()));
      host.startActivity(settings);
    } catch (Exception ignored) {}
  }

  static void recordCall(String direction, String outcome, int durationSeconds, String detail) {
    if (activity == null) return;
    try {
      JSONObject payload = new JSONObject();
      String directionLabel = "outgoing".equals(direction) ? "Panggilan keluar" : "Panggilan masuk";
      String statusLabel = "ended".equals(outcome) ? "selesai" : ("declined".equals(outcome) ? "ditolak" : ("missed".equals(outcome) ? "tidak dijawab" : "diangkat"));
      String durationLabel = durationSeconds > 0 ? " · " + (durationSeconds / 60) + " mnt " + (durationSeconds % 60) + " dtk" : "";
      payload.put("direction", direction);
      payload.put("outcome", outcome);
      payload.put("duration", 0);
      payload.put("detail", directionLabel + " · " + statusLabel + durationLabel);
      payload.put("at", System.currentTimeMillis());
      String script = "window.RicAiraChat&&window.RicAiraChat.recordCall(" + payload.toString() + ")";
      activity.runOnUiThread(() -> {
        WebView page = ((MainActivity) activity).getBridge().getWebView();
        page.evaluateJavascript(script, null);
      });
    } catch (Exception ignored) {}
  }

  @JavascriptInterface
  public void exportLocalBackup(String base64Payload) {
    if (activity == null || base64Payload == null || base64Payload.isEmpty()) return;
    activity.runOnUiThread(() -> {
      Uri uri = null;
      try {
        byte[] data = Base64.decode(base64Payload, Base64.DEFAULT);
        String raw = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        JSONObject source = new JSONObject(raw);
        JSONObject backup = new JSONObject();
        backup.put("format", "ric-space-angel-backup");
        backup.put("version", 1);
        backup.put("exportedAt", System.currentTimeMillis());
        backup.put("app", "Ric Space");
        backup.put("data", source);
        byte[] outputData = backup.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, "Ric_Space_Angel_Backup_" + System.currentTimeMillis() + ".json");
        values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          values.put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
          values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("Folder Download tidak tersedia");
        try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
          if (output == null) throw new Exception("File tidak dapat dibuka");
          output.write(outputData);
          output.flush();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          ContentValues done = new ContentValues();
          done.put(MediaStore.MediaColumns.IS_PENDING, 0);
          activity.getContentResolver().update(uri, done, null, null);
        }
        notifyBackupStatus("Backup Angel tersimpan di folder Download");
      } catch (Exception error) {
        if (uri != null) {
          try { activity.getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
        }
        notifyBackupStatus("Backup Angel gagal disimpan");
      }
    });
  }

  private static void notifyBackupStatus(String message) {
    if (activity == null) return;
    String safe = JSONObject.quote(message);
    ((MainActivity) activity).getBridge().getWebView().evaluateJavascript(
      "window.dispatchEvent(new CustomEvent('aira-local-backup-status',{detail:" + safe + "}))", null);
  }

  @JavascriptInterface
  public void restoreLocalBackup() {
    if (activity == null) return;
    activity.runOnUiThread(() -> {
      Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      picker.addCategory(Intent.CATEGORY_OPENABLE);
      picker.setType("application/json");
      activity.startActivityForResult(picker, LOCAL_RESTORE_REQUEST);
    });
  }

  static void importLocalBackup(android.net.Uri uri) {
    if (activity == null || uri == null) return;
    try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[8192]; int count;
      while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
      String raw = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      JSONObject file = new JSONObject(raw);
      String payload;
      if ("ric-space-angel-backup".equals(file.optString("format"))) {
        JSONObject data = file.optJSONObject("data");
        if (data == null) throw new Exception("Data Angel tidak ditemukan");
        payload = data.toString();
      } else {
        // Accept the previous backup format for backward compatibility.
        new JSONObject(raw);
        payload = raw;
      }
      String encoded = Base64.encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP);
      String script = "(function(){try{var raw=decodeURIComponent(escape(atob('" + encoded + "')));JSON.parse(raw);localStorage.setItem('ric-companion-v1',raw);location.reload()}catch(e){window.dispatchEvent(new CustomEvent('aira-local-backup-status',{detail:'File backup Angel tidak valid'}))}})()";
      activity.runOnUiThread(() -> ((MainActivity) activity).getBridge().getWebView().evaluateJavascript(script, null));
    } catch (Exception error) {
      notifyBackupStatus("File backup Angel tidak valid");
    }
  }

  @JavascriptInterface
  public void startCall() {
    if (activity == null) return;
    activity.runOnUiThread(() -> {
      Intent intent = new Intent(activity, AiraCallActivity.class);
      intent.putExtra("manual_call", true);
      activity.startActivity(intent);
    });
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
    target.set(2026, Calendar.AUGUST, 18, 13, 10, 0);
    target.set(Calendar.MILLISECOND, 0);
    SharedPreferences prefs = context.getSharedPreferences("ric_angel_tests", Context.MODE_PRIVATE);
    String testKey = "angel_call_test_20260818_1310";
    if (prefs.getBoolean(testKey, false)) return;
    long when = target.getTimeInMillis();
    Calendar fallbackLimit = Calendar.getInstance();
    fallbackLimit.set(2026, Calendar.AUGUST, 18, 13, 30, 0);
    if (when <= now.getTimeInMillis()) {
      if (now.getTimeInMillis() > fallbackLimit.getTimeInMillis()) return;
      when = now.getTimeInMillis() + 90_000L;
    }
    scheduleAlarm(context, ACTION_CALL, when, 5350, 4499, 40);
    prefs.edit().putBoolean(testKey, true).apply();
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
