# AndroidGo — 三引擎围棋应用

## 技术栈

Kotlin + Jetpack Compose (Material 3) + MVI + C++17 GTP Client (JNI/CMake)
双引擎 dlopen+pthread 同进程：GNU Go 3.8 + KataGo v1.16.5 (CPU Eigen / GPU OpenCL)
NDK r27 · minSdk 26 · targetSdk 36 · arm64-v8a only · 16KB ELF 对齐 · MIT License

## 源码结构

```
app/src/main/
├── cpp/                          # JNI bridge (Gradle CMake 构建)
│   ├── gtp_client.cpp/h          # GTP 客户端：pipe、超时、interrupt
│   └── jni_bridge.cpp            # JNI 入口，dlopen 引擎 .so
├── java/org/snailtrail/androidgo/
│   ├── MainActivity.kt           # ~120 行薄壳，组装 Compose UI
│   ├── GameViewModel.kt          # MVI: UiState + GameEvent + AppBusyState
│   ├── MainScreen.kt             # 对弈主屏：GoBoard + 信息卡 + 按钮
│   ├── HistoryScreen.kt          # 历史列表：加载/复盘/删除
│   ├── engine/
│   │   ├── EngineManager.kt      # GTP 代理 + 单线程串行队列 (limitedParallelism(1))
│   │   ├── GtpEngine.kt          # 单引擎实例管理
│   │   └── ModelConfig.kt        # 模型名 + 难度→visits 映射（make model 生成）
│   ├── game/
│   │   ├── GoGame.kt             # 棋盘状态、规则（中国规则数子法）
│   │   ├── GoUtils.kt            # 坐标变换
│   │   ├── BoardAnalysis.kt      # 局面评估数据结构
│   │   ├── SgfUtil.kt            # SGF 读写
│   │   └── SgfConstants.kt       # SGF 常量
│   └── ui/
│       ├── board/GoBoard.kt      # 棋盘 Compose 绘制
│       ├── GameInfoBar.kt        # 对局信息栏
│       ├── GameSettingsDialog.kt # 新局/设置对话框（棋盘、让子、引擎、后端、难度）
│       ├── TitleBar.kt           # 顶栏：+⚙💾📜ℹ️ 五按钮
│       └── theme/                # Material3 主题
└── jniLibs/arm64-v8a/            # 引擎 .so + libc++_shared.so (不提交 git)
```

## 构建

```bash
export ANDROID_NDK_HOME=~/Android/Sdk/ndk/27.0.12077973

make menuconfig   # 选 KataGo 模型（b10c128/b15c192/b20c256）
make engines      # 编三个引擎 + 模型 + libc++_shared.so
make all          # engines + apk
make install      # apk + adb install
make run          # 启动 app
make clean        # 清除所有构建产物
make verify       # 检查 .so 符号导出
```

`make apk` 前必须先 `make engines`（guard-assets 检查，不会自动触发长时间编译）。
模型文件 (169MB) 已提交 git，clone 即可构建，无需外部下载。

## 核心设计规则

### 引擎操作
- **GtpEngine 不对外暴露**，所有操作通过 `EngineManager` 代理方法
- **GTP 串行队列**：`limitedParallelism(1)`，所有 GTP 命令在此队列执行
- **引擎同实例复用**多回合，genmove 失败才重启
- **数子/评估只用主引擎**，不起临时引擎（避免模型文件竞争）

### 数子
- 公式：`(黑-白)/2 - 贴子 + 让子还子`
- 盘中：KataGo → `kata-analyze` 评估；GNU Go → 传统数子（`final_status_list dead`）
- 终局两连 pass → 自动触发终局数子（传统 `final_status_list dead`）
- SGF 保存 `AE[engine_type:difficulty]`，加载时恢复引擎类型和难度
- 走子回放用 `moveHistory`（List，有序），不用 `state.stones`（HashMap）
- 让子通过 `setFixedHandicap` GTP 命令设置

### UI
- Material3 原生控件，三页：对弈 · 历史 · 复盘
- `AppBusyState` 统一管理忙状态（替代 8 个分散 flag）
- AI 思考中显示遮罩 + "AI思考中…"，引擎初始化显示 "引擎启动中…"
- 中英双语（跟随系统）

## 当前版本：v1.0.1 (2026-06-08)

最新未发布变更（MVI 重构、GTP 代理封装、评估/数子分离、复手信息卡等）见 RELEASE.md `[Unreleased]` 段。

## 已知问题（按优先级）

| 优先级 | 项 | 状态 |
|--------|---|------|
| P0 | GPU 性能调参 | 待继续 |
| P0 | 多 GPU 厂商兼容测试（Mali/PowerVR） | 未开始 |
| P1 | OpenCL tuning 阶段超 60s | 待优化 |
| P1 | 引擎层单元测试 | 未开始 |
| P2 | 定时器（byo-yomi） | 未开始 |
| P2 | SGF 分享/导入 | 未开始 |
| P2 | 棋谱分支树可视化 | 未开始 |
| P3 | 棋盘动画、音效 | 未开始 |
