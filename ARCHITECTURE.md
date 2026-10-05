# Nexus — AI Agent OS for Android
## 核心理念

不是"聊天工具"。是一个**在手机上持续存在、自主运转的 AI Agent 操作系统**。

用户和 Agent 的关系：
- 不是"主人与工具"
- 是"指挥官与执行官"
- Agent 有目标、有记忆、有自主行动能力，遇到需要决策的才来找你

---

## 架构层次

```
┌─────────────────────────────────────────────┐
│                  用户层                       │
│  目标下达 / 结果审阅 / 关键决策授权            │
└───────────────┬─────────────────────────────┘
                │
┌───────────────▼─────────────────────────────┐
│              Intent Engine（意图引擎）         │
│  理解用户目标 → 分解为可执行子任务              │
│  决定：现在做 / 等条件触发 / 需要用户确认       │
└───┬───────────┬───────────┬─────────────────┘
    │           │           │
┌───▼───┐  ┌───▼───┐  ┌────▼────┐
│Sensor │  │Memory │  │Executor │
│传感层  │  │记忆层  │  │执行层   │
└───┬───┘  └───┬───┘  └────┬────┘
    │           │           │
┌───▼───────────▼───────────▼─────────────────┐
│              Android Service Layer           │
│  ForegroundService — 永远活着                 │
│  WorkManager — 定时/条件触发                  │
│  Room DB — 持久状态                           │
└─────────────────────────────────────────────┘
```

---

## 五个核心模块

### 1. AgentService（Android Foreground Service）
**这是心脏。**
- 开机自启，永远在后台运行
- 维护 Agent 的运行循环：感知→规划→执行→反馈
- 不依赖 UI，UI 只是它的"显示屏"

### 2. SensorHub（感知层）
Agent 的眼睛和耳朵，持续采集信号：

| 传感器 | 信号类型 | 触发条件 |
|--------|---------|---------|
| TimeSensor | 时间事件 | cron 表达式 |
| NetworkSensor | 外部 API 变化 | HTTP 轮询 diff |
| NotificationSensor | 系统通知 | NotificationListenerService |
| LocationSensor | 地理位置 | Geofence |
| FileSensor | 文件变化 | FileObserver |
| MessageSensor | 用户消息 | 实时 |

### 3. MemoryStore（记忆层）
不是简单的对话历史，是结构化记忆：

```
WorkingMemory    — 当前任务上下文（RAM）
EpisodicMemory   — 发生过什么（Room DB，按时间）
SemanticMemory   — 知道什么（Room DB，向量化）
ProceduralMemory — 怎么做（Skills 目录）
GoalStack        — 当前目标栈（优先级队列）
```

### 4. PlannerEngine（规划层）
Agent 的大脑：
- 接收 Sensor 的信号
- 查询 Memory 的上下文
- 调用 LLM 做决策：「现在应该做什么？」
- 输出 ActionPlan（动作序列）

关键设计：**ReAct 循环**
```
Reason → Act → Observe → Reason → Act → ...
```
每一步都记录，失败了回溯，不是一次性的。

### 5. ToolRegistry（工具层）
Agent 的手脚，可热插拔：

| 工具 | 能力 |
|------|------|
| ShellTool | 在 proot workspace 执行命令 |
| HttpTool | 调用任意 HTTP API |
| FileTool | 读写工作区文件 |
| NotifyTool | 推送通知给用户 |
| LLMTool | 嵌套调用 LLM |
| McpTool | 连接 MCP 服务器 |
| AndroidTool | 系统级操作（需权限） |

---

## 关键突破点

### 突破一：Agent 不因对话结束而死
```kotlin
// AgentService 是 ForegroundService
// 即使 App 关闭，它继续运行
class AgentService : LifecycleService() {
    // 有自己的协程作用域
    // 有自己的运行循环
    // UI 是可选的
}
```

### 突破二：事件驱动而非被动响应
```kotlin
// 不等用户说话，传感器触发 Agent 运行
sensorHub.on(Event.TimeReached("09:00")) {
    agent.run(goal = "整理昨天的信息摘要，推送给用户")
}
sensorHub.on(Event.ApiChanged("ollama/releases")) {
    agent.run(goal = "新版本发布了，通知用户并分析更新内容")
}
```

### 突破三：目标驱动而非指令驱动
```kotlin
// 用户下达目标，Agent 自己规划步骤
user.setGoal("每天早上给我一份 AI 领域简报")
// Agent 自动分解为：
// 1. 07:50 启动 → 抓取 arXiv/GitHub/DEV.to
// 2. 调用 LLM 总结
// 3. 08:00 推送给用户
// 全程无需用户干预
```

### 突破四：工作区是 Agent 的真实身体
```
proot workspace 不是沙箱，是 Agent 的物理身体：
- 可以安装工具（apt install）
- 可以运行服务（python server）
- 可以持久存储（文件系统）
- 可以访问网络（已验证12个平台）
```

---

## 与 RikkaHub 的根本区别

| 维度 | RikkaHub | Nexus |
|------|---------|-------|
| 存在方式 | 对话触发，用完即死 | 持续存在，永远运行 |
| 主动性 | 零，等用户说话 | 有目标，自主行动 |
| 记忆 | 对话历史（线性） | 结构化多层记忆 |
| 工具 | 对话中临时调用 | 注册式，随时可用 |
| 身份 | 工具 | 执行官 |
| 用户关系 | 主动问→被动答 | 委托目标→自主完成→汇报 |
