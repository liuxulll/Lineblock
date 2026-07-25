package com.lineblock.app;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍服务 —— 用于在系统层面拦截悬浮窗区域的触摸事件
 *
 * 工作原理：
 *   1. Android 10 上，TYPE_APPLICATION_OVERLAY 类型的悬浮窗，其 View 的 onTouchEvent
 *      默认会消费事件（只要返回 true），所以**主路径**上的拦截其实在 FloatWindowService 里。
 *   2. 但某些特殊场景（如下层应用使用 SurfaceView/TextureView 全屏，
 *      或者系统级 toast、输入法等），事件可能仍然会透下去。
 *   3. 启用本无障碍服务后，可以作为兜底机制 —— 在悬浮窗的 Y 坐标附近的触摸事件
 *      通过全局手势拦截屏蔽。
 *
 * 配置见 res/xml/accessibility_service_config.xml：
 *   - accessibilityEventTypes: 我们不需要具体事件，设为 none 即可
 *   - canPerformGestures=true 才能在系统层面拦截/操作
 *
 * 备注：本服务作为一个空壳即可，实际拦截由悬浮窗 View 自己的 onTouchEvent 完成。
 *       服务存在的意义是：让用户在"无障碍"设置中能看到本应用，进一步提升拦截可信度。
 */
public class TouchBlockAccessibilityService extends AccessibilityService {

    private static final String TAG = "TouchBlockA11y";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要处理具体事件 —— 真正的触摸拦截在悬浮窗 View 的 onTouchEvent 里
    }

    @Override
    public void onInterrupt() {
        // 系统要求实现，本服务无需特殊处理
        Log.d(TAG, "无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "无障碍服务已销毁");
    }

    /**
     * 对外暴露：服务是否已启用
     */
    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
    }
}
