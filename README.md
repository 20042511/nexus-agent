# Nexus — AI Agent OS for Android

> 不是聊天工具。是持续存在、自主运转的 AI Agent。

## 与现有方案的根本区别

| | RikkaHub | Nexus |
|--|---------|-------|
| 存在方式 | 对话触发，结束即死 | ForegroundService，永远活着 |
| 主动性 | 零，等你说话 | 有目标，自己跑 |
| 记忆 | 对话历史（线性） | 四层结构化记忆 |
| 用户关系 | 你问它答 | 你委托目标，它完成后汇报 |
| 感知 | 无 | 多路传感器（时间/网络/通知） |

## 架构

```
┌─────────────────────────────────────────┐
│           用户（委托目标 / 审批决策）      │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         AgentService（心脏）             │
│   ForegroundService · 开机自启           │
│   每30秒驱动一次 ReAct 循环              │
└──────┬───────┬───────┬──────────────────┘
       │       │       │
  SensorHub  Memory  PlannerEngine
  (眼耳)    (大脑)   (规划+ReAct)
       │       │       │
       └───────┴───────┘
               │
         ToolRegistry
    ┌──────────┼──────────┐
  ShellTool HttpTool  NotifyTool
  (workspace) (出网)   (推送)
```

## 核心文件

```
service/
  AgentService.kt      ← 心脏，ForegroundService
  BootReceiver.kt      ← 开机自启
  CronScheduler.kt     ← Cron 调度

sensor/
  SensorHub.kt         ← 感知层（时间/HTTP/通知）

planner/
  PlannerEngine.kt     ← ReAct 推理循环

tool/
  ToolRegistry.kt      ← 12个内置工具

data/
  model/Models.kt      ← 数据模型（Goal/Task/Memory）
  db/NexusDatabase.kt  ← Room 数据库

ui/
  screen/HomeScreen.kt ← 仪表盘 UI（非聊天界面）
```

## 内置工具（Agent 的手脚）

| 工具 | 能力 |
|------|------|
| shell_exec | 在 proot workspace 执行任意命令 |
| http_get | GET 任意 URL |
| http_post | POST 任意 URL |
| file_read | 读 workspace 文件 |
| file_write | 写 workspace 文件 |
| notify_user | 推送消息给用户（App内+ntfy.sh） |
| memory_search | 搜索长期记忆 |
| memory_write | 写入长期记忆 |
| github_api | GitHub REST API |
| arxiv_search | arXiv 论文搜索 |
| pypi_lookup | PyPI 包查询 |
| ntfy_push | ntfy.sh 推送 |

## 使用流程

1. 打开 App → Agent 自动在后台启动
2. 点 + → 下达一个目标（"每天早上给我 AI 简报"）
3. 设置触发方式（立即/定时/事件）
4. 关掉 App，Agent 自己跑
5. 完成后收到通知，打开看结果
6. 需要你决策时，Agent 主动弹出"授权"卡片

## 技术栈

- Kotlin + Coroutines
- Jetpack Compose（UI）
- Room（持久化）
- WorkManager（可靠定时）
- Hilt（依赖注入）
- ForegroundService（永久运行）
