# AndroidGo 架构收敛方案

tag: 整体架构优化之前 → 488e321

## Part A：引擎操作规范

### A1. loadSgf → 三步
lock(Initializing) → await syncEngineToBoard() → unlock(resetToGame) → AI trigger

### A2. loadFromReview → 同上

## Part B：遮罩统一 + 冗余删除

### B1. MainActivity.kt
统一遮罩(Initializing/AiThinking/Evaluating), 0.25 alpha, 32dp spinner
删除 boardLocked(), isAiTurn 参数, busyState 参数传递

### B2. TitleBar.kt
删除 aiThinking/engineInitializing 参数和 menuEnabled

### B3. MainScreen.kt BottomBar
删除 aiThinking/engineInitializing/scoringInFlight 参数

### B4. GameViewModel.kt
删除 boardLocked(), handleClick 中 boardLocked/isAiTurn 检查
保留 isAiTurn() 为 private

### B5. strings
新增 evaluating 文案

## Part C：不改
Engine Close Thread, aiGeneration/aiMoveInFlight, restoreAutosave, busyState 5值

## 改动量
+47 -101 = -54 行净
