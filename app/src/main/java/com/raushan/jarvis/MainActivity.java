package com.raushan.jarvis;

import android.app.Activity;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  TextView status, transcript;
  EditText input;
  TextToSpeech tts;
  SpeechRecognizer recognizer;
  boolean torch=false;
  CameraManager camera;
  String cameraId;
  LinearLayout root;

  int cyan=Color.rgb(0,220,255), bg=Color.rgb(4,10,20);

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    buildUI();
    tts = new TextToSpeech(this, result -> {
      if(result==TextToSpeech.SUCCESS) {
        tts.setLanguage(new Locale("en","IN"));
        say("JARVIS online. Systems ready.");
      }
    });
    camera=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
    try { String[] ids=camera.getCameraIdList(); if(ids.length>0) cameraId=ids[0]; } catch(Exception e){}
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);
    if(SpeechRecognizer.isRecognitionAvailable(this))
      recognizer=SpeechRecognizer.createSpeechRecognizer(this);
  }

  void buildUI() {
    ScrollView scroll=new ScrollView(this);
    root=new LinearLayout(this); root.setOrientation(1); root.setPadding(24,32,24,24);
    root.setBackgroundColor(bg); scroll.addView(root); setContentView(scroll);
    TextView title=new TextView(this); title.setText("J.A.R.V.I.S."); title.setTextColor(cyan);
    title.setTextSize(30); title.setTypeface(Typeface.MONOSPACE,Typeface.BOLD); title.setGravity(Gravity.CENTER);
    root.addView(title);
    TextView sub=new TextView(this); sub.setText("PERSONAL ASSISTANT  //  NATIVE ANDROID");
    sub.setTextColor(Color.LTGRAY); sub.setGravity(Gravity.CENTER); sub.setTextSize(11);
    root.addView(sub);
    TextView orb=new TextView(this); orb.setText("◉"); orb.setTextColor(cyan); orb.setTextSize(86);
    orb.setGravity(Gravity.CENTER); orb.setPadding(0,28,0,18); root.addView(orb);
    status=new TextView(this); status.setText("SYSTEM STANDBY"); status.setTextColor(cyan);
    status.setGravity(Gravity.CENTER); status.setTextSize(14); status.setPadding(0,8,0,20); root.addView(status);
    transcript=new TextView(this); transcript.setText("Awaiting your command."); transcript.setTextColor(Color.WHITE);
    transcript.setTextSize(16); transcript.setPadding(12,18,12,18); transcript.setBackgroundColor(Color.rgb(10,25,38));
    root.addView(transcript);
    Button listen=button("🎙  SPEAK TO JARVIS"); listen.setOnClickListener(v->listen()); root.addView(listen);
    input=new EditText(this); input.setSingleLine(true); input.setHint("Type a command...");
    input.setHintTextColor(Color.GRAY); input.setTextColor(Color.WHITE); root.addView(input);
    Button send=button("SEND COMMAND"); send.setOnClickListener(v->{String s=input.getText().toString().trim(); if(!s.isEmpty()){input.setText(""); handle(s);}}); root.addView(send);
    Button stop=button("■  STOP SPEECH"); stop.setOnClickListener(v->{if(tts!=null)tts.stop(); status.setText("SPEECH INTERRUPTED");}); root.addView(stop);
    Button torchBtn=button("FLASHLIGHT ON / OFF"); torchBtn.setOnClickListener(v->toggleTorch()); root.addView(torchBtn);
    Button battery=button("BATTERY STATUS"); battery.setOnClickListener(v->{int p=getBatteryPct(); reply("Battery level is "+p+" percent.");}); root.addView(battery);
    Button wifi=button("OPEN WI-FI SETTINGS"); wifi.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))); root.addView(wifi);
    Button bt=button("OPEN BLUETOOTH SETTINGS"); bt.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))); root.addView(bt);
    Button display=button("OPEN DISPLAY SETTINGS"); display.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))); root.addView(display);
    TextView foot=new TextView(this); foot.setText("Offline commands available • Internet AI connection not yet configured in this native build");
    foot.setTextColor(Color.GRAY); foot.setTextSize(11); foot.setPadding(4,24,4,8); root.addView(foot);
  }

  Button button(String label) {
    Button b=new Button(this); b.setText(label); b.setTextColor(cyan);
    b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(12,35,52)));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,8,0,8);
    b.setLayoutParams(lp); return b;
  }

  void listen() {
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10); return;
    }
    if(recognizer==null){reply("Speech recognition is not available on this device.");return;}
    status.setText("LISTENING...");
    Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"hi-IN");
    i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
    recognizer.setRecognitionListener(new RecognitionListener(){
      public void onReadyForSpeech(Bundle p){status.setText("LISTENING...");}
      public void onBeginningOfSpeech(){}
      public void onRmsChanged(float r){}
      public void onBufferReceived(byte[] b){}
      public void onEndOfSpeech(){status.setText("PROCESSING...");}
      public void onError(int e){status.setText("READY"); transcript.setText("Speech error: "+e);}
      public void onResults(Bundle b){ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(a!=null&&!a.isEmpty())handle(a.get(0));}
      public void onPartialResults(Bundle b){}
      public void onEvent(int e,Bundle b){}
    });
    recognizer.startListening(i);
  }

  void handle(String raw) {
    String s=raw.toLowerCase(Locale.ROOT).trim();
    transcript.setText("YOU: "+raw);
    if(s.contains("stop")||s.contains("bas")||s.contains("chup")) {
      if(tts!=null)tts.stop(); status.setText("STOPPED"); return;
    }
    if(s.contains("torch")||s.contains("flashlight")||s.contains("टॉर्च")) {toggleTorch();return;}
    if(s.contains("battery")||s.contains("बैटरी")) {reply("Battery level is "+getBatteryPct()+" percent.");return;}
    if(s.contains("wifi")||s.contains("wi-fi")) {startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));reply("Opening Wi-Fi settings.");return;}
    if(s.contains("bluetooth")) {startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));reply("Opening Bluetooth settings.");return;}
    if(s.contains("display")||s.contains("brightness")) {startActivity(new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS));reply("Opening display settings.");return;}
    if(s.contains("volume")) {reply("Use your device volume buttons to adjust volume.");return;}
    reply("I heard: "+raw+". This command is not configured yet.");
  }

  void toggleTorch() {
    if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.CAMERA},11);
      reply("Camera permission is needed for the flashlight. Allow it, then tap again.");
      return;
    }
    try {
      if(cameraId==null){reply("Flashlight unavailable.");return;}
      torch=!torch; camera.setTorchMode(cameraId,torch);
      reply(torch?"Flashlight on.":"Flashlight off.");
    } catch(Exception e){torch=false;reply("Could not control flashlight. Check camera permission and device support.");}
  }

  int getBatteryPct() {
    android.content.IntentFilter f=new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    Intent i=registerReceiver(null,f); if(i==null)return -1;
    int level=i.getIntExtra("level",-1), scale=i.getIntExtra("scale",-1);
    return (level>=0&&scale>0)?Math.round(level*100f/scale):-1;
  }

  void reply(String s) { transcript.setText("JARVIS: "+s); say(s); }
  void say(String s) { status.setText("SPEAKING"); if(tts!=null)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"jarvis"); }
  @Override protected void onDestroy() {
    if(recognizer!=null){recognizer.destroy();recognizer=null;}
    if(tts!=null){tts.stop();tts.shutdown();}
    try{if(torch&&cameraId!=null)camera.setTorchMode(cameraId,false);}catch(Exception e){}
    super.onDestroy();
  }
}
