package com.ricspace.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public final class RideBridge {
  static final int LOCATION_PERMISSION_REQUEST = 911;
  private static Activity activity;
  @SuppressLint("StaticFieldLeak") private static WebView webView;

  static void attach(Activity host, WebView view) {
    activity = host;
    webView = view;
    view.addJavascriptInterface(new RideBridge(), "RicRideNative");
  }

  @JavascriptInterface
  public void start() {
    if (activity == null) return;
    if (!hasLocationPermission(activity)) {
      String[] permissions = Build.VERSION.SDK_INT >= 33
        ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}
        : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
      ActivityCompat.requestPermissions(activity, permissions, LOCATION_PERMISSION_REQUEST);
      return;
    }
    RideLocationService.start(activity);
  }

  @JavascriptInterface
  public void stop() {
    if (activity != null) RideLocationService.stop(activity);
  }

  static boolean hasLocationPermission(Context context) {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
      || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  static void pushLocation(double latitude, double longitude, float speed, float bearing, float accuracy) {
    WebView view = webView;
    if (view == null) return;
    String payload = String.format(java.util.Locale.US,
      "{latitude:%.7f,longitude:%.7f,speed:%.3f,heading:%.2f,accuracy:%.1f}",
      latitude, longitude, speed, bearing, accuracy);
    view.post(() -> view.evaluateJavascript(
      "window.dispatchEvent(new CustomEvent('ric-native-location',{detail:" + payload + "}));", null));
  }
}
