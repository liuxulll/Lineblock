package com.lineblock.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 悬浮窗服务 —— 本应用的核心
 *
 * 职责：
 *   1. 启动一个前台服务，添加 TYPE_APPLICATION_OVERLAY 类型的悬浮窗
 *   2. 悬浮窗是一个 View，宽度=屏幕宽，高度=用户设置的厚度（3~10px）
 *   3. View 背景完全透明（调试模式下显示灰色边框）
 *   4. 拦截悬浮窗区域内的所有触摸事件
 *   5. 识别长按 1 秒 → 进入拖动模式
 *   6. 识别单击 → 弹出控制菜单
 *   7. 响应 Activity 端发来的更新指令（厚度/透明度/锁定/边框）
 */
public class FloatWindowService extends Service {

    private static final String TAG = "FloatWindowService";

    /* ============== Intent action & extras ============== */
    public static final String ACTION_UPDATE   = "com.lineblock.app.action.UPDATE";
    public static final String EXTRA_TYPE      = "type";
    public static final String EXTRA_VALUE     = "value";

    public static final String TYPE_THICKNESS  = "thickness";
    public static final String TYPE_ALPHA      = "alpha";
    public static final String TYPE_LOCKED     = "locked";
    public static final String TYPE_BORDER     = "border";

    /* ============== 通知相关 ============== */
    private static final String CHANNEL_ID  = "line_block_channel";
    private static final int    NOTIF_ID    = 1;

    /* ============== 触摸相关常量 ============== */
    private static final long LONG_PRESS_MS = 1000L;        // 长按 1 秒进入拖动
    private static final int  SLOP          = 20;            // 移动超过 20px 视为滑动（非长按）
    private static final int  VIBRATE_MS    = 50;            // 长按反馈震动时长

