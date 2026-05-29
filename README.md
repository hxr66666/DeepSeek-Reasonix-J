# DeepSeek-Reasonix-j 项目功能介绍

## 项目概述

**DeepSeek-Reasonix-j** 是一个专为 DeepSeek 优化的终端 AI 编程代理，围绕前缀缓存稳定性设计，长会话下 token 成本始终低位运行，可以一直开着使用。照着DeepSeek-Reasonix的java实现（原项目git连接 https://github.com/esengine/DeepSeek-Reasonix.git）

### 核心设计理念

- **有立场而非通用**：每个抽象都由 DeepSeek 特定行为或经济属性驱动
- **DeepSeek-Reasonix-j**：足够便宜的编码代理，可以一直开着而不担心费用
- **故意不做多供应商灵活性**：只做 DeepSeek，绑死一个后端是特性而非限制
- **终端优先**：不做 IDE 集成，diff 在 git diff，文件树在 ls

---

## 核心功能模块

### 1. 缓存优先循环（Pillar 1）

#### 三区域内存架构
```
┌─────────────────────────────────────────┐
│ IMMUTABLE PREFIX                        │ ← 会话固定
│   system + tool_specs + few_shots        │   缓存命中候选
├─────────────────────────────────────────┤
│ APPEND-ONLY LOG                         │ ← 单调增长
│   [assistant₁][tool₁][assistant₂]...    │   保留前一轮前缀
├─────────────────────────────────────────┤
│ VOLATILE SCRATCH                        │ ← 每轮重置
│   R1 thinking, transient plan state      │   从不发送到上游
└─────────────────────────────────────────┘
```

#### 不变量设计
1. 前缀每会话计算一次，哈希并固定
2. 日志条目按追加顺序序列化；无重写
3. 暂存区在任何信息折叠进日志前通过 Pillar 2 蒸馏

#### 工具并行调度
- 工具声明 `parallelSafe?: boolean`（默认 false）
- 循环调度器将连续的并行安全调用分组并通过 `Promise.allSettled` 竞速
- 第一个非并行安全调用结束该组并单独运行（串行屏障 - 读写顺序保留）
- 工具结果产生和历史追加仍按声明顺序落地

**内置并行安全工具**：
- 只读文件系统（`read_file`、`list_directory`、`directory_tree`、`search_files`、`search_content`、`get_file_info`）
- Web（`web_search`、`web_fetch`）
- `recall_memory`、`semantic_search`
- 隔离子循环（`run_skill`、`spawn_subagent`）
- 内存作业查询（`job_output`、`list_jobs`）

**环境变量配置**：
- `REASONIX_PARALLEL_MAX`：默认 3（硬上限 16）
- `REASONIX_TOOL_DISPATCH=serial`：强制串行调度

---

### 2. 工具调用修复（Pillar 2）

针对 DeepSeek 经验性失败模式的四道修复：

#### 2.1 flatten（扁平化）
- 工具注册时自动检测 >10 个叶子参数或深度 >2 的 schema
- 以点表示法呈现给模型
- dispatch() 在调用用户 fn 前重新嵌套参数

#### 2.2 scavenge（ scavenge ）
- 正则 + JSON 解析器扫描 reasoning_content
- 找回模型忘记在 tool_calls 中发出的工具调用

#### 2.3 truncation（截断修复）
- 检测不平衡的 JSON
- 通过关闭括号修复或请求继续完成

#### 2.4 storm（风暴抑制）
- 滑动窗口内相同的 (tool, args) 元组 → 抑制调用
- 注入反思轮

---

### 3. 成本控制（Pillar 3）

#### 3.1 分层默认值（flash-first）
| Preset | Model | Effort | Cost |
|---|---|---|---:|
| `flash` | `v4-flash` | `max` | 1× |
| `auto`（默认） | `v4-flash` → `v4-pro` on hard turns | `max` | 1–3× |
| `pro` | `v4-pro` | `max` | ~12× |

所有辅助调用（`forceSummaryAfterIterLimit`、子代理 spawn、截断修复重试）硬编码 `v4-flash + effort=high`。

#### 3.2 轮结束自动压缩
- 日志中超过 `TURN_END_RESULT_CAP_TOKENS`（3000）的工具结果在轮结束时压缩到该上限
- 模型在读取它的那一轮有完整文本；后续轮看到紧凑摘要，可按需重新读取
- 主动 40% 上下文比例阈值在长多迭代轮内在 80% 紧急阈值触发前提前运行相同的收缩

