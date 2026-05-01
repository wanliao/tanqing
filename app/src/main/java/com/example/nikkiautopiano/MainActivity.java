package com.example.nikkiautopiano;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "请先开启悬浮窗权限！", Toast.LENGTH_LONG).show();
        }

        // 2. 创建主界面
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 200, 50, 50);

        Button btnPickFile = new Button(this);
        btnPickFile.setText("📁 选择本地 .json 乐谱文件");
        btnPickFile.setTextSize(18);
        layout.addView(btnPickFile);
        setContentView(layout);

        // 3. 点击按钮去选择文件
        btnPickFile.setOnClickListener(v -> {
            if (PianoService.instance == null) {
                Toast.makeText(this, "无障碍未连接，请去设置里重新开启！", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 1001);
        });
    }

    // 4. 接收选中的文件并读取内容
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                // 将读取到的 JSON 字符串发给后台服务
                PianoService.instance.updateScore(sb.toString());
                Toast.makeText(this, "✅ JSON乐谱加载成功！快切回游戏播放吧", Toast.LENGTH_LONG).show();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "❌ 读取失败，请确保是正确的 json 文本", Toast.LENGTH_SHORT).show();
            }
        }
    }
}