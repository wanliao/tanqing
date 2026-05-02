package com.example.nikkiautopiano;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class TestPlayActivity extends AppCompatActivity {

    private Map<String, Button> btnMap = new HashMap<>();
    private boolean isTesting = false;
    private String jsonScore = "[]";
    private TextView statusTv;

    // 【修改】只保留 TextView，不需要 ScrollView 了
    private TextView lyricTv;

    private SoundPool soundPool;
    private Map<String, Integer> soundMap = new HashMap<>();
    private boolean isSoundLoaded = false;

    private static final String PREF_NAME = "PianoAppConfig";
    private static final String KEY_SCORE_PATH = "ScorePath";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setImmersiveStatusBar();
        // 【新增】强制锁定当前界面为竖屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        loadScoreData();
        initSoundPool();

        // 1. 根布局
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(0, getStatusBarHeight(), 0, 0);
        // 2. 状态提示文本
        statusTv = new TextView(this);
        statusTv.setText("加载音效中...");
        statusTv.setTextColor(Color.WHITE);
        statusTv.setPadding(0, 30, 0, 10);
        root.addView(statusTv);

        // 3. 【修改】中间的单行歌词展示区（覆盖模式）
        lyricTv = new TextView(this);
        // 使用 weight=1.0f，让歌词区域占据屏幕中间所有剩余的空间
        LinearLayout.LayoutParams lyricParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        lyricParams.setMargins(40, 10, 40, 30);
        lyricTv.setLayoutParams(lyricParams);

        // 设置居中显示、大字体、醒目的颜色
        lyricTv.setGravity(Gravity.CENTER);
        lyricTv.setBackgroundColor(Color.parseColor("#222222"));
        lyricTv.setTextColor(Color.parseColor("#00E676"));
        lyricTv.setTextSize(24f);
        lyricTv.setText("等待播放...\n(如果歌词将在此处显示)");

        root.addView(lyricTv);

        // 4. 底部的钢琴键盘
        GridLayout gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(7);
        gridLayout.setRowCount(3);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.gravity = Gravity.CENTER_HORIZONTAL;
        gridLayout.setLayoutParams(gridParams);

        String[] rows = {"T", "M", "B"};
        for (int r = 0; r < 3; r++) {
            for (int c = 1; c <= 7; c++) {
                String keyName = rows[r] + c;
                Button btn = createPianoKey(keyName);
                btnMap.put(keyName, btn);
                gridLayout.addView(btn);

                loadSoundForKey(keyName);
            }
        }
        root.addView(gridLayout);

        // 5. 播放按钮
        Button startBtn = new Button(this);
        startBtn.setText("开始测试播放");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 30, 0, 50);
        startBtn.setLayoutParams(btnParams);

        startBtn.setOnClickListener(v -> {
            if (!isTesting) startTest();
        });
        root.addView(startBtn);

        setContentView(root);
        setupAliases();
    }

    private void loadScoreData() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 获取文件名
        String fileName = sp.getString(KEY_SCORE_PATH, "");
        if (!TextUtils.isEmpty(fileName)) {
            try {
                // 使用新的读取方法
                jsonScore = readTextFromAssets(fileName);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "文件读取失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
// 1. 同样在 TestPlayActivity 类的底部，加入沉浸式辅助方法：

    private void setImmersiveStatusBar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.view.Window window = getWindow();
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    // 【特有】因为这个界面是黑色的，如果是白底可能还需要加亮色状态栏文字的 Flag
            );
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result == 0 ? 80 : result;
    }
    private String readTextFromAssets(String fileName) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = getAssets().open("zhiyuan/" + fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }
        return stringBuilder.toString();
    }

    private void initSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build();

        soundPool.setOnLoadCompleteListener((soundPool1, sampleId, status) -> {
            isSoundLoaded = true;
            runOnUiThread(() -> statusTv.setText("准备就绪！可以播放了"));
        });
    }

    private void loadSoundForKey(String keyName) {
        int resId = getResources().getIdentifier(keyName.toLowerCase(), "raw", getPackageName());
        if (resId != 0) {
            int soundId = soundPool.load(this, resId, 1);
            soundMap.put(keyName, soundId);
        }
    }

    private Button createPianoKey(String name) {
        Button btn = new Button(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 130;
        params.height = 130;
        params.setMargins(8, 8, 8, 8);
        btn.setLayoutParams(params);
        btn.setText(name);
        btn.setTextSize(12);
        btn.setTextColor(Color.GRAY);
        setKeyStyle(btn, false);
        return btn;
    }

    private void setKeyStyle(Button btn, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(15);
        if (active) {
            gd.setColor(Color.parseColor("#FF4081"));
            btn.setTextColor(Color.WHITE);
        } else {
            gd.setColor(Color.parseColor("#333333"));
            btn.setTextColor(Color.GRAY);
        }
        gd.setStroke(2, Color.parseColor("#555555"));
        btn.setBackground(gd);
    }

    private void setupAliases() {
        String[] letters = {"C", "D", "E", "F", "G", "A", "B"};
        for (int i = 1; i <= 7; i++) {
            Button targetBtn = btnMap.get("M" + i);
            btnMap.put(String.valueOf(i), targetBtn);
            btnMap.put(letters[i-1], targetBtn);
            btnMap.put(letters[i-1].toLowerCase(), targetBtn);

            if (soundMap.containsKey("M" + i)) {
                int soundId = soundMap.get("M" + i);
                soundMap.put(String.valueOf(i), soundId);
                soundMap.put(letters[i-1], soundId);
                soundMap.put(letters[i-1].toLowerCase(), soundId);
            }
        }
    }

    private void startTest() {
        if (!isSoundLoaded && !soundMap.isEmpty()) {
            statusTv.setText("音效还没加载完，稍等哦...");
            return;
        }

        if (jsonScore.equals("[]") || TextUtils.isEmpty(jsonScore)) {
            statusTv.setText("暂无有效乐谱");
            return;
        }

        isTesting = true;
        statusTv.setText("正在播放...");

        // 每次点击播放时重置歌词
        lyricTv.setText("♪ ...");

        new Thread(() -> {
            try {
                JSONArray jsonArray = new JSONArray(jsonScore);
                long startTime = System.currentTimeMillis();
                long accumulatedDelay = 0;

                for (int i = 0; i < jsonArray.length(); i++) {
                    if (!isTesting) break;
                    JSONObject step = jsonArray.getJSONObject(i);
                    JSONArray notes = step.optJSONArray("notes");
                    JSONArray durations = step.optJSONArray("durations");
                    int defaultDuration = step.optInt("duration", 80);
                    int delay = step.optInt("delay", 400);

                    // 读取当前步的歌词
                    String lyricText = step.optString("lyric", step.optString("lyrics", ""));

                    // 【核心修改】如果这一行有歌词，直接 setText 覆盖掉之前的文字
                    if (!TextUtils.isEmpty(lyricText)) {
                        runOnUiThread(() -> lyricTv.setText(lyricText));
                    }

                    if (notes != null) {
                        for (int j = 0; j < notes.length(); j++) {
                            String note = notes.getString(j);
                            int dur = (durations != null && j < durations.length())
                                    ? durations.getInt(j) : defaultDuration;

                            highlightKey(note, dur);
                        }
                    }

                    accumulatedDelay += delay;
                    long targetTime = startTime + accumulatedDelay;
                    long sleepTime = targetTime - System.currentTimeMillis();
                    if (sleepTime > 0) Thread.sleep(sleepTime);
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(TestPlayActivity.this, "JSON格式解析失败，请检查文件", Toast.LENGTH_SHORT).show());
            }
            runOnUiThread(() -> {
                isTesting = false;
                statusTv.setText("播放结束");
                // 播放结束后给出提示
                lyricTv.setText("--- 播放完毕 ---");
            });
        }).start();
    }

    private void highlightKey(String note, int duration) {
        Button btn = btnMap.get(note);
        if (btn == null || note.equals("0")) return;

        if (soundMap.containsKey(note)) {
            soundPool.play(soundMap.get(note), 1.0f, 1.0f, 1, 0, 1.0f);
        }

        runOnUiThread(() -> setKeyStyle(btn, true));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            runOnUiThread(() -> setKeyStyle(btn, false));
        }, duration);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isTesting = false;
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}