    private WindowManager mWindowManager;
    private View mLineView;                  // 悬浮窗 View
    private WindowManager.LayoutParams mLp;  // 悬浮窗布局参数
    private SettingsManager mSettings;
    private int mScreenHeight;               // 屏幕高度（像素）
    private int mScreenWidth;                // 屏幕宽度
    private Vibrator mVibrator;
    private PopupWindow mMenuPopup;          // 控制菜单
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) {
        return null; // 不需要绑定
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务 onCreate");
        mSettings   = SettingsManager.getInstance(this);
        mVibrator   = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // 启动前台服务（Android 8+ 必须）
        startInForeground();

        // 屏幕尺寸
        DisplayMetrics dm = getResources().getDisplayMetrics();
        mScreenWidth  = dm.widthPixels;
        mScreenHeight = dm.heightPixels;

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 构建并显示悬浮窗
        mLineView = createLineView();
        mLp       = createLayoutParams();
        try {
            mWindowManager.addView(mLineView, mLp);
        } catch (Exception e) {
            Log.e(TAG, "添加悬浮窗失败（可能是权限未授予）", e);
            Toast.makeText(this, "添加悬浮窗失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    /**
     * 启动前台服务 —— 通知用户本应用正在运行（Android 8+ 系统强制要求）
     */
    private void startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "悬浮窗服务",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("用于保持悬浮窗运行");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification n = b.setContentTitle("LineBlock 运行中")
                .setContentText("点击打开主界面")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        // Android 10（API 29）要求指定 foregroundServiceType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    /**
     * 构造悬浮窗 LayoutParams
     */
    private WindowManager.LayoutParams createLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;  // Android 8+ 悬浮窗专用类型
        lp.format = PixelFormat.TRANSLUCENT;                            // 透明像素格式
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE        // 不获取输入焦点（避免拦截返回键）
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL       // 触摸只命中我们自己的 View
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN      // 延伸到屏幕区域
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;     // 允许在屏幕外
        lp.gravity = Gravity.TOP | Gravity.START;                      // 锚点为左上角
        lp.width  = WindowManager.LayoutParams.MATCH_PARENT;            // 宽度=屏幕宽
        lp.height = mSettings.getThickness();                           // 高度=设置厚度
        // 根据保存的比例计算 Y 坐标
        lp.y = (int) (mScreenHeight * mSettings.getYRatio()) - lp.height / 2;
        if (lp.y < 0) lp.y = 0;
        return lp;
    }

    /**
     * 构造悬浮窗 View（极简：自己 new 一个 View，自管 onTouchEvent）
     *   - 背景完全透明（或调试时显示灰线）
     *   - 触摸事件完全消费
     */
    private View createLineView() {
        // 不在这里设 LayoutParams，最终生效的是外部 mLp
        return new LineView(this);
    }

    /**
     * 悬浮窗 View 实现 —— 负责绘制 + 触摸识别
     *
     * 触摸状态机：
     *   DOWN  → 启动 1 秒长按定时器，记录初始位置
     *   MOVE  → 移动超过 slop → 取消长按；处于拖动模式 → 实时更新 Y
     *   UP    → 触发长按：保存位置；未触发长按且未明显移动：单击 → 弹菜单
     */
    private class LineView extends View {

        private float mDownRawX, mDownRawY;
        private int   mDownY;             // 触摸按下时 LayoutParams.y
        private boolean mMoved;           // 是否明显移动过
        private boolean mDragging;        // 是否处于拖动模式

        private final GestureDetector mGestureDetector;
        private final int mTouchSlop;

        LineView(Context ctx) {
            super(ctx);
            // 保证能接收点击事件（否则某些设备上onTouchEvent不会被调用）
            setClickable(true);
            setFocusable(true);

            // 初始化背景：调试边框/透明
            applyBackground();

            mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

            mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    // 单击直接弹菜单（只有在没有明显移动且未处于拖动时)
                    if (!mMoved && !mDragging) {
                        showMenu();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    // 长按进入拖动（受锁定限制）
                    if (!mSettings.isLocked()) {
                        mDragging = true;
                        doVibrate();
                        Log.d(TAG, "进入拖动模式 (GestureDetector)");
                    }
                }
            });
        }

        /** 根据"是否显示边框"设置背景：显示=灰线，隐藏=完全透明 */
        private void applyBackground() {
            if (mSettings.isShowBorder()) {
                // 调试模式：显示一条半透明灰线，方便定位
                setBackgroundColor(Color.argb(160, 200, 200, 200));
            } else {
                // 正常使用：完全透明
                setBackgroundColor(Color.TRANSPARENT);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // 先让 GestureDetector 识别单击/长按
            mGestureDetector.onTouchEvent(event);

            // 如果锁定了，则只允许"单击弹菜单"，不允许拖动
            boolean locked = mSettings.isLocked();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    mDownRawX = event.getRawX();
                    mDownRawY = event.getRawY();
                    mDownY    = mLp.y;
                    mMoved    = false;
                    // 注意：不再使用手动长按定时器（GestureDetector 处理）
                    return true; // 消费
                }

                case MotionEvent.ACTION_MOVE: {
                    float dx = Math.abs(event.getRawX() - mDownRawX);
                    float dy = Math.abs(event.getRawY() - mDownRawY);
                    if (dx > mTouchSlop || dy > mTouchSlop) {
                        mMoved = true;
                    }
                    if (mDragging && !locked) {
                        int newY = (int) (mDownY + (event.getRawY() - mDownRawY));
                        // 限制在屏幕内（顶部不能超出状态栏区域，底部不能超出屏幕）
                        if (newY < 0) newY = 0;
                        if (newY > mScreenHeight - getHeight())
                            newY = mScreenHeight - getHeight();
                        mLp.y = newY;
                        mWindowManager.updateViewLayout(this, mLp);
                    }
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    // 如果在拖动中，保存位置；否则单击由 GestureDetector 的 onSingleTapConfirmed 处理
                    if (mDragging) {
                        mDragging = false;
                        float ratio = mLp.y / (float) mScreenHeight;
                        mSettings.setYRatio(ratio);
                        Log.d(TAG, "保存位置 ratio=" + ratio);
                    }
                    return true;
                }
            }
            return true;
        }
    }

    /**
     * 震动反馈
     */
    private void doVibrate() {
        if (mVibrator == null || !mVibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //noinspection deprecation
            mVibrator.vibrate(VIBRATE_MS);
        }
    }

    /**
     * 弹出控制菜单（PopupWindow）
     * 菜单内容：
     *   - 透明度 SeekBar (0~100)
     *   - 线条粗细 SeekBar (3~10)
     *   - 锁定位置 Switch
     *   - 显示边框 Switch（调试用）
     *   - 退出按钮
     */
    private void showMenu() {
        if (mMenuPopup != null && mMenuPopup.isShowing()) {
            mMenuPopup.dismiss();
            return;
        }

        // 用一个最简单的 LinearLayout 充作菜单容器
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // —— 透明度 ——
        TextView tvAlpha = new TextView(this);
        tvAlpha.setText("透明度：" + mSettings.getAlpha() + "%");
        root.addView(tvAlpha);

        SeekBar sbAlpha = new SeekBar(this);
        sbAlpha.setMax(100);
        sbAlpha.setProgress(mSettings.getAlpha());
        sbAlpha.setOnSeekBarChangeListener(new SimpleSeekBar() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                tvAlpha.setText("透明度：" + p + "%");
                mSettings.setAlpha(p);
                // 透明度滑块仅在"显示边框"模式下生效：p 越大边框越明显
                // 主体悬浮窗始终完全透明（Color.TRANSPARENT），不显示任何颜色
                mLineView.setBackgroundColor(mSettings.isShowBorder()
                        ? Color.argb((int) (255 * p / 100f), 200, 200, 200)
                        : Color.TRANSPARENT);
            }
        });
        root.addView(sbAlpha);

        // —— 线条粗细 ——
        TextView tvThick = new TextView(this);
        tvThick.setText("线条粗细：" + mSettings.getThickness() + " px");
        root.addView(tvThick);

        SeekBar sbThick = new SeekBar(this);
        sbThick.setMax(7);               // 0~7 对应 3~10
        sbThick.setProgress(mSettings.getThickness() - 3);
        sbThick.setOnSeekBarChangeListener(new SimpleSeekBar() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                int thickness = p + 3;
                tvThick.setText("线条粗细：" + thickness + " px");
                mSettings.setThickness(thickness);
                mLp.height = thickness;
                mWindowManager.updateViewLayout(mLineView, mLp);
            }
        });
        root.addView(sbThick);

        // —— 锁定位置 ——
        Switch swLocked = new Switch(this);
        swLocked.setText("锁定位置");
        swLocked.setChecked(mSettings.isLocked());
        swLocked.setOnCheckedChangeListener((b, checked) -> mSettings.setLocked(checked));
        root.addView(swLocked);

        // —— 显示边框（调试用）——
        Switch swBorder = new Switch(this);
        swBorder.setText("显示边框（调试）");
        swBorder.setChecked(mSettings.isShowBorder());
        swBorder.setOnCheckedChangeListener((b, checked) -> {
            mSettings.setShowBorder(checked);
            ((LineView) mLineView).applyBackground();
        });
        root.addView(swBorder);

        // —— 退出按钮 ——
        android.widget.Button btnExit = new android.widget.Button(this);
        btnExit.setText("退出 APP");
        btnExit.setOnClickListener(v -> {
            mSettings.setLocked(false); // 避免下次启动时残留
            stopSelf();
            // 退出整个 APP
            android.os.Process.killProcess(android.os.Process.myPid());
        });
        root.addView(btnExit);

        // 测量 root（必须先 measure 才能用 WRAP_CONTENT 风格的高度）
        int menuW = (int) (280 * getResources().getDisplayMetrics().density);
        root.measure(
                View.MeasureSpec.makeMeasureSpec(menuW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int menuH = root.getMeasuredHeight();
        if (menuH <= 0) menuH = ViewGroup.LayoutParams.WRAP_CONTENT;

        mMenuPopup = new PopupWindow(root, menuW, menuH, true);
        mMenuPopup.setOutsideTouchable(true);
        mMenuPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        // 在悬浮窗的上方 200px 位置显示
        int showX = 0;
        int showY = mLp.y - menuH - (int) (40 * getResources().getDisplayMetrics().density);
        if (showY < 0) showY = 0;
        mMenuPopup.showAtLocation(mLineView, Gravity.TOP | Gravity.START, showX, showY);
    }

    /** 简化 SeekBar 监听器（只需重写 onProgressChanged） */
    private static abstract class SimpleSeekBar implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar sb) {}
        @Override public void onStopTrackingTouch(SeekBar sb) {}
    }

    /* ========================================================
     * onStartCommand —— 接收 Activity 端发来的更新指令
     * ======================================================== */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (!ACTION_UPDATE.equals(action)) return START_STICKY;

        String type  = intent.getStringExtra(EXTRA_TYPE);
        int    value = intent.getIntExtra(EXTRA_VALUE, 0);

        if (mLineView == null || mLp == null) return START_STICKY;

        switch (type == null ? "" : type) {
            case TYPE_THICKNESS: {
                mLp.height = value;
                mWindowManager.updateViewLayout(mLineView, mLp);
                break;
            }
            case TYPE_ALPHA: {
                mSettings.setAlpha(value);
                ((LineView) mLineView).applyBackground();
                break;
            }
            case TYPE_LOCKED: {
                mSettings.setLocked(value != 0);
                break;
            }
            case TYPE_BORDER: {
                mSettings.setShowBorder(value != 0);
                ((LineView) mLineView).applyBackground();
                break;
            }
        }
        return START_STICKY;
    }

    /**
     * 对外便捷方法：发送"更新指令"到 Service
     */
    public static void sendUpdate(Context ctx, String type, int value) {
        Intent i = new Intent(ctx, FloatWindowService.class);
        i.setAction(ACTION_UPDATE);
        i.putExtra(EXTRA_TYPE, type);
        i.putExtra(EXTRA_VALUE, value);
        ctx.startService(i);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "服务 onDestroy");
        mHandler.removeCallbacksAndMessages(null);
        if (mMenuPopup != null && mMenuPopup.isShowing()) mMenuPopup.dismiss();
        if (mLineView != null && mWindowManager != null) {
            try { mWindowManager.removeView(mLineView); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
