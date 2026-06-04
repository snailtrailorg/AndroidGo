# AndroidGo v1.0

**发布日期**: 2026-06-04

首个正式版本。三引擎围棋应用，中国规则数子法。

---

## 核心功能

- **三引擎支持**: GNU Go 3.8、KataGo v1.16.5 CPU (Eigen)、KataGo v1.16.5 GPU (OpenCL)
- **CPU/GPU 后端切换**: 选 KataGo 时可独立选择算力后端
- **GTP 协议完整实现**: C++ JNI 桥接，dlopen + pthread 同进程运行
- **中国规则数子法**: 死子判定、贴子/还子、中盘/终局计分
- **SGF 完整读写**: 保存、加载、复盘 (delta replay)
- **中英双语**: 跟随系统语言
- **Material 3 UI**: RoleChip 统一风格，响应式布局

## 技术参数

| 参数 | 值 |
|------|-----|
| 棋盘大小 | 9/13/19 路 |
| 让子 | 0–9 |
| 难度 | 1–10 (KataGo: 500–5000 visits) |
| 引擎 | GNU Go / KataGo |
| 后端 | CPU (Eigen) / GPU (OpenCL) |
| GPU 兼容 | Qualcomm Adreno ✅ ; ARM Mali / PowerVR 待测试 |
| 最低系统 | Android 8.0 (API 26) |
| 架构 | arm64-v8a |

## 构建

详见 [BUILD.md](BUILD.md)。

```bash
export ANDROID_NDK_HOME=~/Android/Sdk/ndk/27.0.12077973
make -C jni/katago all    # 构建引擎
./gradlew assembleRelease # 构建 APK
```

## 已知限制

- GPU 后端小访问量时性能不及 CPU（~500 visits 以下）
- Mali/PowerVR GPU 未经过兼容性测试
- 不包含定时器 (byo-yomi)
- 复盘加载后引擎/后端配置可能丢失

## 变更历史

### v1.0 (2026-06-04)

**新增**:
- OpenCL GPU 加速后端 (runtime dispatch table)
- CPU/GPU 后端选择器 (开局对话框)
- KataGo Eigen CPU 后端（独立 .so）
- 引擎初始化进度提示 (棋盘转圈)
- 难度 1-10 → maxVisits 500-5000 指数映射
- 构建系统: Makefile + CMake，一键构建双后端
- BUILD.md 完整构建文档
- 多语言字符串全面对齐 (90 keys)

**修复**:
- Genmove 双发导致 GTP 协议乱序和引擎崩溃
- 切换引擎时 goGame.pass() 污染新局状态
- engineManager.close() 阻塞主线程
- 数据库存 0 时旧协程残留

**优化**:
- fork+exec 删除，统一 dlopen+pthread
- 读取超时从 60s 增加到 120s
- UI 硬编码文本全部迁移到 stringResource
- 引擎命名规范化: `libgnugo`, `libkatago_cpu`, `libkatago_gpu`
- 资产提取改为原子写入 (tmp→rename)
- Makefile 支持增量编译
- CMake GLOB 添加 CONFIGURE_DEPENDS

**审查**:
- 22 项代码审查，修 8 项，14 项记录不改原因

---

## License

MIT
