package com.example.nikkiautopiano;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnAccessibility;
    private TextView tvScorePath;
    private Button btnSelectScore;
    private Button btnStartService;
    private Button btnTestPlay;

    private static final int REQUEST_CODE_SELECT_FILE = 1001;
    private static final String PREF_NAME = "PianoAppConfig";
    private static final String KEY_SCORE_PATH = "ScorePath";

    private SharedPreferences sharedPreferences;
    // 在其他按钮声明的下面加上这个
    private android.widget.Switch switchCalibration;
    // 新增一个存储 Key
    private static final String KEY_ENABLE_CALIBRATION = "EnableCalibration";
    // 2. 修改 onCreate 方法：
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 【新增】在 setContentView 之前调用沉浸式代码
        setImmersiveStatusBar();

        setContentView(R.layout.activity_main);

        // 【新增】给最外层的 View 增加一个顶部的 Padding，防止顶部按钮被状态栏挡住
        findViewById(android.R.id.content).setPadding(0, getStatusBarHeight(), 0, 0);

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        initViews();
        loadSavedConfig();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityButtonState();
    }

    private void initViews() {
        btnAccessibility = findViewById(R.id.btn_accessibility);
        tvScorePath = findViewById(R.id.tv_score_path);
        btnSelectScore = findViewById(R.id.btn_select_score);
        btnStartService = findViewById(R.id.btn_start_service);
        btnTestPlay = findViewById(R.id.btn_test_play);
        switchCalibration = findViewById(R.id.switch_calibration);
    }

    private void loadSavedConfig() {
        String savedPath = sharedPreferences.getString(KEY_SCORE_PATH, "");
        if (!TextUtils.isEmpty(savedPath)) {
            tvScorePath.setText("当前曲谱: \n" + savedPath);
        } else {
            tvScorePath.setText("当前曲谱: 未选择");
        }
        boolean isCalibrationEnabled = sharedPreferences.getBoolean(KEY_ENABLE_CALIBRATION, false);
        switchCalibration.setChecked(isCalibrationEnabled);
    }
// 1. 在 MainActivity 类中，加入这两个辅助方法（可以直接放在文件最末尾的最后一个大括号前面）

    // 设置沉浸式状态栏
    private void setImmersiveStatusBar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.view.Window window = getWindow();
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }
    }

    // 获取状态栏高度（用于留出安全距离）
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result == 0 ? 80 : result; // 给个默认值兜底
    }
    private void setupListeners() {
        btnAccessibility.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled()) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                Toast.makeText(this, "请在列表中找到【自动弹琴小助手】并开启服务", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "无障碍服务已经处于开启状态！", Toast.LENGTH_SHORT).show();
            }
        });
        // 新增：监听开关状态变化并保存
        switchCalibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(KEY_ENABLE_CALIBRATION, isChecked).apply();
            if (isChecked) {
                Toast.makeText(this, "已开启！现在可以长按悬浮窗进行校准了", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已关闭防误触", Toast.LENGTH_SHORT).show();
            }
        });

        // 这是我们新改的：读取 assets/zhiyuan 目录的弹窗逻辑
        btnSelectScore.setOnClickListener(v -> {
            try {
                String[] files = getAssets().list("zhiyuan");
                if (files != null && files.length > 0) {
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("请选择曲谱")
                            .setItems(files, (dialog, which) -> {
                                String selectedFile = files[which];
                                sharedPreferences.edit().putString(KEY_SCORE_PATH, selectedFile).apply();
                                tvScorePath.setText("当前曲谱: \n" + selectedFile);
                                Toast.makeText(this, "已选择: " + selectedFile, Toast.LENGTH_SHORT).show();
                            })
                            .show();
                } else {
                    Toast.makeText(this, "没找到曲谱，请检查 assets/zhiyuan 目录", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "读取目录失败", Toast.LENGTH_SHORT).show();
            }
        });

        btnStartService.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "请先开启上方无障碍服务哦", Toast.LENGTH_SHORT).show();
                return;
            }
            String currentPath = sharedPreferences.getString(KEY_SCORE_PATH, "");
            if (TextUtils.isEmpty(currentPath)) {
                Toast.makeText(this, "请先选择曲谱文件", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "准备就绪，可以开始弹琴啦！", Toast.LENGTH_SHORT).show();
        });

        // 【这就是你丢失的那段代码】：点击测试按钮，跳转到 TestPlayActivity
        btnTestPlay.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TestPlayActivity.class);
            startActivity(intent);
        });
    }

    private void updateAccessibilityButtonState() {
        if (isAccessibilityServiceEnabled()) {
            btnAccessibility.setText("无障碍服务: 已开启 (点击可去系统关闭)");
            btnAccessibility.setTextColor(0xFF009900);
        } else {
            btnAccessibility.setText("点击前往开启无障碍服务");
            btnAccessibility.setTextColor(0xFFFF0000);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + PianoService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                String pathString = uri.toString();

                sharedPreferences.edit().putString(KEY_SCORE_PATH, pathString).apply();
                tvScorePath.setText("当前曲谱: \n" + pathString);
                Toast.makeText(this, "曲谱路径已保存！", Toast.LENGTH_SHORT).show();
            }
        }
    }
}