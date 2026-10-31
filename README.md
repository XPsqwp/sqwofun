# sqwofun

第二个应用（与仓库 `XPsqwp/GlassAnime` 里的 **SQWPFun** 是彼此独立的两套，包名不同、可同时安装）。

- 包名：`com.sqwofun.app`
- 应用名：sqwofun
- 技术栈：Kotlin 2.2.20 / AGP 8.11.1 / Gradle 8.13 / compileSdk 36 / minSdk 31 / Compose BOM 2025.09.00
- 玻璃：`io.github.kyant0:backdrop:1.0.0`（[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)），真实 AGSL 折射，不是模糊滤镜仿制

## 底部导航栏

`app/src/main/java/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt`
来自 AndroidLiquidGlass catalog，融合了 Lanerc 项目的调校：

- 1dp 锐利镜面高光（`Highlight(1dp, blur 2dp, α0.9, 115°)`）
- 底部压暗的厚度感（`InnerShadow(26dp, offset 10dp, 黑 0.62)`）
- 竖向渐变表面 + 外投影
- 选中指示器：可拖拽滑动的玻璃胶囊，`lens(12dp·p, 16dp·p, chromaticAberration)`
- **捕捉层只录玻璃本身（`content = {}`）**：透镜采样源里不含图标文字，所以胶囊里不会再多出第二份灰色图标/文字

## 版本

| 版本 | 内容 |
|---|---|
| 1.0 | 独立应用，Lanerc 玻璃组件 |
| 1.1 | 捕捉层挂到可见行 |
| 1.2 | 捕捉层只录玻璃，去掉胶囊里的灰色副本 |

## 构建

```bash
gradle :app:assembleDebug
```

CI：`.github/workflows/android.yml`，push 到 `main` 自动出 `app-debug` 产物。
