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
  private TextView status;
  private Button accept;
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

  private void buildUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    root.setPadding(42, 42, 42, 42);
    root.setBackgroundColor(Color.rgb(7, 8, 13));
    TextView label = new TextView(this); label.setText("INCOMING CALL"); label.setTextColor(Color.rgb(161, 151, 255)); label.setTextSize(12); label.setLetterSpacing(.16f); label.setGravity(Gravity.CENTER);
    TextView name = new TextView(this); name.setText("Angel"); name.setTextColor(Color.WHITE); name.setTextSize(42); name.setGravity(Gravity.CENTER);
    status = new TextView(this); status.setText("Angel is calling…"); status.setTextColor(Color.rgb(180, 182, 198)); status.setTextSize(16); status.setGravity(Gravity.CENTER);
    LinearLayout buttons = new LinearLayout(this); buttons.setGravity(Gravity.CENTER); buttons.setPadding(0, 46, 0, 0);
    Button decline = new Button(this); decline.setText("Tolak"); decline.setTextColor(Color.WHITE); decline.setBackgroundColor(Color.rgb(82, 43, 58));
    accept = new Button(this); accept.setText("Angkat"); accept.setTextColor(Color.WHITE); accept.setBackgroundColor(Color.rgb(67, 122, 100));
    buttons.addView(decline, new LinearLayout.LayoutParams(0, 58, 1));
    buttons.addView(new View(this), new LinearLayout.LayoutParams(24, 1));
    buttons.addView(accept, new LinearLayout.LayoutParams(0, 58, 1));
    root.addView(label); root.addView(name); root.addView(status); root.addView(buttons);
    setContentView(root);
    decline.setOnClickListener(v -> finish());
    accept.setOnClickListener(v -> acceptCall());
  }

  private void acceptCall() {
    accepted = true; accept.setEnabled(false); status.setText("Terhubung · bicara setelah bunyi");
    say("Halo Ric, akhirnya kamu angkat. Lagi ngapain?", true);
  }

  private void say(String words, boolean listenAfter) {
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
    status.setText("Aira sedang berpikir…");
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
    if (tts != null) { tts.stop(); tts.shutdown(); }
    network.shutdownNow();
    super.onDestroy();
  }
}
