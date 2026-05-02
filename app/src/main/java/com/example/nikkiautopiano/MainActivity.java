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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
    }

    private void loadSavedConfig() {
        String savedPath = sharedPreferences.getString(KEY_SCORE_PATH, "");
        if (!TextUtils.isEmpty(savedPath)) {
            tvScorePath.setText("当前曲谱: \n" + savedPath);
        } else {
            tvScorePath.setText("当前曲谱: 未选择");
        }
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

        btnSelectScore.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_CODE_SELECT_FILE);
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

        btnTestPlay.setOnClickListener(v -> {
            // 【修改点】先检查有没有选择文件
            String currentPath = sharedPreferences.getString(KEY_SCORE_PATH, "");
            if (TextUtils.isEmpty(currentPath)) {
                Toast.makeText(this, "请先选择曲谱文件", Toast.LENGTH_SHORT).show();
                return;
            }
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