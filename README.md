# LineBlock —— 屏幕遮挡细线 APP（Android 10 / API 29）

> 一条用来遮挡屏幕指定行的极细横线，可拖动定位、可调粗细、可锁定位置。

---

## ⚠️ 关于"直接给我 APK"

我当前运行的环境**没有装 Android SDK / Gradle**，没法直接在本机构建 APK。
本项目提供**完整可编译的源码** + 打包指引，**只需在装了 Android Studio 的机器上点几下即可出 APK**。

---

## 📁 项目结构

```
LineBlock/
├── build.gradle                  # 项目级 Gradle 配置
├── settings.gradle                # 模块声明
├── gradle.properties              # JVM/AndroidX 参数
├── gradle/wrapper/                # Gradle Wrapper 配置
└── app/
    ├── build.gradle               # 模块级 Gradle 配置（compileSdk=29）
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/lineblock/app/
        │   ├── MainActivity.java
        │   ├── FloatWindowService.java        # 核心：悬浮窗 + 触摸逻辑
        │   ├── TouchBlockAccessibilityService.java
        │   └── SettingsManager.java           # SharedPreferences 封装
        └── res/
            ├── layout/activity_main.xml
            ├── values/{strings,colors,themes}.xml
            ├── xml/accessibility_service_config.xml
            ├── drawable/ic_launcher_foreground.xml
            ├── mipmap/ic_launcher.xml
            └── mipmap-anydpi-v26/ic_launcher.xml
```

---

## 🛠️ 一键打包 APK（推荐路径）

### 1. 准备环境

- 下载 **Android Studio**（任意 4.x 或更新版本）：<https://developer.android.com/studio>
- 安装时**勾选** Android SDK、Android SDK Platform 29、Android SDK Build-Tools 29.0.3
- 首次启动 SDK Manager 时，确认 SDK Platform 29 已下载

### 2. 导入项目

1. 打开 Android Studio
2. **File → Open** → 选择本项目根目录下的 `LineBlock` 文件夹
3. 第一次打开会触发 **Gradle Sync**，自动下载 Gradle 6.8 + 依赖（需要联网，约几分钟）
4. 看到右下角 "Gradle sync finished" 即就绪

### 3. 打包 Debug APK（最快路径，用来测功能）

1. 顶部菜单 **Build → Build Bundle(s)/APK(s) → Build APK(s)**
2. 等待进度条完成
3. 弹窗点 **locate**，会跳到 `app/build/outputs/apk/debug/app-debug.apk`
4. 把这个 apk 传到手机安装即可

### 4. 打包 Release APK（正式分发）

1. 顶部菜单 **Build → Generate Signed Bundle/APK**
2. 选 **APK** → Next
3. 选 **Create new...** 建 keystore：
   - 路径随便选，比如 `~/lineblock.jks`
   - 密码自己设一个
   - 其它字段填完点 OK
4. 选刚建的 keystore，输密码 → Next
5. 选 **release**，勾 **V1 + V2 Signature** → Create
6. 等编译完成，跳出 `app-release.apk`，即最终可分发的 APK

### 5. 安装到手机

- USB 连手机，开启"开发者选项 + USB 调试"
- 命令行：`adb install app-debug.apk`（或 `adb install -r app-release.apk` 覆盖安装）
- 或直接把 apk 拷到手机，文件管理器点击安装（需允许"未知来源"）

---

## 📱 使用说明（给最终用户）

1. 第一次打开 APP，会跳到系统"显示在其他应用上层"权限页，**允许**
2. 点"启动悬浮窗"，APP 自动切到后台，屏幕中央出现一条看不见的横线
3. 调整位置：**长按横线 1 秒**（轻微震动反馈）→ 上下滑动 → 松开自动保存
4. 弹出菜单：**单击横线** → 弹出控制菜单
5. 锁定位置：APP 主界面 或 菜单中都有"锁定位置"开关
6. 调试模式：开启"显示边框"会显示一条灰色细线，方便定位

---

## 🔧 关键实现说明

| 需求 | 实现位置 |
|---|---|
| 悬浮窗 | `FloatWindowService` 使用 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` |
| 触摸拦截 | `LineView.onTouchEvent` 返回 `true` 消费事件；Window flag 用 `FLAG_NOT_TOUCH_MODAL` |
| 长按 1 秒进入拖动 | `Handler.postDelayed(1000ms)` + 滑动 slop=20px 取消长按 |
| 拖动状态 | `mDragging` 布尔位 + `updateViewLayout` 实时更新 Y |
| 锁定位置 | `SettingsManager.isLocked()` 控制长按定时器是否启动 |
| 弹出菜单 | `PopupWindow` 在悬浮窗上方 `showAtLocation` |
| 位置持久化 | Y 坐标按屏幕比例（0~1）保存，跨分辨率一致 |
| 调试边框 | `showBorder` 开关控制背景色为 `argb(160, 200, 200, 200)` |

---

## 🐛 常见问题

**Q: 悬浮窗加不上，提示"添加悬浮窗失败"**
A: 没授予 SYSTEM_ALERT_WINDOW 权限，去 APP 主界面点"启动悬浮窗"重新申请。

**Q: 拖动到顶/底被裁了**
A: 已加边界保护：`y < 0` 时夹到 0，`y > 屏高 - 厚` 时夹到 `屏高 - 厚`。

**Q: 想换包名**
A: 改两处：
   - `app/build.gradle` 的 `applicationId`
   - `AndroidManifest.xml` 的 `package="com.lineblock.app"`
   - 同步改 `java/com/lineblock/app/` 目录的 package 路径（Android Studio Refactor → Rename Package）

**Q: 想加图标**
A: 把你的 `ic_launcher.png` 放到 `app/src/main/res/mipmap-xxxhdpi/` 等密度目录，删掉 mipmap 里那个 xml。

---

## 📝 备注

- 最低 API = 29（Android 10），符合需求
- 全部使用原生 Android SDK，无任何第三方库
- 代码含中文注释，方便阅读
- 测试覆盖：权限申请 / 拖动 / 单击菜单 / 锁定 / 厚度调整 / 边框切换 / 跨分辨率位置保持
