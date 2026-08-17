package com.ricspace.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class RideNativeStore {
  private static final String PREFS = "ric_native_ride";
  private static final String SESSION = "session";
  private static final String POINTS = "points";

  static synchronized void begin(Context context, String sessionId) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    if (!sessionId.equals(prefs.getString(SESSION, ""))) {
      prefs.edit().putString(SESSION, sessionId).putString(POINTS, "[]").apply();
    }
  }

  static synchronized void append(Context context, android.location.Location location) {
    try {
      SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
      JSONArray rows = new JSONArray(prefs.getString(POINTS, "[]"));
      JSONObject point = new JSONObject();
      point.put("lat", location.getLatitude());
      point.put("lng", location.getLongitude());
      point.put("speed", location.getSpeed());
      point.put("heading", location.hasBearing() ? location.getBearing() : 0);
      point.put("accuracy", location.hasAccuracy() ? location.getAccuracy() : 0);
      point.put("timestamp", location.getTime() > 0 ? location.getTime() : System.currentTimeMillis());
      rows.put(point);
      int start = Math.max(0, rows.length() - 3600);
      JSONArray trimmed = new JSONArray();
      for (int i = start; i < rows.length(); i++) trimmed.put(rows.get(i));
      prefs.edit().putString(POINTS, trimmed.toString()).apply();
    } catch (Exception ignored) {}
  }

  static synchronized String points(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(POINTS, "[]");
  }

  static synchronized void clear(Context context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(SESSION).putString(POINTS, "[]").apply();
  }
}
