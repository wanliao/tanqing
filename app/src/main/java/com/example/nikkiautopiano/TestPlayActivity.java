package com.example.nikkiautopiano;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class TestPlayActivity extends AppCompatActivity {

    private Map<String, Button> btnMap = new HashMap<>();
    private boolean isTesting = false;
    private String jsonScore;
    private TextView statusTv;

    // 【新增】音频播放引擎和音频映射表
    private SoundPool soundPool;
    private Map<String, Integer> soundMap = new HashMap<>();
    private boolean isSoundLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        jsonScore = getIntent().getStringExtra("json_score");

        // 【新增】初始化 SoundPool
        initSoundPool();

        // --- 以下为原有 UI 代码 ---
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setGravity(Gravity.CENTER);

        statusTv = new TextView(this);
        statusTv.setText("加载音效中...");
        statusTv.setTextColor(Color.WHITE);
        statusTv.setPadding(0, 50, 0, 50);
        root.addView(statusTv);

        GridLayout gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(7);
        gridLayout.setRowCount(3);

        String[] rows = {"T", "M", "B"};
        for (int r = 0; r < 3; r++) {
            for (int c = 1; c <= 7; c++) {
                String keyName = rows[r] + c;
                Button btn = createPianoKey(keyName);
                btnMap.put(keyName, btn);
                gridLayout.addView(btn);

                // 【新增】尝试加载对应的音频文件 (比如 t1.mp3)
                loadSoundForKey(keyName);
            }
        }
        root.addView(gridLayout);

        Button startBtn = new Button(this);
        startBtn.setText("开始测试播放");
        startBtn.setOnClickListener(v -> {
            if (!isTesting) startTest();
        });
        root.addView(startBtn);

        setContentView(root);
        setupAliases();
    }

    // 【新增】配置音频引擎，允许最多 10 个按键同时发声
    private void initSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build();

        // 监听加载完成
        soundPool.setOnLoadCompleteListener((soundPool1, sampleId, status) -> {
            isSoundLoaded = true;
            runOnUiThread(() -> statusTv.setText("准备就绪！可以播放了"));
        });
    }

    // 【新增】动态从 res/raw 文件夹读取对应的 mp3/ogg
    private void loadSoundForKey(String keyName) {
        // 资源文件名必须小写，比如 "M1" 变成 "m1"
        int resId = getResources().getIdentifier(keyName.toLowerCase(), "raw", getPackageName());
        if (resId != 0) {
            int soundId = soundPool.load(this, resId, 1);
            soundMap.put(keyName, soundId);
        }
    }

    private Button createPianoKey(String name) {
        Button btn = new Button(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 150;
        params.height = 150;
        params.setMargins(10, 10, 10, 10);
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

            // 【新增】把别名的声音也映射过去
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

        isTesting = true;
        statusTv.setText("正在播放预览...");
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
            }
            runOnUiThread(() -> {
                isTesting = false;
                statusTv.setText("播放结束");
            });
        }).start();
    }

    private void highlightKey(String note, int duration) {
        Button btn = btnMap.get(note);
        if (btn == null || note.equals("0")) return;

        // 【新增】播放声音！(参数：音频ID, 左声道音量, 右声道音量, 优先级, 循环次数, 播放速度)
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
        // 【新增】退出时释放音频资源，防止内存泄漏
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}