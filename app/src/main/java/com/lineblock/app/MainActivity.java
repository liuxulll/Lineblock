package com.lineblock.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 主界面 —— 提供两个功能：
 *   1. 申请"显示悬浮窗"权限（Android 6+ 必须）
 *   2. 提供"锁定位置"开关（远程控制悬浮窗是否可拖动）
 *   3. 提供"显示边框"开关（调试用）
 *   4. 提供"启动悬浮窗"按钮
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY_PERMISSION = 1001;

    private SettingsManager mSettings;
    private TextView        mTvStatus;
    private Switch          mSwLocked;
    private Switch          mSwBorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSettings = SettingsManager.getInstance(this);

        mTvStatus = findViewById(R.id.tv_status);
        mSwLocked = findViewById(R.id.sw_locked);
        mSwBorder = findViewById(R.id.sw_border);

        // 锁定开关
        mSwLocked.setChecked(mSettings.isLocked());
        mSwLocked.setOnCheckedChangeListener((CompoundButton btn, boolean checked) -> {
            mSettings.setLocked(checked);
            // 实时同步给 Service
            FloatWindowService.sendUpdate(this, FloatWindowService.TYPE_LOCKED, checked ? 1 : 0);
            Toast.makeText(this, checked ? "已锁定位置" : "已解锁，可拖动", Toast.LENGTH_SHORT).show();
        });

        // 边框开关
        mSwBorder.setChecked(mSettings.isShowBorder());
        mSwBorder.setOnCheckedChangeListener((btn, checked) -> {
            mSettings.setShowBorder(checked);
            FloatWindowService.sendUpdate(this, FloatWindowService.TYPE_BORDER, checked ? 1 : 0);
        });

        // 启动悬浮窗按钮
        findViewById(R.id.btn_start).setOnClickListener(v -> startFloat());
        // 关闭悬浮窗按钮
        findViewById(R.id.btn_stop).setOnClickListener(v -> stopFloat());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到主界面都刷新一下状态显示
        mTvStatus.setText("悬浮窗权限：" + (Settings.canDrawOverlays(this) ? "已授予" : "未授予"));
    }

    /**
     * 启动悬浮窗 —— 先检查权限，没有则跳系统设置
     */
    private void startFloat() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
            return;
        }
        // 用普通 startService，不用 startForegroundService
        // 原因：startForegroundService 要求 5 秒内调 startForeground，否则 ANR 闪退
        // 而 manifest 缺 foregroundServiceType 声明，startForeground 也会被系统拒绝
        Intent i = new Intent(this, FloatWindowService.class);
        startService(i);
        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show();
        // 启动后可以退出主界面
        finish();
    }

    /**
     * 停止悬浮窗
     */
    private void stopFloat() {
        stopService(new Intent(this, FloatWindowService.class));
        Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startFloat();
            } else {
                Toast.makeText(this, "未授予悬浮窗权限，无法启动", Toast.LENGTH_LONG).show();
            }
        }
    }
}
