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

    private android.widget.ImageView floatingButton; // 改用 ImageView 来显示图标
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
        floatingButton = new android.widget.ImageView(this);

        // === 核心：绘制拟态风格 (Neumorphism) 背景 ===
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(16)); // 圆角矩形
        gd.setColor(Color.parseColor("#E0E5EC")); // 经典的拟态灰白底色
        gd.setStroke(dpToPx(2), Color.parseColor("#FFFFFF")); // 左上角的模拟高光边框

        floatingButton.setBackground(gd);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            floatingButton.setElevation(dpToPx(8)); // 利用系统阴影模拟拟态的暗部阴影
        }

        // 设置内部图标的大小和颜色
        int padding = dpToPx(16);
        floatingButton.setPadding(padding, padding, padding, padding);
        // 初始状态：播放图标
        floatingButton.setImageResource(android.R.drawable.ic_media_play);
        // 给图标上一个拟态风格的凹陷灰色
        floatingButton.setColorFilter(Color.parseColor("#A3B1C6"));

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        // 设置为一个完美的正方形
        int size = dpToPx(45);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size, size, // 宽、高相等
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
                        return false; // 返回 false 以便触发 onClick

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
                        return isDragging;
                }
                return false;
            }
        });

        floatingButton.setOnClickListener(v -> {


            // 【新增停止逻辑】如果正在播放，点击则停止
            if (isPlaying) {
                isPlaying = false;
                // 立刻恢复播放图标
                floatingButton.setImageResource(android.R.drawable.ic_media_play);
                floatingButton.setColorFilter(Color.parseColor("#A3B1C6"));
                Toast.makeText(this, "已强行停止弹琴", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String fileName = sharedPreferences.getString(KEY_SCORE_PATH, "");

            if (TextUtils.isEmpty(fileName)) {
                Toast.makeText(this, "请先回 App 选择一个乐谱！", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                currentScoreStr = readTextFromAssets(fileName);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "曲谱读取失败，请重新选择", Toast.LENGTH_SHORT).show();
                return;
            }

            // 【倒计时状态】不用文字，直接换成系统的同步/等待图标，并锁定按钮
            floatingButton.setImageResource(android.R.drawable.ic_popup_sync);
            floatingButton.setColorFilter(Color.parseColor("#FFA500")); // 变成橙色提示等待
            floatingButton.setEnabled(false);
            Toast.makeText(this, "3秒后开始，请切回游戏...", Toast.LENGTH_SHORT).show();

            new Handler(Looper.getMainLooper()).postDelayed(this::playSongFromJson, 3000);
        });
        // 【修改】：长按悬浮窗，先检查主页的开关是否打开
        floatingButton.setOnLongClickListener(v -> {
            SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            // 读取开关状态，默认为 false
            boolean isCalibrationEnabled = sp.getBoolean("EnableCalibration", false);

            if (isCalibrationEnabled) {
                // 如果开启了，才显示校准界面
                showCalibrationOverlay();
            } else {
                // 如果没开启，提示用户去 App 里打开
                Toast.makeText(this, "校准防误触已开启，请先回 App 主页打开允许校准", Toast.LENGTH_SHORT).show();
            }
            return true; // 消费长按事件
        });
        windowManager.addView(floatingButton, params);
    }

    private void playSongFromJson() {
        isPlaying = true;
        String jsonStr = currentScoreStr;

        // 【正式播放】切换到暂停图标（意思是点击可以停止），并恢复可点击状态，变成醒目的红色/深灰色
        new Handler(Looper.getMainLooper()).post(() -> {
            floatingButton.setImageResource(android.R.drawable.ic_media_pause);
            floatingButton.setColorFilter(Color.parseColor("#FF5252")); // 红色提示正在运行
            floatingButton.setEnabled(true);
        });

        new Thread(() -> {
            try {
                JSONArray jsonArray = new JSONArray(jsonStr);
                long startTime = System.currentTimeMillis();
                long accumulatedDelay = 0;

                for (int i = 0; i < jsonArray.length(); i++) {
                    // 【关键修复】每次执行音符前检查 isPlaying，如果被用户点了停止，立刻跳出循环！
                    if (!isPlaying) {
                        break;
                    }

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

                                        // 获取当前音符配置的原本时长
                                        int currentDur = defaultDuration;
                                        if (durationsArray != null && j < durationsArray.length()) {
                                            currentDur = durationsArray.getInt(j);
                                        }

                                        // 【核心修复】：动态缩短时长，防止手势重叠导致“卡键一直响”
                                        int safeDuration = currentDur;
                                        // 如果配置的按下时长比音符间隔还要长，强制缩短，留出至少 10ms 的空隙给系统执行抬起
                                        if (delay > 0 && safeDuration >= delay) {
                                            safeDuration = delay - 10;
                                        }

                                        // 给一个保底时长，防止遇到极快连音时 duration 变成 0 或负数，导致系统直接不识别这个点击
                                        if (safeDuration < 15) {
                                            safeDuration = 15;
                                        }

                                        durationsToClick.add(safeDuration);
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

            // 播放完毕（或被中断后），恢复成初始的播放图标
            new Handler(Looper.getMainLooper()).post(() -> {
                isPlaying = false;
                floatingButton.setImageResource(android.R.drawable.ic_media_play);
                floatingButton.setColorFilter(Color.parseColor("#A3B1C6")); // 恢复拟态灰
            });
        }).start();
    }

    // 【新增】万能读取方法，用于将 Uri 转换成真实的 JSON 字符串
    private String readTextFromAssets(String fileName) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        // 直接从 assets/zhiyuan/ 拼接文件名进行读取
        try (InputStream inputStream = getAssets().open("zhiyuan/" + fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }
        return stringBuilder.toString();
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
    // 辅助方法：将 dp 转换为物理像素 px，适配不同分辨率屏幕
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }


    // === 新增：坐标校准模式 ===
    private void showCalibrationOverlay() {
        // 防止重复添加
        if (isPlaying) {
            Toast.makeText(this, "请先停止播放再进行校准", Toast.LENGTH_SHORT).show();
            return;
        }

        // 隐藏主悬浮窗
        floatingButton.setVisibility(View.GONE);

        android.widget.FrameLayout overlayLayout = new android.widget.FrameLayout(this);
        // 半透明黑色背景，方便看清游戏按键同时区分校准模式
        overlayLayout.setBackgroundColor(Color.parseColor("#44000000"));

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        // === 替换这部分 overlayParams 的定义 ===
        WindowManager.LayoutParams overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                // 【核心修改】加入 FLAG_LAYOUT_IN_SCREEN 和 FLAG_LAYOUT_NO_LIMITS，允许突破屏幕边界
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        // 【核心修改】适配 Android 9.0 以上的刘海屏/挖孔屏，允许内容延伸到挖孔区域内
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            overlayParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        // =====================================

        // 提取拖拽逻辑为通用监听器
        View.OnTouchListener ballDragListener = new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        // 拖动时给个视觉反馈（稍微放大）
                        view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        view.setX(event.getRawX() + dX);
                        view.setY(event.getRawY() + dY);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        break;
                }
                return true;
            }
        };

        // 创建 T1 小球 (左上角)
        android.widget.TextView ballT1 = createCalibrationBall("T1\n(左上)", "#FF5252");
        ballT1.setX(300); ballT1.setY(300); // 初始大概位置
        ballT1.setOnTouchListener(ballDragListener);

        // 创建 B7 小球 (右下角)
        android.widget.TextView ballB7 = createCalibrationBall("B7\n(右下)", "#448AFF");
        ballB7.setX(800); ballB7.setY(600); // 初始大概位置
        ballB7.setOnTouchListener(ballDragListener);

        // 创建保存按钮
        Button btnSave = new Button(this);
        btnSave.setText("保存坐标并退出");
        btnSave.setBackgroundColor(Color.parseColor("#00E676"));
        btnSave.setTextColor(Color.BLACK);
        android.widget.FrameLayout.LayoutParams btnParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        btnParams.topMargin = dpToPx(30);
        btnSave.setLayoutParams(btnParams);

        btnSave.setOnClickListener(v -> {
            // 获取小球中心点坐标 (加上宽高的一半)
            float t1X = ballT1.getX() + (ballT1.getWidth() / 2f);
            float t1Y = ballT1.getY() + (ballT1.getHeight() / 2f);
            float b7X = ballB7.getX() + (ballB7.getWidth() / 2f);
            float b7Y = ballB7.getY() + (ballB7.getHeight() / 2f);

            // 你的绝佳算法：根据两点算出间距
            float newColGap = (b7X - t1X) / 6f;
            float newRowGap = (b7Y - t1Y) / 2f;

            // 保存到本地
            SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit()
                    .putFloat("StartX", t1X)
                    .putFloat("StartYTop", t1Y)
                    .putFloat("ColGap", newColGap)
                    .putFloat("RowGap", newRowGap)
                    .apply();

            // 重新初始化坐标引擎
            initializeKeyMap();
            Toast.makeText(this, "坐标校准成功！", Toast.LENGTH_SHORT).show();

            // 移除全屏校准界面，恢复主悬浮窗
            windowManager.removeView(overlayLayout);
            floatingButton.setVisibility(View.VISIBLE);
        });

        // 创建取消按钮
        Button btnCancel = new Button(this);
        btnCancel.setText("取消");
        btnCancel.setBackgroundColor(Color.parseColor("#FF5252"));
        btnCancel.setTextColor(Color.WHITE);
        android.widget.FrameLayout.LayoutParams btnCancelParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        btnCancelParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        btnCancelParams.topMargin = dpToPx(90);
        btnCancel.setLayoutParams(btnCancelParams);
        btnCancel.setOnClickListener(v -> {
            windowManager.removeView(overlayLayout);
            floatingButton.setVisibility(View.VISIBLE);
        });

        overlayLayout.addView(ballT1);
        overlayLayout.addView(ballB7);
        overlayLayout.addView(btnSave);
        overlayLayout.addView(btnCancel);

        windowManager.addView(overlayLayout, overlayParams);
    }

    // 辅助方法：画一个漂亮的圆形小球
    private android.widget.TextView createCalibrationBall(String text, String colorHex) {
        android.widget.TextView ball = new android.widget.TextView(this);
        ball.setText(text);
        ball.setTextColor(Color.WHITE);
        ball.setGravity(Gravity.CENTER);
        ball.setTextSize(12);

        int ballSize = dpToPx(60); // 小球大小
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(ballSize, ballSize);
        ball.setLayoutParams(params);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(colorHex));
        gd.setStroke(dpToPx(2), Color.WHITE);
        ball.setBackground(gd);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ball.setElevation(dpToPx(4));
        }
        return ball;
    }

    private void initializeKeyMap() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // 读取保存的坐标，如果没有保存过，就先用默认的兜底
        float StartX = sp.getFloat("StartX", 8f);
        float StartYTop = sp.getFloat("StartYTop", 8f);
        float ColGap = sp.getFloat("ColGap", 8f);
        float RowGap = sp.getFloat("RowGap", 8f);

        String[] rowNames = {"T", "M", "B"};
        Nikki_KeyMap.clear(); // 清空旧数据

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