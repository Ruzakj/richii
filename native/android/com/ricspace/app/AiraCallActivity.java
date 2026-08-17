package com.ricspace.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiraCallActivity extends Activity {
  private static final int MIC_REQUEST = 913;
  private TextToSpeech tts;
  private SpeechRecognizer recognizer;
  private MediaPlayer voicePlayer;
  private TextView status;
  private Button accept;
  private LinearLayout callActions;
  private final ExecutorService network = Executors.newSingleThreadExecutor();
  private boolean accepted = false;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);
    buildUi();
    tts = new TextToSpeech(this, statusCode -> {
      if (statusCode == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("id", "ID"));
    });
    if (SpeechRecognizer.isRecognitionAvailable(this)) recognizer = SpeechRecognizer.createSpeechRecognizer(this);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int color, float radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(dp((int) radius));
    return drawable;
  }

  private Button actionButton(String icon, String label, int color) {
    Button button = new Button(this);
    button.setText(icon + "\\n" + label);
    button.setTextSize(13);
    button.setTextColor(Color.WHITE);
    button.setAllCaps(false);
    button.setGravity(Gravity.CENTER);
    button.setPadding(0, 0, 0, 0);
    button.setBackground(shape(color, 48));
    return button;
  }

  private void buildUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(28), dp(28), dp(28), dp(34));
    root.setBackgroundColor(Color.BLACK);

    TextView label = new TextView(this);
    label.setText("PANGGILAN MASUK");
    label.setTextColor(Color.rgb(177, 151, 255));
    label.setTextSize(12);
    label.setLetterSpacing(.14f);
    label.setGravity(Gravity.CENTER);
    root.addView(label, new LinearLayout.LayoutParams(-1, -2));

    View topSpace = new View(this);
    root.addView(topSpace, new LinearLayout.LayoutParams(1, 0, 1f));

    TextView avatar = new TextView(this);
    avatar.setText("A");
    avatar.setTextSize(48);
    avatar.setTextColor(Color.WHITE);
    avatar.setGravity(Gravity.CENTER);
    GradientDrawable avatarBg = shape(Color.rgb(29, 29, 36), 84);
    avatarBg.setStroke(dp(3), Color.rgb(221, 54, 142));
    avatar.setBackground(avatarBg);
    LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(144), dp(144));
    avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
    root.addView(avatar, avatarParams);

    TextView name = new TextView(this);
    name.setText("Angel");
    name.setTextColor(Color.WHITE);
    name.setTextSize(34);
    name.setGravity(Gravity.CENTER);
    name.setPadding(0, dp(20), 0, 0);
    root.addView(name, new LinearLayout.LayoutParams(-1, -2));

    status = new TextView(this);
    status.setText("Memanggil…");
    status.setTextColor(Color.rgb(168, 168, 177));
    status.setTextSize(16);
    status.setGravity(Gravity.CENTER);
    status.setPadding(0, dp(7), 0, 0);
    root.addView(status, new LinearLayout.LayoutParams(-1, -2));

    View middleSpace = new View(this);
    root.addView(middleSpace, new LinearLayout.LayoutParams(1, 0, 1f));

    LinearLayout utilities = new LinearLayout(this);
    utilities.setGravity(Gravity.CENTER);
    TextView encrypted = new TextView(this);
    encrypted.setText("⌁  Panggilan privat");
    encrypted.setTextColor(Color.rgb(128, 128, 138));
    encrypted.setTextSize(13);
    utilities.addView(encrypted);
    root.addView(utilities, new LinearLayout.LayoutParams(-1, -2));

    callActions = new LinearLayout(this);
    callActions.setGravity(Gravity.CENTER);
    callActions.setPadding(0, dp(30), 0, 0);
    Button decline = actionButton("✕", "Tolak", Color.rgb(202, 57, 83));
    accept = actionButton("☎", "Angkat", Color.rgb(112, 66, 255));
    callActions.addView(decline, new LinearLayout.LayoutParams(dp(88), dp(88)));
    View gap = new View(this);
    callActions.addView(gap, new LinearLayout.LayoutParams(dp(72), 1));
    callActions.addView(accept, new LinearLayout.LayoutParams(dp(88), dp(88)));
    root.addView(callActions, new LinearLayout.LayoutParams(-1, -2));

    setContentView(root);
    decline.setOnClickListener(v -> finish());
    accept.setOnClickListener(v -> acceptCall());
  }

  private void acceptCall() {
    accepted = true; accept.setEnabled(false); if (callActions != null) callActions.setVisibility(View.INVISIBLE); status.setText("Terhubung · Angel");
    say("Halo Ric, akhirnya kamu angkat. Lagi ngapain?", true);
  }

  private void say(String words, boolean listenAfter) {
    network.execute(() -> {
      try {
        File audio = requestElevenLabsVoice(words);
        runOnUiThread(() -> playElevenLabsVoice(audio, listenAfter, words));
      } catch (Exception error) {
        runOnUiThread(() -> speakAndroidFallback(words, listenAfter));
      }
    });
  }

  private File requestElevenLabsVoice(String words) throws Exception {
    URL url = new URL("https://vibetube-cloud.vercel.app/api/voice");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(12000);
    connection.setReadTimeout(30000);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setDoOutput(true);
    JSONObject body = new JSONObject(); body.put("text", words);
    try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes("UTF-8")); }
    if (connection.getResponseCode() != 200) throw new Exception("ElevenLabs " + connection.getResponseCode());
    File audio = new File(getCacheDir(), "angel-voice-" + System.currentTimeMillis() + ".mp3");
    try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(audio)) {
      byte[] buffer = new byte[8192]; int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }
    return audio;
  }

  private void playElevenLabsVoice(File audio, boolean listenAfter, String fallbackWords) {
    try {
      if (voicePlayer != null) { voicePlayer.release(); voicePlayer = null; }
      voicePlayer = new MediaPlayer();
      voicePlayer.setDataSource(audio.getAbsolutePath());
      voicePlayer.setOnPreparedListener(MediaPlayer::start);
      voicePlayer.setOnCompletionListener(player -> {
        player.release(); voicePlayer = null; audio.delete();
        if (listenAfter && accepted) runOnUiThread(this::startListening);
      });
      voicePlayer.setOnErrorListener((player, what, extra) -> {
        player.release(); voicePlayer = null; audio.delete(); speakAndroidFallback(fallbackWords, listenAfter); return true;
      });
      voicePlayer.prepareAsync();
    } catch (Exception error) { audio.delete(); speakAndroidFallback(fallbackWords, listenAfter); }
  }

  private void speakAndroidFallback(String words, boolean listenAfter) {
    if (tts == null) return;
    tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
      @Override public void onStart(String id) {}
      @Override public void onError(String id) {}
      @Override public void onDone(String id) { if ("listen".equals(id) && accepted) runOnUiThread(() -> startListening()); }
    });
    tts.speak(words, TextToSpeech.QUEUE_FLUSH, null, listenAfter ? "listen" : "reply");
  }

  private void startListening() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST); return;
    }
    if (recognizer == null) { status.setText("Voice recognition tidak tersedia di perangkat ini."); return; }
    status.setText("Mendengarkan…");
    recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
      public void onReadyForSpeech(Bundle b) {} public void onBeginningOfSpeech() {} public void onRmsChanged(float r) {} public void onBufferReceived(byte[] b) {}
      public void onEndOfSpeech() { status.setText("Angel sedang berpikir…"); }
      public void onError(int error) { if (accepted) status.setText("Aku belum denger, coba bicara lagi."); }
      public void onResults(Bundle results) {
        ArrayList<String> texts=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts != null && !texts.isEmpty()) askAira(texts.get(0));
      }
      public void onPartialResults(Bundle p) {} public void onEvent(int t, Bundle p) {}
    });
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    recognizer.startListening(intent);
  }

  private void askAira(String message) {
    status.setText("Angel sedang berpikir…");
    network.execute(() -> {
      try {
        URL url = new URL("https://vibetube-cloud.vercel.app/api/companion");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
        connection.setRequestProperty("Content-Type", "application/json"); connection.setDoOutput(true);
        JSONObject body = new JSONObject();
        body.put("system", "Kamu Angel, perempuan dan pacarnya Ric. Ric adalah cowok dan pacarmu. Sedang dalam panggilan suara. Jawab bahasa Indonesia santai, hangat, natural, singkat 1-3 kalimat, jangan menyebut sistem atau AI.");
        JSONArray messages = new JSONArray(); messages.put(new JSONObject().put("role", "user").put("content", message)); body.put("messages", messages);
        try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes("UTF-8")); }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) { String line; while ((line = reader.readLine()) != null) result.append(line); }
        String reply = new JSONObject(result.toString()).optString("reply", "Aku dengerin kok, lanjut cerita ya.");
        runOnUiThread(() -> { status.setText("Terhubung · Angel"); say(reply, true); });
      } catch (Exception e) { runOnUiThread(() -> { status.setText("Koneksi putus, coba lagi ya."); say("Maaf ya, koneksiku lagi putus. Coba panggil aku lagi nanti.", false); }); }
    });
  }

  @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (requestCode == MIC_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startListening();
  }

  @Override protected void onDestroy() {
    accepted = false;
    if (recognizer != null) { recognizer.destroy(); recognizer = null; }
    if (voicePlayer != null) { voicePlayer.stop(); voicePlayer.release(); voicePlayer = null; }
    if (tts != null) { tts.stop(); tts.shutdown(); }
    network.shutdownNow();
    super.onDestroy();
  }
}