#### 3.3 模型选择（/model）
- 用户通过 `/model flash` 或 `/model pro` 在 flash 和 pro 间切换（持久化）
- 可在 `.reasonix/settings.json` 的 `model` 键中设置

#### 3.4 模型自报告升级（`<<<NEEDS_PRO>>>`）
- 模型自行决定何时任务超过当前层级
- 两种形式：
  - `<<<NEEDS_PRO>>>`：裸标记，无理由
  - `<<<NEEDS_PRO: <reason>>>>`：包含用户在警告行看到的一句话理由
- 在 pro 层级上，标记是无操作

#### 成本透明度
- 每轮和会话成本在 StatsPanel 中着色：
  - `turn $0.003`：绿色 <$0.05，黄色 $0.05–0.20，红色 ≥$0.20
  - `session $0.12`：相同尺度 ×10

---

## 主要命令和模式

### 核心命令

| 命令 | 何时用 |
|---|---|
| `reasonix code [dir]` | 编码 agent，**先用这个** |
| `reasonix chat` | 纯聊天，不挂文件系统 / shell 工具 |
| `reasonix run "task"` | 一次性，结果流到 stdout，适合 shell 管道 |
| `reasonix doctor` | 体检：Node 版本、API Key、MCP 接线 |
| `reasonix update` | 升级 Reasonix 本身 |

### chat vs. code 模式对比

| 你拿到什么 | `code` | `chat` |
|---|---|---|
| 文件系统工具 + `edit_file` | ✓ | — |
| SEARCH/REPLACE → `/apply` 审阅 | ✓ | — |
| Shell 工具（带 gate） | ✓ | — |
| Plan 模式 · `/todo` · `/skill new` · `/mcp add` | ✓ | — |
| Memory（`remember` / `recall_memory`） | 项目 + 全局 | 仅全局 |
| 配置里的 MCP · web 搜索 · `ask_choice` | ✓ | ✓ |
| 编码导向系统提示词 | ✓ | 通用 |
| Session 作用域 | 按目录 | 共享默认 |

---

## 核心能力详解

### 1. 文件系统工具

- `read_file`：读取文件内容
- `list_directory`：列出目录内容
- `directory_tree`：显示目录树
- `search_files`：搜索文件
- `search_content`：在文件中搜索内容
- `get_file_info`：获取文件信息
- `write_file`：写入文件
- `edit_file`：编辑文件（SEARCH/REPLACE 模式）

### 2. Shell 工具

- `run_command`：运行命令（带审批 gate）
- `run_background`：后台运行命令（JobRegistry）
- `job_output`：获取后台作业输出
- `list_jobs`：列出后台作业

### 3. 记忆系统

- `remember`：记住信息
- `recall_memory`：回忆记忆
- `forget`：忘记记忆
- 记忆分为四类：`user` / `feedback` / `project` / `reference`

### 4. 技能系统

- 模型可以调用的 markdown 剧本
- 两种模式：`inline` 或 `subagent`
- 创建技能：
  ```bash
  /skill new my-skill              # <project>/.reasonix/skills/my-skill.md
  /skill new my-skill --global     # ~/.reasonix/skills，跨项目共用
  ```

### 5. 子代理系统

- `spawn_subagent`：派生隔离子代理
- 默认 `v4-flash + effort=high`
- 子代理 sink 接线支持

### 6. 计划模式

- `submit_plan`：提交计划（带审阅 gate）
- `/todo`：管理待办事项
- 计划检查点确认
- 计划修订

### 7. Web 搜索

- 多引擎支持：Mojeek、SearXNG、Metaso
- `web_search`：网络搜索
- `web_fetch`：获取网页内容

### 8. 代码查询与索引

- Tree-sitter 语法分析
- 代码符号提取
- 语义索引（支持 Ollama 或任何 OpenAI 兼容的 embedding 接口）
- Java 特定支持（`java/` 模块）

### 9. MCP（Model Context Protocol）

- MCP 客户端 + 桥接（stdio + SSE + Streamable HTTP）
- MCP 浏览器和市场
- MCP 健康检查
- MCP 重连机制
- `.mcp.json` 配置支持

---

## 高级功能

### 1. Hooks 系统

生命周期事件触发的 shell 命令：
- `PreToolUse`：工具使用前（可拦截）
- `PostToolUse`：工具使用后
- `UserPromptSubmit`：用户提示提交
- `Stop`：停止

### 2. 权限管理

- 按工作区的 shell 白名单
- 精确前缀匹配
- 编辑工具 gate

### 3. 持久化会话

