package com.example.nikkiautopiano;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class PianoService extends AccessibilityService {

    public static PianoService instance;
    private WindowManager windowManager;
    private Button floatingButton;
    private HashMap<String, float[]> Nikki_KeyMap = new HashMap<>();
    private Random random = new Random();

    private String currentScoreStr = "[]";
    private boolean isPlaying = false;

    // 用于读取保存的配置
    private static final String PREF_NAME = "PianoAppConfig";
    private static final String KEY_SCORE_PATH = "ScorePath";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        initializeKeyMap();
        showFloatingWindow();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (windowManager != null && floatingButton != null) {
            windowManager.removeView(floatingButton);
        }
        instance = null;
        return super.onUnbind(intent);
    }

    private void showFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingButton = new Button(this);
        floatingButton.setText("▶ 开始");
        floatingButton.setBackgroundColor(Color.parseColor("#88000000"));
        floatingButton.setTextColor(Color.WHITE);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        params.x = 0;
        params.y = 0;

        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;
                            params.x = initialX + (int) deltaX;
                            params.y = initialY + (int) deltaY;
                            windowManager.updateViewLayout(floatingButton, params);
                        }
                        return isDragging;

                    case MotionEvent.ACTION_UP:
                        if (isDragging) return true;
                        return false;
                }
                return false;
            }
        });

        floatingButton.setOnClickListener(v -> {
            if (isPlaying) return;

            // 【核心修复】点击开始时，动态读取最新的曲谱文件内容
            SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String pathStr = sharedPreferences.getString(KEY_SCORE_PATH, "");

            if (TextUtils.isEmpty(pathStr)) {
                Toast.makeText(this, "请先回 App 选择一个乐谱！", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // 读取文件内容转换为字符串
                currentScoreStr = readTextFromUri(Uri.parse(pathStr));
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "文件读取失败，请重新选择", Toast.LENGTH_SHORT).show();
                return;
            }

            floatingButton.setText("3秒后...");
            floatingButton.setEnabled(false);
            new Handler(Looper.getMainLooper()).postDelayed(this::playSongFromJson, 3000);
        });

        windowManager.addView(floatingButton, params);
    }

    // 【新增】万能读取方法，用于将 Uri 转换成真实的 JSON 字符串
    private String readTextFromUri(Uri uri) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }
        return stringBuilder.toString();
    }

    private void playSongFromJson() {
        isPlaying = true;
        String jsonStr = currentScoreStr;

        new Thread(() -> {
            try {
                JSONArray jsonArray = new JSONArray(jsonStr);
                long startTime = System.currentTimeMillis();
                long accumulatedDelay = 0;

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject step = jsonArray.getJSONObject(i);

                    JSONArray notesArray = step.optJSONArray("notes");
                    JSONArray durationsArray = step.optJSONArray("durations");
                    int defaultDuration = step.optInt("duration", 80);
                    int delay = step.optInt("delay", 400);

                    List<float[]> pointsToClick = new ArrayList<>();
                    List<Integer> durationsToClick = new ArrayList<>();

                    if (notesArray != null) {
                        for (int j = 0; j < notesArray.length(); j++) {
                            String noteName = notesArray.getString(j);
                            if (!noteName.equals("0")) {
                                float[] pos = Nikki_KeyMap.get(noteName);
                                if (pos != null) {
                                    pointsToClick.add(new float[]{
                                            pos[0] + random.nextInt(14) - 7,
                                            pos[1] + random.nextInt(14) - 7
                                    });

                                    if (durationsArray != null && j < durationsArray.length()) {
                                        durationsToClick.add(durationsArray.getInt(j));
                                    } else {
                                        durationsToClick.add(defaultDuration);
                                    }
                                }
                            }
                        }
                    }

                    if (!pointsToClick.isEmpty()) {
                        playChord(pointsToClick, durationsToClick);
                    }

                    accumulatedDelay += delay;
                    long targetTime = startTime + accumulatedDelay;
                    long sleepTime = targetTime - System.currentTimeMillis();

                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(instance, "乐谱格式错误，请检查 JSON", Toast.LENGTH_SHORT).show()
                );
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                isPlaying = false;
                floatingButton.setText("▶ 开始");
                floatingButton.setEnabled(true);
            });
        }).start();
    }

    public void playChord(List<float[]> points, List<Integer> durations) {
        if (points == null || points.isEmpty()) return;

        GestureDescription.Builder builder = new GestureDescription.Builder();

        for (int i = 0; i < points.size(); i++) {
            float[] pt = points.get(i);
            int dur = durations.get(i);
            Path path = new Path();
            path.moveTo(pt[0], pt[1]);
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, dur));
        }

        dispatchGesture(builder.build(), null, null);
    }

    private void initializeKeyMap() {
        // [用户保留数据：一加15 的特殊坐标偏移]
        float StartX = 626f;
        float ColGap = 240.5f;
        float StartYTop = 620f;
        float RowGap = 221.5f;
        String[] rowNames = {"T", "M", "B"};

        for (int rowIdx = 0; rowIdx < 3; rowIdx++) {
            float currentY = StartYTop + (rowIdx * RowGap);
            for (int colIdx = 0; colIdx < 7; colIdx++) {
                float currentX = StartX + (colIdx * ColGap);
                String noteName = rowNames[rowIdx] + (colIdx + 1);
                Nikki_KeyMap.put(noteName, new float[]{currentX, currentY});
                if (rowNames[rowIdx].equals("M")) {
                    Nikki_KeyMap.put(String.valueOf(colIdx + 1), new float[]{currentX, currentY});
                }
            }
        }

        Nikki_KeyMap.put("0", new float[]{0f, 0f});

        String[] letters = {"C", "D", "E", "F", "G", "A", "B"};
        for (int i = 0; i < 7; i++) {
            float[] midPos = Nikki_KeyMap.get("M" + (i + 1));
            Nikki_KeyMap.put(letters[i], midPos);
            Nikki_KeyMap.put(letters[i].toLowerCase(), midPos);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}