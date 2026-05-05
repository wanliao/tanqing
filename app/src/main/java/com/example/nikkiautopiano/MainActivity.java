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
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import java.io.File;

public class MainActivity extends AppCompatActivity {

    private Button btnAccessibility;

    private TextView tvScorePath;
    private Button btnSelectScore;
    private Button btnStartService;
    private Button btnTestPlay;

    private static final int REQUEST_CODE_SELECT_FILE = 1001;
    private static final String PREF_NAME = "PianoAppConfig";
    private static final String KEY_SCORE_PATH = "ScorePath";
    private Button btnCloudScores;
    private SharedPreferences sharedPreferences;
    // 在其他按钮声明的下面加上这个
    private android.widget.Switch switchCalibration;
    private Button btnImportLocal;
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
        copyAssetsToLocal();
        initViews();
        loadSavedConfig();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityButtonState();
    }
    // ================= 释放自带曲谱到本地目录 =================
    private void copyAssetsToLocal() {
        // getExternalFilesDir(null) 就是 Android/data/com.example.nikkiautopiano/files 目录
        File targetDir = getExternalFilesDir(null);
        if (targetDir == null) return;

        SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 如果已经释放过了，就不重复释放了，省时间
        if (sp.getBoolean("isAssetsCopied", false)) {
            return;
        }

        new Thread(() -> {
            try {
                String[] files = getAssets().list("zhiyuan");
                if (files != null) {
                    for (String fileName : files) {
                        InputStream is = getAssets().open("zhiyuan/" + fileName);
                        File outFile = new File(targetDir, fileName);
                        FileOutputStream fos = new FileOutputStream(outFile);
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                        is.close();
                        fos.flush();
                        fos.close();
                    }
                }
                sp.edit().putBoolean("isAssetsCopied", true).apply();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    private void initViews() {
        btnAccessibility = findViewById(R.id.btn_accessibility);
        tvScorePath = findViewById(R.id.tv_score_path);
        btnSelectScore = findViewById(R.id.btn_select_score);
        btnStartService = findViewById(R.id.btn_start_service);
        btnTestPlay = findViewById(R.id.btn_test_play);
        switchCalibration = findViewById(R.id.switch_calibration);
        // 【修复 1】改成和 XML 里一致的 btn_download_score
        btnCloudScores = findViewById(R.id.btn_download_score);
        // 加上这一行
        btnImportLocal = findViewById(R.id.btn_import_local);
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
        // 无障碍服务按钮逻辑
        btnAccessibility.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled()) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                Toast.makeText(this, "请在列表中找到【自动弹琴小助手】并开启服务", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "无障碍服务已经处于开启状态！", Toast.LENGTH_SHORT).show();
            }
            // 【已修复】：去掉了这里原本误放的系统文件选择器代码
        });

        // 监听开关状态变化并保存
        switchCalibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(KEY_ENABLE_CALIBRATION, isChecked).apply();
            if (isChecked) {
                Toast.makeText(this, "已开启！现在可以长按悬浮窗进行校准了", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已关闭防误触", Toast.LENGTH_SHORT).show();
            }
        });

        // 读取 Android/data/包名/files 目录的弹窗逻辑
        btnSelectScore.setOnClickListener(v -> {
            try {
                File dir = getExternalFilesDir(null);
                if (dir != null && dir.exists()) {
                    // 只列出 json 和 txt 文件
                    File[] files = dir.listFiles((d, name) -> name.endsWith(".json") || name.endsWith(".txt"));

                    if (files != null && files.length > 0) {
                        String[] fileNames = new String[files.length];
                        for (int i = 0; i < files.length; i++) {
                            fileNames[i] = files[i].getName();
                        }
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("请选择曲谱 (本地存储)")
                                .setItems(fileNames, (dialog, which) -> {
                                    String selectedFile = fileNames[which];
                                    sharedPreferences.edit().putString(KEY_SCORE_PATH, selectedFile).apply();
                                    tvScorePath.setText("当前曲谱: \n" + selectedFile);
                                    Toast.makeText(this, "已选择: " + selectedFile, Toast.LENGTH_SHORT).show();
                                })
                                .show();
                    } else {
                        Toast.makeText(this, "本地目录没找到曲谱，请下载", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "读取本地目录失败", Toast.LENGTH_SHORT).show();
            }
        });

        // 准备开始弹琴按钮逻辑
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

        // 测试按钮逻辑
        btnTestPlay.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TestPlayActivity.class);
            startActivity(intent);
        });

        // 云端获取按钮逻辑
        btnCloudScores.setOnClickListener(v -> {
            Toast.makeText(this, "正在连接服务器获取曲谱...", Toast.LENGTH_SHORT).show();
            fetchCloudScoreList();
        });

        // 【新增】：正确的导入本地曲谱按钮逻辑
        btnImportLocal.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*"); // 允许选择所有类型文件
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "选择曲谱文件"), REQUEST_CODE_SELECT_FILE);
        });
    }

    // ================= 云端下载曲谱核心代码 =================
    // ================= 云端下载曲谱核心代码 =================
    // ================= 云端下载曲谱核心代码 =================
    private void fetchCloudScoreList() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://38.55.147.9:3456/scores/list.json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setRequestMethod("GET");

                // 【核心修复】：在进入 UI 线程之前，提前把状态码拿出来存好
                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    org.json.JSONArray jsonArray = new org.json.JSONArray(response.toString());
                    String[] cloudFiles = new String[jsonArray.length()];
                    for (int i = 0; i < jsonArray.length(); i++) {
                        cloudFiles[i] = jsonArray.getString(i);
                    }

                    runOnUiThread(() -> showCloudFilesDialog(cloudFiles));
                } else {
                    // 这里直接使用刚刚存好的 responseCode 变量，就不再涉及网络调用，自然也不会报错了
                    runOnUiThread(() -> Toast.makeText(this, "获取失败，状态码: " + responseCode, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "网络连接异常，请检查 Nginx 服务器是否开启", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showCloudFilesDialog(String[] files) {
        if (files == null || files.length == 0) {
            Toast.makeText(this, "云端暂时没有曲谱", Toast.LENGTH_SHORT).show();
            return;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("请选择要下载的曲谱")
                .setItems(files, (dialog, which) -> {
                    String selectedFile = files[which];
                    downloadScoreFromServer(selectedFile);
                })
                .show();
    }

    // 【修改版】下载逻辑：存入外部私有目录
    private void downloadScoreFromServer(String fileName) {
        Toast.makeText(this, "开始下载: " + fileName, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://38.55.147.9:3456/scores/" + fileName);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();

                    // 【核心修改】改成保存到 Android/data/包名/files 目录
                    File targetFile = new File(getExternalFilesDir(null), fileName);
                    FileOutputStream fos = new FileOutputStream(targetFile);

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "下载成功！", Toast.LENGTH_SHORT).show();
                        sharedPreferences.edit().putString(KEY_SCORE_PATH, fileName).apply();
                        tvScorePath.setText("当前曲谱: \n" + fileName + " (云端/本地)");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
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
    // 弹出重命名对话框
    private void showRenameDialog(Uri fileUri) {
        android.widget.EditText editText = new android.widget.EditText(this);
        editText.setHint("导入的曲谱");

        new android.app.AlertDialog.Builder(this)
                .setTitle("给曲谱起个名字")
                .setMessage("请输入曲谱名称（不需要加.json）")
                .setView(editText)
                .setPositiveButton("确定", (dialog, which) -> {
                    String inputName = editText.getText().toString().trim();
                    if (TextUtils.isEmpty(inputName)) {
                        inputName = "导入的曲谱"; // 默认名字
                    }
                    saveFileToLocal(fileUri, inputName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 执行文件拷贝和重命名
    private void saveFileToLocal(Uri srcUri, String newName) {
        try {
            // 确保名字以 .json 结尾，方便我们之前的代码识别
            if (!newName.endsWith(".json") && !newName.endsWith(".txt")) {
                newName += ".json";
            }

            InputStream is = getContentResolver().openInputStream(srcUri);
            File destFile = new File(getExternalFilesDir(null), newName);
            FileOutputStream fos = new FileOutputStream(destFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            is.close();
            fos.flush();
            fos.close();

            // 自动选中这个新导入的曲谱
            sharedPreferences.edit().putString(KEY_SCORE_PATH, newName).apply();
            tvScorePath.setText("当前曲谱: \n" + newName + " (已导入)");
            Toast.makeText(this, "导入成功并已选中！", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                // 用户选完文件了，弹出输入框问他叫什么名字
                showRenameDialog(data.getData());
            }
        }
    }
}