- JSONL 会话持久化
- 会话重放（`replay` 命令）
- 会话管理（`sessions` 命令）
- 会话修剪（`prune-sessions` 命令）

### 4. 检查点系统

- 自动 checkpoint
- checkpoint 选择器
- 可回滚到任意检查点

### 5. 编辑历史

- `/undo`：撤销
- `/history`：查看历史
- `/show`：显示状态
- 编辑历史条目管理

### 6. QQ 通道

- 将现有会话延伸到 QQ
- QQ 消息可以进入当前会话
- 助手回复会回到 QQ
- 后续确认和跟进交互也可以继续在 QQ 上完成

### 7. 多语言支持

- 内置 i18n 系统
- 支持语言：EN、JA、de、ru、zh-CN

### 8. 主题系统

- 主题选择器
- 自定义主题 token
- 明暗主题支持

---

## 技术架构

### 核心模块布局

```
reasonix/
├── pom.xml                          # 父 POM
├── reasonix-core/                   # 核心业务逻辑（可独立服务）
│   ├── pom.xml
│   └── src/main/java/com/reasonix/core/
│       ├── loop/                    # 核心循环
│       │   ├── CacheFirstLoop.java  # 主循环入口
│       │   ├── Dispatch.java        # 工具并行调度
│       │   ├── Healing.java         # 消息修复
│       │   ├── Shrink.java          # Token 截断
│       │   └── Streaming.java       # 流式响应处理
│       ├── memory/                  # 三区内存
│       │   ├── ImmutablePrefix.java # 系统前缀（缓存键）
│       │   ├── AppendOnlyLog.java   # 追加日志
│       │   ├── VolatileScratch.java # 易失性暂存
│       │   └── SessionStore.java    # 会话持久化
│       ├── context/                 # 上下文管理
│       │   └── ContextManager.java  # 折叠决策
│       ├── tools/                   # 工具系统
│       │   ├── ToolRegistry.java    # 工具注册表
│       │   ├── ToolDefinition.java  # 工具定义
│       │   ├── ToolContext.java     # 工具上下文
│       │   └── shell/
│       │       └── ShellTools.java  # Shell 命令执行
│       ├── hooks/                   # 钩子系统
│       │   └── HookManager.java     # 钩子管理
│       ├── workspaces/              # 工作区管理
│       │   └── WorkspaceManager.java
│       ├── client/                 # API 客户端
│       │   ├── DeepSeekClient.java  # DeepSeek API
│       │   └── Usage.java          # Token 统计
│       ├── ports/                  # 端口接口（依赖倒置）
│       │   ├── ModelClient.java     # 模型客户端接口
│       │   ├── ToolHost.java        # 工具主机接口
│       │   ├── EventSink.java       # 事件持久化接口
│       │   └── MemoryStore.java     # 内存存储接口
│       ├── adapters/               # 适配器实现
│       │   ├── LangChain4jModelClient.java
│       │   ├── LangChain4jMcpClient.java
│       │   └── jsonl/
│       │       └── JsonlEventSink.java
│       ├── transcript/             # 转录日志
│       │   ├── TranscriptLog.java
│       │   └── TranscriptDiff.java
│       ├── telemetry/              # 遥测
│       │   └── Pricing.java        # 定价计算
│       ├── acp/                    # ACP 协议
│       │   ├── Protocol.java
│       │   └── Dispatch.java
│       ├── skills/                 # 技能系统
│       │   ├── SkillStore.java     # 技能存储
│       │   └── SkillRunner.java    # 技能执行
│       └── core/                   # 核心工具
│           ├── Events.java
│           ├── Reducers.java
│           └── PauseGate.java
│
├── reasonix-api/                   # REST/gRPC API 定义
│   ├── pom.xml
│   └── src/main/java/com/reasonix/api/
│       ├── rest/                   # REST API
│       │   ├── ReasonixController.java
│       │   ├── ChatRequest.java
│       │   ├── ChatResponse.java
│       │   ├── ToolRequest.java
│       │   └── SessionRequest.java
│       ├── grpc/                   # gRPC 定义（可选）
│       │   └── ReasonixGrpcService.java
│       ├── dto/                    # 数据传输对象
│       │   ├── MessageDto.java
│       │   ├── ToolCallDto.java
│       │   └── UsageDto.java
│       └── websocket/              # WebSocket 流式传输
│           └── StreamHandler.java
│
├── reasonix-server/                # 独立服务入口
│   ├── pom.xml
│   └── src/main/java/com/reasonix/server/
│       ├── ReasonixServerApplication.java  # Spring Boot 入口
│       ├── config/
│       │   ├── SecurityConfig.java
│       │   ├── WebSocketConfig.java
│       │   └── CorsConfig.java
│       └── Application.java        # 服务启动
│
├── reasonix-tui/                   # Tamboui TUI 前端
│   ├── pom.xml
│   └── src/main/java/com/reasonix/tui/
│       ├── ReasonixTuiApp.java    # TUI 入口
│       ├── view/
│       │   ├── AppView.java        # 主视图
│       │   ├── ChatView.java       # 聊天界面
│       │   ├── ToolCard.java       # 工具调用卡片
│       │   ├── DiffCard.java       # Diff 显示
│       │   ├── PlanCard.java       # 计划显示
│       │   ├── UsageCard.java      # Token/USD 统计
│       │   ├── Composer.java       # 输入框
│       │   └── WorkspaceView.java  # 工作区选择
│       ├── component/
│       │   ├── StatusBar.java      # 状态栏
│       │   ├── MessageList.java    # 消息列表
│       │   └── ToolPanel.java      # 工具面板
│       ├── client/                 # 后端通信
│       │   ├── DirectClient.java   # 直接调用 Core
│       │   └── RestClient.java    # REST API 客户端
│       └── style/
│           └── Theme.java          # CSS 主题
│
├── reasonix-javafx/                # JavaFX GUI 前端（未来扩展）
│   ├── pom.xml
│   └── src/main/java/com/reasonix/javafx/
│       ├── ReasonixJavaFxApp.java  # JavaFX 入口
│       ├── view/
│       │   ├── MainView.java       # 主窗口
│       │   ├── ChatPane.java       # 聊天面板
│       │   ├── ToolPane.java       # 工具面板
│       │   ├── SettingsPane.java   # 设置面板
│       │   └── WorkspacePane.java  # 工作区管理
│       ├── component/
│       │   ├── MessageCell.java   # 消息单元格
│       │   ├── ToolCallCard.java   # 工具调用卡片
│       │   └── UsageIndicator.java # 使用统计指示器
│       ├── client/
│       │   └── RestClient.java     # REST API 客户端
│       └── controller/
│           └── MainController.java # 主控制器
│
├── reasonix-mcp/                   # MCP 协议模块
│   ├── pom.xml
│   └── src/main/java/com/reasonix/mcp/
│       ├── McpClient.java          # MCP 客户端
│       ├── McpServer.java          # MCP 服务端
│       ├── protocol/
│       │   ├── InitializeRequest.java
│       │   ├── ToolListRequest.java
│       │   └── ToolCallRequest.java
│       └── transport/
│           ├── StdioTransport.java
│           └── WebSocketTransport.java
│
├── reasonix-index/                 # 语义搜索模块
│   ├── pom.xml
│   └── src/main/java/com/reasonix/index/
│       ├── semantic/
│       │   ├── SemanticStore.java   # 向量存储
│       │   ├── EmbeddingClient.java # Embedding 客户端
│       │   └── Chunker.java        # 代码分块
│       └── search/
│           └── CodeSearcher.java   # 代码搜索
│
├── reasonix-code/                  # 代码分析模块
│   ├── pom.xml
│   └── src/main/java/com/reasonix/code/
│       ├── java/
│       │   ├── ClassSourceFinder.java
│       │   └── ZipReader.java
│       ├── query/
│       │   ├── GrammarMap.java     # 语法映射
│       │   ├── SymbolExtractor.java # 符号提取
│       │   └── AntlrParser.java    # ANTLR 解析器
│       ├── edit/
│       │   ├── EditBlockParser.java
│       │   ├── AtomicFileWriter.java
│       │   └── DiffPreview.java
│       ├── lifecycle/
│       │   ├── EngineeringLifecycleRuntime.java
│       │   └── PlanStore.java
│       └── checkpoint/
│           └── CheckpointManager.java
│
├── reasonix-app/                  # 一体化打包入口
│   ├── pom.xml
│   └── src/main/java/com/reasonix/app/
│       ├── ReasonixApp.java        # 统一入口
│       └── Launcher.java           # Native Image 启动器
│
└── reasonix-common/                # 公共工具
    ├── pom.xml
    └── src/main/java/com/reasonix/common/
        ├── config/
        │   └── Config.java         # 配置管理
        ├── util/
        │   ├── PathUtils.java
        │   └── JsonUtils.java
        └── exception/
            └── ReasonixException.java
```

