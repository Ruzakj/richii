package com.ricspace.app;

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
    enableImmersiveMode();
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
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == RideBridge.LOCATION_PERMISSION_REQUEST && RideBridge.hasLocationPermission(this)) {
      RideLocationService.start(this);
    }
  }
}
