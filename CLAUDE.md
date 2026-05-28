# AndroidGo — 围棋棋盘 Android App

## 项目简介

Android 围棋棋盘应用。使用 Kotlin + Jetpack Compose，Canvas 自绘棋盘和棋子。

## 技术栈

- 语言：Kotlin
- UI：Jetpack Compose（Material 3）
- 最低 SDK：24（Android 7.0）
- 目标 SDK：36
- 构建：Gradle Kotlin DSL（`build.gradle.kts`）

## 目录结构

```
app/src/main/java/org/snailtrail/androidgo/
├── MainActivity.kt
├── game/           # 游戏逻辑
│   ├── BoardState      # 棋盘状态
│   └── StoneColor      # 棋子颜色（黑/白）
└── ui/
    ├── board/       # 棋盘渲染
    │   └── GoBoard.kt  # Canvas 绘制 + 触控手势
    └── theme/       # Material 3 主题
```

## 构建

用 Android Studio 打开项目根目录 `AndroidGo/`，或命令行：

```bash
./gradlew assembleDebug
```

## 棋盘渲染约定

- 棋盘底色：`BoardBackground = Color(0xFFDEB887)`（木色）
- 黑子：深灰渐变 `#4A4A4A → #1A1A1A`
- 白子：白灰渐变 `#FFFFFF → #C8C8C8`
- 星位：9/13/19 路各有预定义坐标
- 坐标标签：A-S（列）、1-19 倒序（行）
- 手势：`detectTapGestures`（落子）+ `detectTransformGestures`（缩放/平移）

## 编码约定

- Compose 组件用 `@Composable` 函数，文件一个主组件
- 颜色和常量定义为顶层 `private val`
- 棋盘坐标 (x, y) = (列, 行)，x 从左到右，y 从上到下