### 关键技术栈

- **JDK**：GraalVM 25+（Java 21+）
- **TUI 框架**：tamboui（jline3-backend + toolkit + markdown + css）
- **AI 集成**：langchain4j（DeepSeek MCP 支持）
- **构建工具**：Maven
- **AI 后端**：DeepSeek API（v4-flash、v4-pro）
- **协议**：MCP（Model Context Protocol）
- **语法分析**：Tree-sitter
- **配置**：JSON（全局 `~/.reasonix/config.json` + 项目级 `<project>/.reasonix/`）

---

## 配置系统

### 全局配置

- 位置：`~/.reasonix/config.json`
- 项目级覆盖：`<project>/.reasonix/`

### 主要配置主题

| 主题 | 说明 |
|---|---|
| MCP 服务器 | stdio · SSE · Streamable HTTP |
| Skills | 模型可以调用的 markdown 剧本 |
| Memory | 用户私有的知识，钉进前缀 |
| Hooks | 生命周期事件触发的 shell 命令 |
| 权限 | 按工作区的 shell 白名单 |
| Web 搜索 | 默认 Mojeek；可切到 SearXNG 或 Metaso |
| 语义索引 | 本地 Ollama，或任何 OpenAI 兼容的 embedding 接口 |

---

## 斜杠命令（Slash Commands）

