package com.lineblock.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 设置管理器（单例）
 * 用 SharedPreferences 持久化保存：
 *   - 悬浮窗 Y 坐标（屏幕高度的比例 0~1，跨分辨率保持位置稳定）
 *   - 悬浮窗线条厚度（像素，3~10）
 *   - 悬浮窗透明度（0~100，0=完全透明，100=完全不透明，调试边框时用）
 *   - 是否锁定位置（true=不能拖动，false=可以拖动）
 *   - 是否显示调试边框
 */
public class SettingsManager {

    private static final String PREF_NAME = "line_block_prefs";

    // 默认值
    public static final float  DEFAULT_Y_RATIO      = 0.5f;   // 默认在屏幕中间
    public static final int    DEFAULT_THICKNESS    = 5;      // 默认 5 像素
    public static final int    DEFAULT_ALPHA        = 0;      // 默认完全透明
    public static final boolean DEFAULT_LOCKED      = false;  // 默认不锁定
    public static final boolean DEFAULT_SHOW_BORDER = false;  // 默认不显示边框

    // SharedPreferences key
    private static final String KEY_Y_RATIO      = "y_ratio";
    private static final String KEY_THICKNESS    = "thickness";
    private static final String KEY_ALPHA        = "alpha";
    private static final String KEY_LOCKED       = "locked";
    private static final String KEY_SHOW_BORDER  = "show_border";

    private static SettingsManager sInstance;
    private final SharedPreferences sp;

    private SettingsManager(Context ctx) {
        // MODE_PRIVATE：只有本应用能访问
        sp = ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context ctx) {
        if (sInstance == null) {
            sInstance = new SettingsManager(ctx);
        }
        return sInstance;
    }

    // —— 读取方法 —— //
    public float getYRatio()       { return sp.getFloat(KEY_Y_RATIO, DEFAULT_Y_RATIO); }
    public int   getThickness()    { return sp.getInt(KEY_THICKNESS, DEFAULT_THICKNESS); }
    public int   getAlpha()        { return sp.getInt(KEY_ALPHA, DEFAULT_ALPHA); }
    public boolean isLocked()      { return sp.getBoolean(KEY_LOCKED, DEFAULT_LOCKED); }
    public boolean isShowBorder()  { return sp.getBoolean(KEY_SHOW_BORDER, DEFAULT_SHOW_BORDER); }

    // —— 写入方法 —— //
    public void setYRatio(float y)      { sp.edit().putFloat(KEY_Y_RATIO, y).apply(); }
    public void setThickness(int t)     { sp.edit().putInt(KEY_THICKNESS, t).apply(); }
    public void setAlpha(int a)         { sp.edit().putInt(KEY_ALPHA, a).apply(); }
    public void setLocked(boolean b)    { sp.edit().putBoolean(KEY_LOCKED, b).apply(); }
    public void setShowBorder(boolean b){ sp.edit().putBoolean(KEY_SHOW_BORDER, b).apply(); }
}
