package com.ricspace.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    RideBridge.attach(this, getBridge().getWebView());
    AiraBridge.attach(this, getBridge().getWebView());
    enableImmersiveMode();
    openCompanionIfRequested(getIntent());
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    openCompanionIfRequested(intent);
  }

  private void openCompanionIfRequested(Intent intent) {
    if (intent == null || !intent.getBooleanExtra("open_companion", false)) return;
    getBridge().getWebView().postDelayed(
      () -> getBridge().getWebView().loadUrl("https://vibetube-cloud.vercel.app/ric-companion.html?proactive=1"),
      350
    );
  }

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