### 基础命令
- `/help`：帮助
- `/clear`：清屏
- `/model`：切换模型（flash/pro）
- `/effort`：设置推理努力程度
- `/quit`：退出

### 内存命令
- `/remember`：记住信息
- `/forget`：忘记记忆
- `/list-memories`：列出记忆

### 技能命令
- `/skill new`：创建新技能
- `/skill list`：列出技能
- `/skill edit`：编辑技能

### MCP 命令
- `/mcp add`：添加 MCP 服务器
- `/mcp list`：列出 MCP 服务器
- `/mcp remove`：移除 MCP 服务器
- `/mcp browse`：浏览 MCP 工具

### 编辑命令
- `/apply`：应用编辑
- `/undo`：撤销
- `/history`：查看历史
- `/show`：显示状态

### 计划命令
- `/todo`：管理待办
- `/plan`：计划模式

### 会话命令
- `/sessions`：会话管理
- `/replay`：重放会话
- `/checkpoint`：检查点管理

### 观测命令
- `/stats`：统计信息
- `/events`：事件日志
- `/usage`：用量统计

### 管理命令
- `/update`：更新
- `/doctor`：体检
- `/search-engine`：切换搜索引擎
- `/theme`：切换主题

---

## 能力一览总结

- ✅ **Cell-diff 渲染器**：美观的 diff 展示
- ✅ **MCP 集成**：完整的 MCP 客户端和桥接
- ✅ **计划模式**：可审查的计划执行
- ✅ **权限系统**：细粒度的权限控制
- ✅ **内嵌仪表盘**：TUI 内置的观测面板
- ✅ **持久化工作区会话**：会话保存和重放
- ✅ **Hooks/Skills/Memory**：完整的扩展系统
- ✅ **语义检索**：代码语义搜索
- ✅ **自动 checkpoint**：自动保存检查点
- ✅ `/effort` 旋钮**：灵活的推理努力调整
- ✅ **Transcript 重放**：完整的会话重放
- ✅ **事件日志**：详细的事件记录

---

## 与其他工具对比

| | Reasonix | Claude Code | Cursor | Aider |
|---|---|---|---|---|
| 后端 | DeepSeek | Anthropic | OpenAI / Anthropic | 任意（OpenRouter）|
| 协议 | **MIT** | 闭源 | 闭源 | Apache 2 |
| 单任务成本 | **低** | 高 | 订阅 + 用量 | 不一 |
| DeepSeek 前缀缓存 | **专门工程化** | 不适用 | 不适用 | 偶发命中 |
| 内嵌 web 仪表盘 | 支持 | — | 不适用 (IDE) | — |
| 持久化的工作区会话 | 支持 | 部分 | 不适用 | — |
| 计划模式 · MCP · Hooks | 支持 | 支持 | 支持 | 部分 |
| 开放社区共建 | 支持 | — | — | 支持 |

---

## 项目不做的事

- ❌ **多供应商灵活性**：故意只做 DeepSeek
- ❌ **IDE 集成**：终端优先
- ❌ **追最难的 reasoning 榜单**：Claude Opus 在某些榜单还是赢家
- ❌ **完全离线 / 永远免费**：需要付费的 DeepSeek API Key

---

## 社区与支持



---

## 许可证

MIT 许可证，详见 [LICENSE](./LICENSE)
