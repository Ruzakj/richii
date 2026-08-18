package com.ricspace.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ActivityNotFoundException;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends BridgeActivity {
  private static final String LATEST_RELEASE_API = "https://api.github.com/repos/Ruzakj/richii/releases/latest";
  private static final String LATEST_APK_URL = "https://github.com/Ruzakj/richii/releases/latest/download/Ric-Space.apk";
  private static final String APK_MIME = "application/vnd.android.package-archive";

  private final Handler pluTimerFixHandler = new Handler(Looper.getMainLooper());
  private int pluTimerFixAttempts = 0;
  private final Handler angelUiHandler = new Handler(Looper.getMainLooper());
  private BroadcastReceiver apkDownloadReceiver;
  private boolean updateCheckRunning = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WebView webView = getBridge().getWebView();
    // The app is a remote VibeTube shell. Always bypass the WebView HTTP cache so
    // a new Vercel revision (including Angel UI) is visible without reinstalling.
    webView.clearCache(true);
    webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
    RideBridge.attach(this, webView);
    AiraBridge.attach(this, webView);
    webView.addJavascriptInterface(new SpeedometerFullscreenBridge(this), "RicAndroid");
    enableImmersiveMode();
    openCompanionIfRequested(getIntent());
    schedulePluTimerWebViewFix();
    scheduleAngelUiCleanup();
    webView.postDelayed(() -> webView.loadUrl("https://vibetube-cloud.vercel.app/?app=android&refresh=1"), 180);
    checkForAppUpdate();
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    openCompanionIfRequested(intent);
    schedulePluTimerWebViewFix();
    scheduleAngelUiCleanup();
  }

  private void openCompanionIfRequested(Intent intent) {
    if (intent == null || !intent.getBooleanExtra("open_companion", false)) return;
    getBridge().getWebView().postDelayed(
      () -> getBridge().getWebView().loadUrl("https://vibetube-cloud.vercel.app/ric-companion.html?proactive=1&fresh=1"),
      350
    );
  }

  /** Check the public GitHub Release channel without blocking startup. */
  private void checkForAppUpdate() {
    if (updateCheckRunning) return;
    updateCheckRunning = true;
    new Thread(() -> {
      int remoteVersion = -1;
      try {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
          StringBuilder body = new StringBuilder();
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
          }
          Matcher tag = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"v?([0-9]+(?:\\.[0-9]+){0,2})\\\"").matcher(body.toString());
          if (tag.find()) remoteVersion = versionCodeFromTag(tag.group(1));
        }
        connection.disconnect();
      } catch (Exception ignored) {
        // Offline or GitHub unavailable: silently keep the installed version.
      }

      final int availableVersion = remoteVersion;
      updateCheckRunning = false;
      if (availableVersion <= 0 || availableVersion <= getInstalledVersionCode()) return;

      runOnUiThread(() -> showUpdateDialog(availableVersion));
    }).start();
  }

  private int getInstalledVersionCode() {
    try {
      PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return (int) info.getLongVersionCode();
      return info.versionCode;
    } catch (Exception e) {
      return 1;
    }
  }

  private static int versionCodeFromTag(String version) {
    try {
      String[] parts = version.split("\\.");
      int major = Integer.parseInt(parts[0]);
      int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
      int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
      return major * 10000 + minor * 100 + patch;
    } catch (Exception e) {
      return -1;
    }
  }

  private void showUpdateDialog(int availableVersion) {
    new AlertDialog.Builder(this)
      .setTitle("Update Ric Space tersedia")
      .setMessage("Versi baru tersedia. Data Angel, setting, dan data aplikasi tetap dipertahankan saat update.")
      .setNegativeButton("Nanti", null)
      .setPositiveButton("Update sekarang", (dialog, which) -> downloadAndInstallUpdate())
      .show();
  }

  private void downloadAndInstallUpdate() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
      Toast.makeText(this, "Izinkan Ric Space memasang update dari sumber ini.", Toast.LENGTH_LONG).show();
      try {
        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          Uri.parse("package:" + getPackageName())));
      } catch (ActivityNotFoundException ignored) {}
      return;
    }

    try {
      DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
      DownloadManager.Request request = new DownloadManager.Request(Uri.parse(LATEST_APK_URL));
      request.setTitle("Ric Space update");
      request.setDescription("Mengunduh versi terbaru Ric Space");
      request.setMimeType(APK_MIME);
      request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      request.setDestinationInExternalPublicDir(
        Environment.DIRECTORY_DOWNLOADS,
        "Ric-Space-update-" + System.currentTimeMillis() + ".apk"
      );

      final long downloadId = manager.enqueue(request);
      apkDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
          long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
          if (id != downloadId) return;
          try {
            Uri apkUri = manager.getUriForDownloadedFile(downloadId);
            if (apkUri != null) installDownloadedApk(apkUri);
            else Toast.makeText(MainActivity.this, "Download update gagal.", Toast.LENGTH_LONG).show();
          } finally {
            unregisterApkReceiver();
          }
        }
      };
      IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(apkDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
      } else {
        registerReceiver(apkDownloadReceiver, filter);
      }
      Toast.makeText(this, "Update sedang diunduh ke Download.", Toast.LENGTH_LONG).show();
    } catch (Exception e) {
      Toast.makeText(this, "Gagal memulai update: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private void installDownloadedApk(Uri apkUri) {
    try {
      Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
      intent.setDataAndType(apkUri, APK_MIME);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, "Installer Android tidak tersedia.", Toast.LENGTH_LONG).show();
    }
  }

  private void unregisterApkReceiver() {
    if (apkDownloadReceiver == null) return;
    try { unregisterReceiver(apkDownloadReceiver); } catch (Exception ignored) {}
    apkDownloadReceiver = null;
  }

  @Override
  protected void onDestroy() {
    unregisterApkReceiver();
    super.onDestroy();
  }

  /** Remove the old native Angel shortcut. The single production floating button
   * is owned by angel-shortcut.js so PWA and APK have identical UI/behavior. */
  private void scheduleAngelUiCleanup() {
    angelUiHandler.postDelayed(() -> {
      WebView webView = getBridge().getWebView();
      if (webView == null) return;
      webView.evaluateJavascript(
        "(function(){var e=document.getElementById('ric-angel-shortcut');if(e)e.remove();})()",
        null
      );
      angelUiHandler.postDelayed(this::scheduleAngelUiCleanup, 1000);
    }, 1200);
  }

  private void schedulePluTimerWebViewFix() {
    pluTimerFixAttempts = 0;
    pluTimerFixHandler.post(pluTimerFixRunnable);
  }

  private final Runnable pluTimerFixRunnable = new Runnable() {
    @Override
    public void run() {
      if (getBridge() == null || getBridge().getWebView() == null) return;
      String url = getBridge().getWebView().getUrl();
      if (url != null && url.contains("/apps/plu-timer/")) {
        getBridge().getWebView().evaluateJavascript(
          "javascript:(function(){try{if(!window.__ricPluTimerNotificationFix){window.__ricPluTimerNotificationFix=true;if(!('Notification' in window)){window.Notification=function(){return null};window.Notification.permission='denied';window.Notification.requestPermission=function(){return Promise.resolve('denied')}}else{var nativePermission=window.Notification.requestPermission;window.Notification.requestPermission=function(){try{var p=nativePermission&&nativePermission.call(window.Notification);if(p&&typeof p.catch==='function'){p.catch(function(){})}}catch(e){}return Promise.resolve(window.Notification.permission==='granted'?'granted':'denied')}}}}catch(e){}})()",
          null
        );
        return;
      }
      if (++pluTimerFixAttempts < 30) pluTimerFixHandler.postDelayed(this, 500);
    }
  };

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) enableImmersiveMode();
  }

  private void enableImmersiveMode() {
    Window window = getWindow();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setDecorFitsSystemWindows(false);
      WindowInsetsController controller = window.getInsetsController();
      if (controller != null) {
        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else {
      window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
      window.getDecorView().setSystemUiVisibility(
        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
          | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
          | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      );
    }
  }

  private static final class SpeedometerFullscreenBridge {
    private final Activity activity;
    SpeedometerFullscreenBridge(Activity activity) { this.activity = activity; }
    @JavascriptInterface
    public void setSpeedometerLandscape(final boolean landscape) {
      activity.runOnUiThread(() -> {
        activity.setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (activity instanceof MainActivity) ((MainActivity) activity).enableImmersiveMode();
      });
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == AiraBridge.LOCAL_RESTORE_REQUEST && resultCode == RESULT_OK && data != null) {
      AiraBridge.importLocalBackup(data.getData());
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == RideBridge.LOCATION_PERMISSION_REQUEST && RideBridge.hasLocationPermission(this)) RideLocationService.start(this);
    if (requestCode == AiraBridge.NOTIFICATION_PERMISSION_REQUEST) {
      AiraBridge.scheduleDailyRoutine(this);
      AiraBridge.scheduleTodayTest(this);
    }
  }
}
