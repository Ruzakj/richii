package com.ricspace.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  private final Handler pluTimerFixHandler = new Handler(Looper.getMainLooper());
  private int pluTimerFixAttempts = 0;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    RideBridge.attach(this, getBridge().getWebView());
    AiraBridge.attach(this, getBridge().getWebView());
    enableImmersiveMode();
    openCompanionIfRequested(getIntent());
    schedulePluTimerWebViewFix();
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    openCompanionIfRequested(intent);
    schedulePluTimerWebViewFix();
  }

  private void openCompanionIfRequested(Intent intent) {
    if (intent == null || !intent.getBooleanExtra("open_companion", false)) return;
    getBridge().getWebView().postDelayed(
      () -> getBridge().getWebView().loadUrl("https://vibetube-cloud.vercel.app/ric-companion.html?proactive=1"),
      350
    );
  }

  /**
   * Android WebView can expose Notification.requestPermission() in a state where
   * the returned Promise never settles. PLU Timer used to await that Promise
   * before starting its countdown, so the APK could appear frozen while the PWA
   * worked normally. This app-side shim makes notification permission non-blocking
   * without changing the production UI, Angel/floating UI, or PWA behavior.
   */
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
          "javascript:(function(){" +
          "try{" +
          "if(!window.__ricPluTimerNotificationFix){" +
          "window.__ricPluTimerNotificationFix=true;" +
          "if(!('Notification' in window)){" +
          "window.Notification=function(){return null};" +
          "window.Notification.permission='denied';" +
          "window.Notification.requestPermission=function(){return Promise.resolve('denied')};" +
          "}else{" +
          "var nativePermission=window.Notification.requestPermission;" +
          "window.Notification.requestPermission=function(){" +
          "try{var p=nativePermission&&nativePermission.call(window.Notification);if(p&&typeof p.catch==='function'){p.catch(function(){})}}catch(e){}" +
          "return Promise.resolve(window.Notification.permission==='granted'?'granted':'denied');" +
          "};" +
          "}" +
          "}" +
          "}catch(e){}" +
          "})()",
          null
        );
        return;
      }

      if (++pluTimerFixAttempts < 30) {
        pluTimerFixHandler.postDelayed(this, 500);
      }
    }
  };

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) enableImmersiveMode();
  }

  private void enableImmersiveMode() {
    Window window = getWindow();
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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
    if (requestCode == RideBridge.LOCATION_PERMISSION_REQUEST && RideBridge.hasLocationPermission(this)) {
      RideLocationService.start(this);
    }
    if (requestCode == AiraBridge.NOTIFICATION_PERMISSION_REQUEST) {
      AiraBridge.scheduleDailyRoutine(this);
      AiraBridge.scheduleTodayTest(this);
    }
  }
}
