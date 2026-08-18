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
  private static final int VERSION_BASE = 100000;
  private final Handler pluTimerFixHandler = new Handler(Looper.getMainLooper());
  private int pluTimerFixAttempts = 0;
  private final Handler angelUiHandler = new Handler(Looper.getMainLooper());
  private BroadcastReceiver apkDownloadReceiver;
  private boolean updateCheckRunning = false;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WebView webView = getBridge().getWebView();
    webView.clearCache(true);
    webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
    RideBridge.attach(this, webView);
    AiraBridge.attach(this, webView);
    webView.addJavascriptInterface(new SpeedometerFullscreenBridge(this), "RicAndroid");
    webView.addJavascriptInterface(new UpdateBridge(), "RicUpdater");
    enableImmersiveMode();
    openCompanionIfRequested(getIntent());
    schedulePluTimerWebViewFix();
    scheduleAngelUiCleanup();
    webView.postDelayed(() -> webView.loadUrl("https://vibetube-cloud.vercel.app/?app=android&refresh=1"), 180);
    checkForAppUpdate();
  }

  @Override public void onNewIntent(Intent intent) {
    super.onNewIntent(intent); setIntent(intent); openCompanionIfRequested(intent); schedulePluTimerWebViewFix(); scheduleAngelUiCleanup();
  }

  private void openCompanionIfRequested(Intent intent) {
    if (intent == null || !intent.getBooleanExtra("open_companion", false)) return;
    getBridge().getWebView().postDelayed(() -> getBridge().getWebView().loadUrl("https://vibetube-cloud.vercel.app/ric-companion.html?proactive=1&fresh=1"), 350);
  }

  private void checkForAppUpdate() {
    if (updateCheckRunning) return;
    updateCheckRunning = true;
    new Thread(() -> {
      int remoteVersion = -1;
      try {
        HttpURLConnection c = (HttpURLConnection)new URL(LATEST_RELEASE_API).openConnection();
        c.setRequestMethod("GET"); c.setConnectTimeout(5000); c.setReadTimeout(5000); c.setRequestProperty("Accept", "application/vnd.github+json");
        if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
          StringBuilder b = new StringBuilder(); String line;
          try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) { while ((line=r.readLine())!=null) b.append(line); }
          Matcher m=Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"v?1\\.0\\.([0-9]+)\\\"").matcher(b.toString());
          if(m.find()) remoteVersion=VERSION_BASE+Integer.parseInt(m.group(1));
        }
        c.disconnect();
      } catch(Exception ignored) {}
      final int v=remoteVersion; updateCheckRunning=false;
      if(v>0 && v>getInstalledVersionCode()) runOnUiThread(() -> showUpdateDialog(v));
    }).start();
  }

  private int getInstalledVersionCode() {
    try { PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0); return Build.VERSION.SDK_INT>=Build.VERSION_CODES.P?(int)p.getLongVersionCode():p.versionCode; }
    catch(Exception e){return 1;}
  }

  private void showUpdateDialog(int availableVersion) {
    new AlertDialog.Builder(this).setTitle("Update Ric Space tersedia")
      .setMessage("Versi baru tersedia. Data Angel, setting, dan data aplikasi tetap dipertahankan.")
      .setNegativeButton("Nanti",null).setPositiveButton("Update sekarang",(d,w)->downloadAndInstallUpdate()).show();
  }

  private void manualUpdateCheck() { Toast.makeText(this,"Memeriksa update…",Toast.LENGTH_SHORT).show(); checkForAppUpdate(); }

  private void downloadAndInstallUpdate() {
    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
      Toast.makeText(this,"Izinkan Ric Space memasang update dari sumber ini.",Toast.LENGTH_LONG).show();
      try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()))); } catch(ActivityNotFoundException ignored) {}
      return;
    }
    try {
      DownloadManager m=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
      DownloadManager.Request r=new DownloadManager.Request(Uri.parse(LATEST_APK_URL));
      r.setTitle("Ric Space update"); r.setDescription("Mengunduh versi terbaru Ric Space"); r.setMimeType(APK_MIME);
      r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"Ric-Space-update-"+System.currentTimeMillis()+".apk");
      final long id=m.enqueue(r);
      apkDownloadReceiver=new BroadcastReceiver(){ public void onReceive(Context x,Intent i){ if(!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(i.getAction()))return; if(i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1)!=id)return; try{Uri u=m.getUriForDownloadedFile(id); if(u!=null)installDownloadedApk(u);else Toast.makeText(MainActivity.this,"Download update gagal.",Toast.LENGTH_LONG).show();}finally{unregisterApkReceiver();}}};
      IntentFilter f=new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE); if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)registerReceiver(apkDownloadReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(apkDownloadReceiver,f);
      Toast.makeText(this,"Update sedang diunduh ke Download.",Toast.LENGTH_LONG).show();
    }catch(Exception e){Toast.makeText(this,"Gagal memulai update: "+e.getMessage(),Toast.LENGTH_LONG).show();}
  }

  private void installDownloadedApk(Uri u){try{Intent i=new Intent(Intent.ACTION_INSTALL_PACKAGE);i.setDataAndType(u,APK_MIME);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(ActivityNotFoundException e){Toast.makeText(this,"Installer Android tidak tersedia.",Toast.LENGTH_LONG).show();}}
  private void unregisterApkReceiver(){if(apkDownloadReceiver==null)return;try{unregisterReceiver(apkDownloadReceiver);}catch(Exception ignored){}apkDownloadReceiver=null;}
  @Override protected void onDestroy(){unregisterApkReceiver();super.onDestroy();}

  private void scheduleAngelUiCleanup(){angelUiHandler.postDelayed(()->{WebView w=getBridge().getWebView();if(w==null)return;w.evaluateJavascript("(function(){var e=document.getElementById('ric-angel-shortcut');if(e)e.remove();})()",null);angelUiHandler.postDelayed(this::scheduleAngelUiCleanup,1000);},1200);}
  private void schedulePluTimerWebViewFix(){pluTimerFixAttempts=0;pluTimerFixHandler.post(pluTimerFixRunnable);}
  private final Runnable pluTimerFixRunnable=new Runnable(){public void run(){if(getBridge()==null||getBridge().getWebView()==null)return;String u=getBridge().getWebView().getUrl();if(u!=null&&u.contains("/apps/plu-timer/")){getBridge().getWebView().evaluateJavascript("javascript:(function(){try{if(!window.__ricPluTimerNotificationFix){window.__ricPluTimerNotificationFix=true;if(!('Notification'in window)){window.Notification=function(){return null};window.Notification.permission='denied';window.Notification.requestPermission=function(){return Promise.resolve('denied')}}else{var n=window.Notification.requestPermission;window.Notification.requestPermission=function(){try{var p=n&&n.call(window.Notification);if(p&&typeof p.catch==='function')p.catch(function(){})}catch(e){}return Promise.resolve(window.Notification.permission==='granted'?'granted':'denied')}}}}catch(e){}})()",null);return;}if(++pluTimerFixAttempts<30)pluTimerFixHandler.postDelayed(this,500);}};
  @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)enableImmersiveMode();}
  private void enableImmersiveMode(){Window w=getWindow();if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){w.setDecorFitsSystemWindows(false);WindowInsetsController c=w.getInsetsController();if(c!=null){c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}else{w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);w.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|android.view.View.SYSTEM_UI_FLAG_FULLSCREEN|android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}}

  private static final class SpeedometerFullscreenBridge{private final Activity a;SpeedometerFullscreenBridge(Activity a){this.a=a;}@JavascriptInterface public void setSpeedometerLandscape(final boolean l){a.runOnUiThread(()->{a.setRequestedOrientation(l?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);if(a instanceof MainActivity)((MainActivity)a).enableImmersiveMode();});}}
  private final class UpdateBridge{@JavascriptInterface public void check(){runOnUiThread(MainActivity.this::manualUpdateCheck);}@JavascriptInterface public void updateNow(){runOnUiThread(MainActivity.this::downloadAndInstallUpdate);}@JavascriptInterface public String version(){return String.valueOf(getInstalledVersionCode());}}

  @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==AiraBridge.LOCAL_RESTORE_REQUEST&&c==RESULT_OK&&d!=null)AiraBridge.importLocalBackup(d.getData());}
  @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==RideBridge.LOCATION_PERMISSION_REQUEST&&RideBridge.hasLocationPermission(this))RideLocationService.start(this);if(r==AiraBridge.NOTIFICATION_PERMISSION_REQUEST){AiraBridge.scheduleDailyRoutine(this);AiraBridge.scheduleTodayTest(this);}}
}
