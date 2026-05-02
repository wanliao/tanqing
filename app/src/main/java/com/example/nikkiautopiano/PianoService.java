package com.example.nikkiautopiano;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

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
    private boolean isPlaying = false; // 增加播放状态标记

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

    public void updateScore(String newScore) {
        this.currentScoreStr = newScore;
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
            if (currentScoreStr.equals("[]")) {
                Toast.makeText(this, "请先回 App 选择一个乐谱！", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isPlaying) return; // 防止重复点击

            floatingButton.setText("3秒后...");
            floatingButton.setEnabled(false);
            new Handler(Looper.getMainLooper()).postDelayed(this::playSongFromJson, 3000);
        });

        windowManager.addView(floatingButton, params);
    }

    private void playSongFromJson() {
        isPlaying = true;
        String jsonStr = currentScoreStr;

        new Thread(() -> {
            try {
                JSONArray jsonArray = new JSONArray(jsonStr);

                // 【核心优化 1】时间戳比对法，解决越弹越慢的问题
                long startTime = System.currentTimeMillis(); // 记录开场时间
                long accumulatedDelay = 0; // 累计应有的延迟

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject step = jsonArray.getJSONObject(i);

                    JSONArray notesArray = step.optJSONArray("notes");
                    // 【新增】尝试读取专属的 durations 数组
                    JSONArray durationsArray = step.optJSONArray("durations");
                    // 兜底的全局按压时间（如果没写 durations 数组，就用这个）
                    int defaultDuration = step.optInt("duration", 80);
                    int delay = step.optInt("delay", 400);

                    List<float[]> pointsToClick = new ArrayList<>();
                    // 【新增】用来存储每个手指对应按多久的列表
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

                                    // 【核心魔法】决定这个手指按多久
                                    if (durationsArray != null && j < durationsArray.length()) {
                                        // 如果写了 durations 数组，就按数组里的时间来
                                        durationsToClick.add(durationsArray.getInt(j));
                                    } else {
                                        // 否则使用默认的 duration
                                        durationsToClick.add(defaultDuration);
                                    }
                                }
                            }
                        }
                    }

                    if (!pointsToClick.isEmpty()) {
                        // 把坐标和对应的时间一起传给点击函数
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

            // 演奏结束复位
            new Handler(Looper.getMainLooper()).post(() -> {
                isPlaying = false;
                floatingButton.setText("▶ 开始");
                floatingButton.setEnabled(true);
            });
        }).start();
    }

    // 【核心优化 2】多指齐奏与动态时值
    // 【升级版】多指齐奏：支持每根手指独立的按压时长
    public void playChord(List<float[]> points, List<Integer> durations) {
        if (points == null || points.isEmpty()) return;

        GestureDescription.Builder builder = new GestureDescription.Builder();

        for (int i = 0; i < points.size(); i++) {
            float[] pt = points.get(i);
            // 拿到这根手指专属的按压时间
            int dur = durations.get(i);

            Path path = new Path();
            path.moveTo(pt[0], pt[1]);
            // 参数：路径，延迟0毫秒开始，按压 dur 毫秒
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, dur));
        }

        dispatchGesture(builder.build(), null, null);
    }

    private void initializeKeyMap() {
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