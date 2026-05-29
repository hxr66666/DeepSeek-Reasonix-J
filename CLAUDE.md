# DeepSeek-Reasonix Java 实现指南

本指南用于将 DeepSeek-Reasonix 从 TypeScript 移植到 Java。

## 项目概述

DeepSeek-Reasonix 是一个基于 DeepSeek API 的 AI 编程助手，核心特性：

- **前缀缓存优化** - 三区域内存划分（ImmutablePrefix/AppendOnlyLog/VolatileScratch）
- **工具调用修复** - JSON 截断修复、Schema 扁平化、重复调用防护
- **上下文折叠** - 基于 promptTokens/ctxMax 比率的自动折叠
- **并行工具调度** - 按并行安全属性分组执行
- **全功能 TUI** - Tamboui Toolkit 声明式 UI

## 技术栈

| 类别 | 技术 | 说明 |
|------|------|------|
| JDK | GraalVM 25 | 支持 Native Image 高性能启动 |
| 构建 | Maven | 标准构建工具 |
| HTTP | LangChain4j | 内置 DeepSeek 支持 + 流式响应 |
| JSON | LangChain4j Jackson | JSON 序列化/反序列化 |
| TUI | Tamboui Toolkit | 声明式 UI + JLine3 后端 + Markdown + CSS |
| MCP | LangChain4j MCP | MCP 工具集成 |
| 测试 | JUnit 5 + Mockito | 标准测试框架 |
| 异步 | Java 21 Virtual Threads | 虚拟线程 I/O 密集型 |

## Tamboui 依赖

```xml
<dependency>
    <groupId>dev.tamboui</groupId>
    <artifactId>tamboui-toolkit</artifactId>
</dependency>
<dependency>
    <groupId>dev.tamboui</groupId>
    <artifactId>tamboui-jline3-backend</artifactId>
</dependency>
<dependency>
    <groupId>dev.tamboui</groupId>
    <artifactId>tamboui-toolkit-markdown</artifactId>
</dependency>
<dependency>
    <groupId>dev.tamboui</groupId>
    <artifactId>tamboui-css</artifactId>
</dependency>
```

## 模块化架构

采用 **多模块 Maven 项目**，支持三种部署模式：

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| **一体化打包** | TUI + Core 合并为单可执行文件 | 本地 CLI 使用 |
| **分离服务** | Core 作为独立服务，TUI/JavaFX 作为客户端 | 多客户端支持 |
| **嵌入式** | Core 作为库被其他应用集成 | IDE 插件、自动化脚本 |

### 模块依赖关系

```
┌─────────────────────────────────────────────────────────────┐
│                    reasonix-app (一体化入口)                  │
│         ┌─────────────────┬─────────────────┐               │
│         │   reasonix-tui  │  reasonix-javafx │               │
│         │   (Tamboui)     │   (JavaFX GUI)   │               │
│         └─────────────────┴─────────────────┘               │
│                      ↓           ↓                           │
│                 reasonix-api (接口定义)                       │
│                      ↓                                       │
│                 reasonix-core (业务逻辑)                      │
│                      ↓                                       │
│    ┌─────────────────────────────────────────────┐          │
│    │ reasonix-mcp │ reasonix-index │ reasonix-code │        │
│    │ (MCP 协议)   │ (语义搜索)     │ (代码分析)    │        │
│    └─────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

## 项目结构

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
│       │   ├── DuckDBMemoryStore.java  # DuckDB 记忆存储
│       │   └── DuckDBSessionStore.java # DuckDB 会话存储
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
│       ├── client/                  # API 客户端
│       │   ├── DeepSeekClient.java  # DeepSeek API
│       │   └── Usage.java           # Token 统计
│       ├── ports/                   # 端口接口（依赖倒置）
│       │   ├── ModelClient.java     # 模型客户端接口
│       │   ├── ToolHost.java        # 工具主机接口
│       │   ├── EventSink.java       # 事件持久化接口
│       │   ├── MemoryStore.java     # 记忆存储接口
│       │   ├── SessionStore.java    # 会话存储接口
│       │   └── ReasonixService.java # 核心服务接口
│       ├── adapters/                # 适配器实现
│       │   ├── LangChain4jModelClient.java
│       │   ├── LangChain4jMcpClient.java
│       │   ├── DuckDBMemoryStore.java
│       │   ├── DuckDBSessionStore.java
│       │   └── jsonl/
│       │       └── JsonlEventSink.java
│       ├── transcript/              # 转录日志
│       │   ├── TranscriptLog.java
│       │   └── TranscriptDiff.java
│       ├── telemetry/               # 遥测
│       │   └── Pricing.java         # 定价计算
│       ├── acp/                     # ACP 协议
│       │   ├── Protocol.java
│       │   └── Dispatch.java
│       ├── skills/                  # 技能系统
│       │   ├── SkillLoader.java     # 技能加载（扫描 .md 文件）
│       │   └── SkillRunner.java     # 技能执行
│       └── core/                    # 核心工具
│           ├── Events.java
│           ├── Reducers.java
│           └── PauseGate.java
│
├── reasonix-api/                    # REST/gRPC API 定义
│   ├── pom.xml
│   └── src/main/java/com/reasonix/api/
│       ├── rest/                    # REST API
│       │   ├── ReasonixController.java
│       │   ├── ChatRequest.java
│       │   ├── ChatResponse.java
│       │   ├── ToolRequest.java
│       │   └── SessionRequest.java
│       ├── grpc/                    # gRPC 定义（可选）
│       │   └── ReasonixGrpcService.java
│       ├── dto/                     # 数据传输对象
│       │   ├── MessageDto.java
│       │   ├── ToolCallDto.java
│       │   └── UsageDto.java
│       └── websocket/               # WebSocket 流式传输
│           └── StreamHandler.java
│
├── reasonix-server/                 # 独立服务入口
│   ├── pom.xml
│   └── src/main/java/com/reasonix/server/
│       ├── ReasonixServerApplication.java  # Spring Boot 入口
│       ├── config/
│       │   ├── SecurityConfig.java
│       │   ├── WebSocketConfig.java
│       │   └── CorsConfig.java
│       └── Application.java         # 服务启动
│
├── reasonix-tui/                    # Tamboui TUI 前端
│   ├── pom.xml
│   └── src/main/java/com/reasonix/tui/
│       ├── ReasonixTuiApp.java      # TUI 入口
│       ├── view/
│       │   ├── AppView.java         # 主视图
│       │   ├── ChatView.java        # 聊天界面
│       │   ├── ToolCard.java        # 工具调用卡片
│       │   ├── DiffCard.java        # Diff 显示
│       │   ├── PlanCard.java        # 计划显示
│       │   ├── UsageCard.java       # Token/USD 统计
│       │   ├── Composer.java        # 输入框
│       │   └── WorkspaceView.java   # 工作区选择
│       ├── component/
│       │   ├── StatusBar.java       # 状态栏
│       │   ├── MessageList.java     # 消息列表
│       │   └── ToolPanel.java       # 工具面板
│       ├── client/                  # 后端通信
│       │   ├── DirectClient.java    # 直接调用 Core
│       │   └── RestClient.java      # REST API 客户端
│       └── style/
│           └── Theme.java           # CSS 主题
│
├── reasonix-javafx/                 # JavaFX GUI 前端（未来扩展）
│   ├── pom.xml
│   └── src/main/java/com/reasonix/javafx/
│       ├── ReasonixJavaFxApp.java   # JavaFX 入口
│       ├── view/
│       │   ├── MainView.java        # 主窗口
│       │   ├── ChatPane.java        # 聊天面板
│       │   ├── ToolPane.java        # 工具面板
│       │   ├── SettingsPane.java    # 设置面板
│       │   └── WorkspacePane.java   # 工作区管理
│       ├── component/
│       │   ├── MessageCell.java     # 消息单元格
│       │   ├── ToolCallCard.java    # 工具调用卡片
│       │   └── UsageIndicator.java  # 使用统计指示器
│       ├── client/
│       │   └ RestClient.java      # REST API 客户端
│       └── controller/
│           └── MainController.java  # 主控制器
│
├── reasonix-mcp/                    # MCP 协议模块
│   ├── pom.xml
│   └── src/main/java/com/reasonix/mcp/
│       ├── McpClient.java           # MCP 客户端
│       ├── McpServer.java           # MCP 服务端
│       ├── protocol/
│       │   ├── InitializeRequest.java
│       │   ├── ToolListRequest.java
│       │   └── ToolCallRequest.java
│       └── transport/
│           ├── StdioTransport.java
│           └── WebSocketTransport.java
│
├── reasonix-index/                  # 语义搜索模块
│   ├── pom.xml
│   └── src/main/java/com/reasonix/index/
│       ├── semantic/
│       │   ├── SemanticStore.java   # 向量存储
│       │   ├── EmbeddingClient.java # Embedding 客户端
│       │   └── Chunker.java         # 代码分块
│       └── search/
│           └── CodeSearcher.java    # 代码搜索
│
├── reasonix-code/                   # 代码分析模块
│   ├── pom.xml
│   └── src/main/java/com/reasonix/code/
│       ├── java/
│       │   ├── ClassSourceFinder.java
│       │   └── ZipReader.java
│       ├── query/
│       │   ├── GrammarMap.java      # 语法映射
│       │   ├── SymbolExtractor.java # 符号提取
│       │   └── AntlrParser.java     # ANTLR 解析器
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
├── reasonix-app/                    # 一体化打包入口
│   ├── pom.xml
│   └── src/main/java/com/reasonix/app/
│       ├── ReasonixApp.java         # 统一入口
│       └── Launcher.java            # Native Image 启动器
│
└── reasonix-common/                 # 公共工具
    ├── pom.xml
    └── src/main/java/com/reasonix/common/
        ├── config/
        │   ├── Config.java          # 配置管理（YAML + DuckDB）
        │   └── ConfigManager.java   # 分层配置加载器
        ├── util/
        │   ├── PathUtils.java
        │   └── JsonUtils.java
        └── exception/
            └── ReasonixException.java
```

## 核心模块实现

### 1. 三区内存 (memory/)

三区域划分实现 DeepSeek 前缀缓存优化：

```java
public class ImmutablePrefix {
    private String system;
    private List<ToolSpec> toolSpecs;
    private List<ChatMessage> fewShots;
    private String fingerprintCache;

    public String getFingerprint() {
        if (fingerprintCache != null) return fingerprintCache;
        String blob = system + JSON.serialize(toolSpecs) + JSON.serialize(fewShots);
        fingerprintCache = DigestUtils.sha256Hex(blob).substring(0, 16);
        return fingerprintCache;
    }

    public void replaceSystem(String s) {
        if (this.system.equals(s)) return;
        this.system = s;
        this.fingerprintCache = null;
    }

    public boolean addTool(ToolSpec spec) {
        if (toolSpecs.stream().anyMatch(t -> t.name().equals(spec.name()))) {
            return false;
        }
        toolSpecs.add(spec);
        this.fingerprintCache = null;
        return true;
    }
}

public class AppendOnlyLog {
    private List<ChatMessage> entries = new ArrayList<>();

    public void append(ChatMessage message) {
        entries.add(message);
    }

    public void extend(List<ChatMessage> messages) {
        messages.forEach(this::append);
    }

    public void compactInPlace(List<ChatMessage> replacement) {
        this.entries = new ArrayList<>(replacement);
    }

    public List<ChatMessage> toMessages() {
        return new ArrayList<>(entries);
    }
}

public class VolatileScratch {
    public String reasoning;
    public Map<String, Object> planState;
    public List<String> notes;

    public void reset() {
        reasoning = null;
        planState = null;
        notes.clear();
    }
}
```

### 2. 主循环 (loop/)

```java
public class CacheFirstLoop {
    private final ImmutablePrefix prefix;
    private final AppendOnlyLog log;
    private final VolatileScratch scratch;
    private final ToolRegistry tools;
    private final ModelClient modelClient;
    private final ContextManager contextManager;

    public Flux<LoopEvent> run(UserMessage message) {
        log.append(new UserMessage(message.content()));

        ChatRequest request = ChatRequest.builder()
            .model(model)
            .prefix(buildPrefix())
            .messages(log.toMessages())
            .build();

        return modelClient.chatStream(request)
            .concatMap(this::processChunk)
            .takeUntil(this::isTerminalEvent);
    }

    private String buildPrefix() {
        return prefix.getSystem() + "\n\n" +
               formatToolSpecs(prefix.getToolSpecs());
    }
}
```

### 3. 工具并行调度 (loop/Dispatch.java)

```java
public Flux<LoopEvent> dispatchToolCallsChunked(
        List<ToolCall> calls,
        DispatchContext ctx) {

    return Flux.fromIterable(calls)
        .bufferUntil(call -> !ctx.isParallelSafe(call.name()))
        .flatMap(chunk -> executeChunk(chunk, ctx));
}

private Mono<ToolResult> dispatchTool(ToolCall call, DispatchContext ctx) {
    return Mono.fromCallable(() -> {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ToolResult> future = executor.submit(() ->
                tools.dispatch(call.name(), call.arguments())
            );
            return future.get();
        }
    }).onErrorResume(e -> Mono.just(ToolResult.failure(e.getMessage())));
}
```

### 4. 上下文折叠 (context/ContextManager.java)

```java
public class ContextManager {
    public static final double HISTORY_FOLD_THRESHOLD = 0.75;
    public static final double HISTORY_FOLD_TAIL_FRACTION = 0.2;
    public static final double AGGRESSIVE_THRESHOLD = 0.78;
    public static final double AGGRESSIVE_TAIL_FRACTION = 0.1;
    public static final double FORCE_SUMMARY_THRESHOLD = 0.8;

    public PostUsageDecision decideAfterUsage(Usage usage, String model) {
        long ctxMax = CONTEXT_TOKENS.getOrDefault(model, 131072L);
        double ratio = (double) usage.promptTokens() / ctxMax;

        if (ratio > FORCE_SUMMARY_THRESHOLD) {
            return new ExitWithSummary(ratio);
        }
        if (alreadyFoldedThisTurn) {
            return new NoOp();
        }
        if (ratio > AGGRESSIVE_THRESHOLD) {
            return new Fold(ratio, AGGRESSIVE_TAIL_FRACTION, true);
        }
        if (ratio > HISTORY_FOLD_THRESHOLD) {
            return new Fold(ratio, HISTORY_FOLD_TAIL_FRACTION, false);
        }
        return new NoOp();
    }
}

public sealed interface PostUsageDecision {
    record NoOp() implements PostUsageDecision {}
    record Fold(double ratio, double tailFraction, boolean aggressive) implements PostUsageDecision {}
    record ExitWithSummary(double ratio) implements PostUsageDecision {}
}
```

### 5. LangChain4j 适配器 (adapters/)

```java
public class LangChain4jModelClient implements ModelClient {
    private final ChatLanguageModel model;
    private final StreamingChatLanguageModel streamingModel;

    public LangChain4jModelClient(String apiKey, String modelName) {
        this.model = DeepSeekChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .build();

        this.streamingModel = DeepSeekStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .build();
    }

    @Override
    public Flux<ModelStreamChunk> chatStream(ChatRequest request) {
        return Flux.create(emitter -> {
            streamingModel.generate(request.messages(), new StreamingResponseHandler<>() {
                @Override
                public void onNext(String token) {
                    emitter.next(new ModelStreamChunk(token, null, null, null));
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    emitter.complete();
                }

                @Override
                public void onError(Throwable error) {
                    emitter.error(error);
                }
            });
        });
    }
}

public class LangChain4jMcpClient {
    private final McpClient mcpClient;

    public LangChain4jMcpClient(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    public List<ToolSpecification> listTools() {
        return mcpClient.listTools();
    }

    public String executeTool(String name, Map<String, Object> args) {
        return mcpClient.executeTool(name, args);
    }
}
```

### 6. Tamboui TUI (tui/)

```java
public class AppView extends TambouiView {
    private final ChatView chatView;
    private final Composer composer;
    private final UsageCard usageCard;

    @Override
    public View build() {
        return VStack(
            Header("Reasonix - DeepSeek Coding Assistant"),
            ScrollView(chatView),
            HStack(
                usageCard,
                Spacer(),
                StatusBar()
            ),
            composer
        ).withCss("app-container");
    }
}

public class ChatView extends TambouiView {
    private final List<MessageCard> messages = new ArrayList<>();

    @Override
    public View build() {
        return VStack(
            messages.stream()
                .map(MessageCard::build)
                .toList()
        ).withCss("chat-container");
    }

    public void appendMessage(ChatMessage msg) {
        messages.add(new MessageCard(msg));
        requestRebuild();
    }
}

public class ToolCard extends TambouiView {
    private final ToolCall call;
    private final ToolResult result;

    @Override
    public View build() {
        return VStack(
            HStack(
                Icon("🔧"),
                Text(call.name()).withCss("tool-name"),
                Badge(result.ok() ? "✓" : "✗")
                    .withCss(result.ok() ? "success" : "error")
            ),
            Markdown(result.output()).withCss("tool-output")
        ).withCss("tool-card");
    }
}

public class Composer extends TambouiView {
    private String input = "";

    @Override
    public View build() {
        return HStack(
            TextField(input)
                .onChange(s -> input = s)
                .placeholder("Type your message...")
                .withCss("input-field"),
            Button("Send")
                .onClick(() -> handleSubmit())
                .withCss("send-button")
        ).withCss("composer");
    }

    private void handleSubmit() {
        if (!input.isEmpty()) {
            onSend.accept(input);
            input = "";
            requestRebuild();
        }
    }
}

public class UsageCard extends TambouiView {
    private long promptTokens = 0;
    private long completionTokens = 0;
    private double costUsd = 0;

    @Override
    public View build() {
        return HStack(
            Text("Tokens: " + promptTokens + "/" + completionTokens),
            Spacer(),
            Text("$" + String.format("%.4f", costUsd))
        ).withCss("usage-bar");
    }

    public void update(Usage usage, double cost) {
        this.promptTokens = usage.promptTokens();
        this.completionTokens = usage.completionTokens();
        this.costUsd = cost;
        requestRebuild();
    }
}
```

### 7. 工具注册表 (tools/ToolRegistry.java)

```java
public class ToolRegistry {
    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final LangChain4jMcpClient mcpClient;

    public ToolRegistry register(ToolDefinition def) {
        if (def.name() == null) {
            throw new IllegalArgumentException("tool requires a name");
        }
        tools.put(def.name(), def);
        return this;
    }

    public ToolRegistry registerMcpTools() {
        for (ToolSpecification spec : mcpClient.listTools()) {
            register(new ToolDefinition(
                spec.name(),
                spec.description(),
                args -> mcpClient.executeTool(spec.name(), args)
            ));
        }
        return this;
    }

    public String dispatch(String name, String argumentsRaw) {
        ToolDefinition def = tools.get(name);
        if (def == null) {
            throw new ToolNotFoundException(name);
        }
        return def.fn().apply(parseArgs(argumentsRaw));
    }
}
```

## 工具调用流程

```
用户输入 (Tamboui Composer)
    ↓
CacheFirstLoop.run()
    ↓
Healing.healLoadedMessages()
    ↓
LangChain4jModelClient.chatStream()
    ↓
Streaming.processChunk()
    ↓
Shrink.shrinkOversizedToolResults()
    ↓
Dispatch.dispatchToolCallsChunked()
    ↓
ToolRegistry.dispatch() → LangChain4jMcpClient
    ↓
Tamboui ToolCard 更新
```

## 折叠决策流程

```
promptTokens / ctxMax
    │
    ├─ > 0.80 ──────────────────→ ExitWithSummary
    │
    ├─ > 0.78 ──────────────────→ Fold(aggressive, tail=10%)
    │
    ├─ > 0.75 ──────────────────→ Fold(normal, tail=20%)
    │
    └─ <= 0.75 ─────────────────→ NoOp
```

## Maven 多模块配置

### 父 POM (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.reasonix</groupId>
    <artifactId>reasonix</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>reasonix-common</module>
        <module>reasonix-core</module>
        <module>reasonix-api</module>
        <module>reasonix-mcp</module>
        <module>reasonix-index</module>
        <module>reasonix-code</module>
        <module>reasonix-server</module>
        <module>reasonix-tui</module>
        <module>reasonix-javafx</module>
        <module>reasonix-app</module>
    </modules>

    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <langchain4j.version>0.36.0</langchain4j.version>
        <tamboui.version>1.0.0</tamboui.version>
        <spring.boot.version>3.4.0</spring.boot.version>
        <javafx.version>24</javafx.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- 内部模块 -->
            <dependency>
                <groupId>com.reasonix</groupId>
                <artifactId>reasonix-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reasonix</groupId>
                <artifactId>reasonix-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reasonix</groupId>
                <artifactId>reasonix-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reasonix</groupId>
                <artifactId>reasonix-mcp</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reasonix</groupId>
                <artifactId>reasonix-index</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reasonix</groupId>
                <artifactId>reasonix-code</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- LangChain4j -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j</artifactId>
                <version>${langchain4j.version}</version>
            </dependency>
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-deepseek</artifactId>
                <version>${langchain4j.version}</version>
            </dependency>
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-mcp</artifactId>
                <version>${langchain4j.version}</version>
            </dependency>

            <!-- Tamboui -->
            <dependency>
                <groupId>dev.tamboui</groupId>
                <artifactId>tamboui-toolkit</artifactId>
                <version>${tamboui.version}</version>
            </dependency>
            <dependency>
                <groupId>dev.tamboui</groupId>
                <artifactId>tamboui-jline3-backend</artifactId>
                <version>${tamboui.version}</version>
            </dependency>
            <dependency>
                <groupId>dev.tamboui</groupId>
                <artifactId>tamboui-toolkit-markdown</artifactId>
                <version>${tamboui.version}</version>
            </dependency>
            <dependency>
                <groupId>dev.tamboui</groupId>
                <artifactId>tamboui-css</artifactId>
                <version>${tamboui.version}</version>
            </dependency>

            <!-- Spring Boot -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-web</artifactId>
                <version>${spring.boot.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-websocket</artifactId>
                <version>${spring.boot.version}</version>
            </dependency>

            <!-- JavaFX -->
            <dependency>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-controls</artifactId>
                <version>${javafx.version}</version>
            </dependency>
            <dependency>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-fxml</artifactId>
                <version>${javafx.version}</version>
            </dependency>

            <!-- Testing -->
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>5.10.0</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <source>25</source>
                        <target>25</target>
                        <compilerArgs>
                            <arg>--enable-preview</arg>
                        </compilerArgs>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <version>0.10.4</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

### reasonix-core/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>com.reasonix</groupId>
        <artifactId>reasonix</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>reasonix-core</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-mcp</artifactId>
        </dependency>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-index</artifactId>
        </dependency>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-code</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-deepseek</artifactId>
        </dependency>
    </dependencies>
</project>
```

### reasonix-tui/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>com.reasonix</groupId>
        <artifactId>reasonix</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>reasonix-tui</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-api</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.tamboui</groupId>
            <artifactId>tamboui-toolkit</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.tamboui</groupId>
            <artifactId>tamboui-jline3-backend</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.tamboui</groupId>
            <artifactId>tamboui-toolkit-markdown</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.tamboui</groupId>
            <artifactId>tamboui-css</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.reasonix.tui.ReasonixTuiApp</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### reasonix-server/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>com.reasonix</groupId>
        <artifactId>reasonix</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>reasonix-server</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring.boot.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### reasonix-javafx/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>com.reasonix</groupId>
        <artifactId>reasonix</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>reasonix-javafx</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-fxml</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>com.reasonix.javafx.ReasonixJavaFxApp</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### reasonix-app/pom.xml（一体化打包）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>com.reasonix</groupId>
        <artifactId>reasonix</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>reasonix-app</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.reasonix</groupId>
            <artifactId>reasonix-tui</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Shade Plugin - 合并所有依赖 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.reasonix.app.ReasonixApp</mainClass>
                                </transformer>
                            </transformers>
                            <finalName>reasonix</finalName>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <!-- Native Image -->
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>build-native</id>
                        <goals>
                            <goal>compile-no-fork</goal>
                        </goals>
                        <phase>package</phase>
                    </execution>
                </executions>
                <configuration>
                    <mainClass>com.reasonix.app.ReasonixApp</mainClass>
                    <buildArgs>
                        <buildArg>--no-fallback</buildArg>
                        <buildArg>--enable-preview</buildArg>
                        <buildArg>-H:+ReportExceptionStackTraces</buildArg>
                    </buildArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## 部署模式

### 1. 一体化打包（本地 CLI）

```bash
# 构建 Native Image
mvn -pl reasonix-app clean package native:compile-no-fork

# 输出: reasonix-app/target/reasonix (可执行文件)
./reasonix --workspace /path/to/project
```

### 2. 分离服务（多客户端）

```bash
# 启动后端服务
mvn -pl reasonix-server spring-boot:run
# 服务监听 http://localhost:8080

# 启动 TUI 客户端（连接服务）
mvn -pl reasonix-tui exec:java -Dexec.args="--server http://localhost:8080"

# 启动 JavaFX 客户端（连接服务）
mvn -pl reasonix-javafx exec:java
```

### 3. 嵵入式（作为库）

```xml
<!-- 其他项目的 pom.xml -->
<dependency>
    <groupId>com.reasonix</groupId>
    <artifactId>reasonix-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
// 在其他应用中使用
ReasonixService service = new ReasonixServiceImpl(config);
String response = service.chat("帮我分析这个代码", workspace);
```

## REST API 设计

### 核心接口

```java
@RestController
@RequestMapping("/api/v1")
public class ReasonixController {

    @PostMapping("/chat")
    public Mono<ChatResponseDto> chat(@RequestBody ChatRequestDto request) {
        return service.chat(request);
    }

    @PostMapping("/chat/stream")
    public Flux<StreamChunkDto> chatStream(@RequestBody ChatRequestDto request) {
        return service.chatStream(request);
    }

    @GetMapping("/tools")
    public List<ToolDefinitionDto> listTools() {
        return service.listTools();
    }

    @PostMapping("/tools/{name}/execute")
    public Mono<ToolResultDto> executeTool(
        @PathVariable String name,
        @RequestBody ToolRequestDto request) {
        return service.executeTool(name, request);
    }

    @GetMapping("/sessions")
    public List<SessionDto> listSessions() {
        return service.listSessions();
    }

    @PostMapping("/sessions/{id}/restore")
    public Mono<SessionDto> restoreSession(@PathVariable String id) {
        return service.restoreSession(id);
    }

    @GetMapping("/workspaces")
    public List<WorkspaceDto> listWorkspaces() {
        return service.listWorkspaces();
    }
}
```

### WebSocket 流式传输

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(streamHandler(), "/ws/stream")
            .setAllowedOrigins("*");
    }

    @Bean
    public WebSocketHandler streamHandler() {
        return new StreamHandler();
    }
}

public class StreamHandler extends TextWebSocketHandler {

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ChatRequestDto request = JsonUtils.parse(message.getPayload(), ChatRequestDto.class);
        
        service.chatStream(request)
            .map(chunk -> JsonUtils.toJson(chunk))
            .map(TextMessage::new)
            .subscribe(
                msg -> session.sendMessage(msg),
                err -> session.close(),
                () -> session.close()
            );
    }
}
```

## 前端通信层

### TUI DirectClient（一体化模式）

```java
public class DirectClient implements ReasonixClient {
    private final ReasonixService service;

    public DirectClient(Config config) {
        this.service = new ReasonixServiceImpl(config);
    }

    @Override
    public Flux<StreamChunkDto> chatStream(ChatRequestDto request) {
        return service.chatStream(request);
    }
}
```

### TUI RestClient（分离服务模式）

```java
public class RestClient implements ReasonixClient {
    private final WebClient webClient;

    public RestClient(String serverUrl) {
        this.webClient = WebClient.builder()
            .baseUrl(serverUrl)
            .build();
    }

    @Override
    public Flux<StreamChunkDto> chatStream(ChatRequestDto request) {
        return webClient.post()
            .uri("/api/v1/chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(StreamChunkDto.class);
    }
}
```

### JavaFX RestClient

```java
public class JavaFxRestClient {
    private final HttpClient httpClient;
    private final String baseUrl;

    public CompletableFuture<String> chat(String message) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/chat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                JsonUtils.toJson(new ChatRequestDto(message))))
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body);
    }
}
```
            <version>5.7.0</version>
            <scope>test</scope>
        </dependency>

        <!-- Utilities -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.14.0</version>
        </dependency>
        <dependency>
            <groupId>commons-codec</groupId>
            <artifactId>commons-codec</artifactId>
            <version>1.16.0</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>25</source>
                    <target>25</target>
                    <compilerArgs>
                        <arg>--enable-preview</arg>
                    </compilerArgs>
                </configuration>
            </plugin>

            <!-- GraalVM Native Image -->
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <version>0.10.0</version>
                <configuration>
                    <mainClass>com.reasonix.ReasonixApplication</mainClass>
                    <buildArgs>
                        <buildArg>--enable-preview</buildArg>
                        <buildArg>-H:+ReportExceptionStackTraces</buildArg>
                        <buildArg>--no-fallback</buildArg>
                    </buildArgs>
                </configuration>
                <executions>
                    <execution>
                        <id>build-native</id>
                        <phase>package</phase>
                        <goals>
                            <goal>compile-no-fork</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## GraalVM Native Image

### 构建命令

```bash
# 使用 GraalVM 25
mvn clean package -Pnative

# 或直接使用 native-image
native-image \
  --enable-preview \
  -H:+ReportExceptionStackTraces \
  --no-fallback \
  -cp target/reasonix-java-1.0.0.jar \
  com.reasonix.ReasonixApplication \
  reasonix
```

### Native Image 配置

```json
// reflect-config.json
[
  {
    "name": "com.reasonix.loop.CacheFirstLoop",
    "allDeclaredConstructors": true,
    "allPublicMethods": true
  },
  {
    "name": "com.reasonix.tools.ToolDefinition",
    "allDeclaredConstructors": true,
    "allPublicMethods": true
  }
]
```

## 性能优化

### 1. 虚拟线程

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<ToolResult>> futures = toolCalls.stream()
        .map(call -> executor.submit(() -> dispatchTool(call)))
        .toList();

    for (var future : futures) {
        results.add(future.get());
    }
}
```

### 2. Tokenizer

```java
public class DeepSeekTokenizer {
    private final Map<String, Integer> vocab;
    private final List<String> mergeRules;

    public List<Integer> encode(String text) {
        List<Integer> tokens = new ArrayList<>();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return tokens;
    }

    public int countTokens(String text) {
        return encode(text).size();
    }
}
```

### 3. 缓存策略

```java
public class TokenCache {
    private final Map<String, CompletableFuture<CachedTokens>> cache =
        new ConcurrentHashMap<>();

    public CompletableFuture<CachedTokens> computeIfAbsent(String key) {
        return cache.computeIfAbsent(key, k ->
            CompletableFuture.supplyAsync(() -> tokenize(k))
        );
    }
}
```

## 移植优先级

| 优先级 | 模块 | 工作量 | 风险 | 说明 |
|--------|------|--------|------|------|
| P0 | Core Loop + Memory | 高 | 中 | 关键路径 |
| P0 | ToolRegistry + LangChain4j MCP | 中 | 低 | LangChain4j 已实现 |
| P0 | Tamboui TUI | 高 | 中 | 全功能 UI |
| P1 | LangChain4j ModelClient | 低 | 低 | 已有 DeepSeek 支持 |
| P1 | Context Manager | 中 | 中 | 折叠算法 |
| P2 | ACP Protocol | 低 | 低 | 协议定义 |
| P3 | Transcript | 低 | 低 | 可后置 |

## 已知挑战

### 1. AsyncGenerator → Flux

```java
public Flux<LoopEvent> dispatchToolCallsChunked(...) {
    return Flux.fromIterable(calls)
        .bufferUntil(call -> !isParallelSafe(call.name()))
        .flatMap(this::executeChunk);
}
```

### 2. JSON Schema 验证

```java
public record ToolArgs(
    @NotBlank String command,
    @Min(1) @Max(3600) Integer timeout
) {}
```

### 3. Shell 命令执行

```java
public String executeCommand(String command, Path cwd) {
    ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
    pb.directory(cwd.toFile());
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);

    Process process = pb.start();
    String output = new String(process.getInputStream().readAllBytes());
    int exitCode = process.waitFor();

    if (exitCode != 0) {
        throw new CommandFailedException(command, exitCode);
    }
    return output;
}
```

### 4. Tamboui 声明式 UI

```java
// 替代 Ink (React) 的声明式 UI
public class MessageCard extends TambouiView {
    private final ChatMessage msg;

    @Override
    public View build() {
        return VStack(
            HStack(
                Avatar(msg.role()),
                Text(msg.role()).withCss("role-label")
            ),
            Markdown(msg.content()).withCss("message-content")
        ).withCss(msg.role().equals("user") ? "user-card" : "assistant-card");
    }
}
```

### 5. GraalVM Native Image 反射配置

LangChain4j 和 Tamboui 需要反射配置：

```bash
# 自动生成配置
native-image-agent -cp target/classes:target/dependency/* \
  com.reasonix.ReasonixApplication

# 或手动配置 reflect-config.json
```

## 配置管理层

采用 **YAML 静态配置 + DuckDB 动态配置** 分层方案：

```
┌─────────────────────────────────────────────────────┐
│                   配置分层架构                        │
├─────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────────────┐ │
│  │  YAML 静态配置    │    │   DuckDB 动态配置       │ │
│  │  (版本控制)       │    │   (运行时管理)          │ │
│  ├─────────────────┤    ├─────────────────────────┤ │
│  │  • 默认 MCPs    │    │  • 用户添加的 MCPs      │ │
│  │  • Skill paths  │    │  • 工作区配置覆盖       │ │
│  │  • 系统参数      │    │  • Hooks 配置           │ │
│  │  • 日志级别     │    │  • 运行时开关           │ │
│  └─────────────────┘    └─────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 配置优先级

```
DuckDB 工作区覆盖  >  DuckDB 全局配置  >  YAML 默认值
```

### YAML 静态配置

```yaml
# ~/.reasonix/config.yaml
system:
  name: Reasonix
  version: "1.0.0"

skills:
  paths:
    - "~/.reasonix/skills"
    - "./.reasonix/skills"

mcp:
  defaultServers:
    - name: filesystem
      command: npx
      args: ["@modelcontextprotocol/server-filesystem", "/"]
    - name: fetch
      command: npx
      args: ["@modelcontextprotocol/server-fetch"]
      env:
        ALLOWED_URLS: "https://*"

defaults:
  model: deepseek-chat
  temperature: 0.7
  maxTokens: 64000
```

### DuckDB 动态配置表

```sql
CREATE TABLE IF NOT EXISTS mcp_servers (
    id VARCHAR PRIMARY KEY,
    name VARCHAR NOT NULL,
    command VARCHAR NOT NULL,
    args JSON,
    env JSON,
    config JSON,
    workspace VARCHAR,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS hooks (
    id VARCHAR PRIMARY KEY,
    event VARCHAR NOT NULL,
    action VARCHAR NOT NULL,
    skill_id VARCHAR,
    config JSON,
    workspace VARCHAR,
    enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workspace_configs (
    workspace VARCHAR PRIMARY KEY,
    skill_paths JSON,
    mcp_servers JSON,
    model VARCHAR,
    temperature DECIMAL(3,2),
    maxTokens INTEGER,
    customSettings JSON,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mcp_workspace ON mcp_servers(workspace);
CREATE INDEX IF NOT EXISTS idx_hooks_event ON hooks(event);
CREATE INDEX IF NOT EXISTS idx_hooks_workspace ON hooks(workspace);
```

### ConfigManager 实现

```java
public class ConfigManager {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Connection duckdb;
    private final SystemConfig systemConfig;

    public ConfigManager() {
        this.systemConfig = loadSystemConfig();
        this.duckdb = DuckDBConnectionManager.getConnection();
    }

    public SystemConfig loadSystemConfig() {
        var configPath = Path.of(System.getProperty("user.home"), ".reasonix", "config.yaml");
        if (Files.exists(configPath)) {
            return yamlMapper.readValue(configPath.toFile(), SystemConfig.class);
        }
        return yamlMapper.readValue(
            getClass().getResourceAsStream("/default-config.yaml"),
            SystemConfig.class
        );
    }

    public List<McpServer> getMcpServers(String workspace) {
        List<McpServer> servers = new ArrayList<>();

        for (var server : systemConfig.getMcp().getDefaultServers()) {
            servers.add(server);
        }

        String sql = """
            SELECT * FROM mcp_servers
            WHERE workspace = ? OR workspace IS NULL
            ORDER BY workspace NULLS FIRST
        """;

        try (var stmt = duckdb.prepareStatement(sql)) {
            stmt.setString(1, workspace);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                servers.add(parseMcpServer(rs));
            }
        }

        return servers;
    }

    public <T> T getConfig(String workspace, String key, Class<T> type) {
        String sql = """
            SELECT customSettings FROM workspace_configs WHERE workspace = ?
        """;

        try (var stmt = duckdb.prepareStatement(sql)) {
            stmt.setString(1, workspace);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                var settings = new ObjectMapper().readValue(rs.getString("customSettings"), Map.class);
                if (settings.containsKey(key)) {
                    return (T) settings.get(key);
                }
            }
        }

        return systemConfig.get(key, type);
    }

    public void addMcpServer(String workspace, McpServer server) { /* INSERT */ }
    public void updateHook(String id, Hook hook) { /* UPDATE */ }
    public void deleteHook(String id) { /* DELETE FROM hooks */ }
}
```

### 配置存储对比

| 配置项 | 存储 | 说明 |
|--------|------|------|
| **Skills** | `skill.md` 文件 + YAML paths | Skill 本身是 Markdown，只需配置查找路径 |
| **默认 MCPs** | YAML | 基础功能、开箱即用 |
| **用户自定义 MCPs** | DuckDB | 多工作区隔离、动态 CRUD |
| **Hooks** | DuckDB | 动态启用/禁用 |
| **工作区覆盖** | DuckDB | 运行时修改 |
| **系统参数** | YAML | 不常修改、版本控制 |

## 技能系统 (skills/)

Skills 是 **Markdown 文件**，无需数据库存储，只需配置查找路径。

### Skill 文件格式

```markdown
---
name: my-skill
description: 这是我的技能
scope: project
---

# my-skill

这里是技能的提示词内容...
```

### Skill 文件位置

```
~/.reasonix/skills/              # 用户全局技能
    └── my-skill.md

<project>/.reasonix/skills/      # 项目本地技能
    └── code-review.md

<custom-path>/                   # 自定义路径（通过 YAML 或 DuckDB 配置）
    └── deploy.md
```

### SkillLoader 实现

```java
public class SkillLoader {

    public List<Skill> loadAllSkills(String workspaceRoot) {
        List<Skill> skills = new ArrayList<>();
        Set<Path> skillDirs = getSkillDirectories(workspaceRoot);

        for (Path dir : skillDirs) {
            if (!Files.exists(dir)) continue;

            try (var files = Files.list(dir)) {
                files.filter(p -> p.toString().endsWith(".md"))
                     .forEach(p -> skills.add(loadSkill(p)));
            }
        }

        return skills;
    }

    private Set<Path> getSkillDirectories(String workspaceRoot) {
        Set<Path> paths = new LinkedHashSet<>();

        paths.add(Path.of(System.getProperty("user.home"), ".reasonix", "skills"));
        paths.add(Path.of(workspaceRoot, ".reasonix", "skills"));

        var config = configManager.getConfig(workspaceRoot);
        if (config.getSkillPaths() != null) {
            for (String p : config.getSkillPaths()) {
                paths.add(Path.of(p).isAbsolute()
                    ? Path.of(p)
                    : Path.of(workspaceRoot).resolve(p));
            }
        }

        return paths;
    }

    private Skill loadSkill(Path path) {
        String content = Files.readString(path);
        var frontmatter = parseFrontmatter(content);
        String body = stripFrontmatter(content);

        return new Skill(
            frontmatter.get("name"),
            frontmatter.get("description"),
            SkillScope.valueOf(frontmatter.getOrDefault("scope", "project")),
            path,
            body
        );
    }

    public Skill createSkill(String name, String workspaceRoot) {
        Path dir = Path.of(workspaceRoot, ".reasonix", "skills");
        Files.createDirectories(dir);

        Path file = dir.resolve(name + ".md");
        String template = "---\nname: " + name + "\nscope: project\n---\n\n# " + name + "\n\n";
        Files.writeString(file, template);

        return loadSkill(file);
    }
}
```

### Skill 作用域优先级

```
project (.reasonix/skills/)  >  user (~/.reasonix/skills/)  >  custom paths
```

## 配置常量

| 常量 | 值 | 说明 |
|------|-----|------|
| `HISTORY_FOLD_THRESHOLD` | `0.75` | 折叠触发阈值 |
| `HISTORY_FOLD_TAIL_FRACTION` | `0.2` | 折叠保留尾部比例 |
| `AGGRESSIVE_THRESHOLD` | `0.78` | 激进折叠阈值 |
| `AGGRESSIVE_TAIL_FRACTION` | `0.1` | 激进折叠尾部比例 |
| `FORCE_SUMMARY_THRESHOLD` | `0.8` | 强制摘要阈值 |
| `TURN_START_FOLD_THRESHOLD` | `0.9` | turn-start 预检阈值 |
| `DEFAULT_MAX_RESULT_CHARS` | `16000` | 工具结果字符上限 |
| `DEFAULT_MAX_RESULT_TOKENS` | `12000` | 工具结果 token 上限 |

## DeepSeek 定价

```java
public record Pricing(
    double inputCacheHitPerM,
    double inputCacheMissPerM,
    double outputPerM
) {
    public static final Map<String, Pricing> DEEPSEEK = Map.of(
        "deepseek-v4-flash", new Pricing(0.0028, 0.14, 0.28),
        "deepseek-v4-pro",   new Pricing(0.003625, 0.435, 0.87)
    );
}

public double costUsd(String model, Usage usage) {
    Pricing p = Pricing.DEEPSEEK.get(model);
    return (usage.promptCacheHitTokens() * p.inputCacheHitPerM() +
            usage.promptCacheMissTokens() * p.inputCacheMissPerM() +
            usage.completionTokens() * p.outputPerM()) / 1_000_000;
}
```

## LangChain4j MCP 集成

```java
public class McpToolProvider {
    private final McpClient mcpClient;

    public McpToolProvider(String mcpServerPath) {
        this.mcpClient = McpClient.builder()
            .transport(StdioTransport.create(mcpServerPath))
            .build();
    }

    public List<ToolSpecification> getTools() {
        return mcpClient.listTools()
            .stream()
            .map(tool -> ToolSpecification.builder()
                .name(tool.name())
                .description(tool.description())
                .parameters(tool.inputSchema())
                .build())
            .toList();
    }

    public ToolExecutionRequest execute(String toolName, Map<String, Object> args) {
        String result = mcpClient.callTool(toolName, args);
        return ToolExecutionRequest.builder()
            .id(UUID.randomUUID().toString())
            .name(toolName)
            .result(result)
            .build();
    }
}
```

## Tamboui CSS 样式示例

```css
/* styles/app.css */
.app-container {
    width: 100%;
    height: 100%;
    background: #1a1a2e;
}

.chat-container {
    flex: 1;
    padding: 8px;
    overflow-y: scroll;
}

.composer {
    padding: 8px;
    background: #16213e;
    border-top: 1px solid #0f3460;
}

.input-field {
    flex: 1;
    background: #0f3460;
    color: #e94560;
    border-radius: 4px;
    padding: 8px;
}

.send-button {
    background: #e94560;
    color: white;
    padding: 8px 16px;
    border-radius: 4px;
}

.tool-card {
    margin: 4px 0;
    padding: 8px;
    background: #16213e;
    border-radius: 4px;
}

.tool-name {
    color: #e94560;
    font-weight: bold;
}

.success {
    background: #00ff88;
}

.error {
    background: #ff4444;
}

.usage-bar {
    padding: 4px 8px;
    background: #0f3460;
    color: #a2a2a2;
}
```

## Java 源码解析 (java/)

Java 源码解析器：从项目目录 + Maven/Gradle 缓存 + javap 反编译三个层次查找源码。

```java
public class ClassSourceFinder {
    private final Path projectRoot;
    private final List<Path> repoPaths;
    private final String javapCommand;
    private final int maxJarScan;

    public ClassSourceFinder(ClassSourceFinderOptions options) {
        this.projectRoot = Path.of(options.projectRoot);
        this.repoPaths = options.repoPaths != null
            ? Arrays.asList(options.repoPaths)
            : defaultRepoPaths();
        this.javapCommand = options.javapCommand ?? "javap";
        this.maxJarScan = options.maxJarScan ?? 2000;
    }

    public FindResult findSource(String fullyQualifiedName, FindSourceOptions opts) {
        var projectResult = searchProject(fullyQualifiedName);
        if (projectResult.found()) return projectResult;
        return searchRepositories(fullyQualifiedName, opts?.jarKeyword());
    }

    private FindResult searchProject(String fqn) {
        String simpleName = simpleClassName(fqn);
        List<String> suffixes = List.of(simpleName + ".java", simpleName + ".java.txt");
        Queue<Path> queue = new LinkedList<>();
        queue.add(projectRoot);
        while (!queue.isEmpty()) {
            Path dir = queue.poll();
            for (var entry : Files.list(dir).toList()) {
                if (Files.isDirectory(entry)) {
                    if (!SKIP_DIRS.contains(entry.getFileName().toString())) {
                        queue.add(entry);
                    }
                } else if (suffixes.contains(entry.getFileName().toString())) {
                    return new FindResultSuccess(true,
                        Files.readString(entry),
                        "project",
                        entry.toString());
                }
            }
        }
        return new FindResultNotFound(false, "not-found");
    }
}
```

### ZIP/JAR 解析器

```java
public class ZipReader {
    public JarEntry readJarEntry(Path jarPath, String entryName) {
        try (var fs = FileSystems.newFileSystem(jarPath)) {
            var path = fs.getPath(entryName);
            if (!Files.exists(path)) return null;
            var bytes = Files.readAllBytes(path);
            return new JarEntry(entryName, bytes);
        }
    }

    public List<String> listJarEntries(Path jarPath) {
        try (var fs = FileSystems.newFileSystem(jarPath)) {
            return Files.walk(fs.getPath("/"))
                .filter(Files::isRegularFile)
                .map(p -> p.toString().substring(1))
                .toList();
        }
    }
}
```

## 代码查询 (code-query/)

Tree-sitter 语法分析 + 语义查询，实现符号提取、代码搜索、定义/引用分类。

### 语法映射

```java
public enum GrammarName { typescript, tsx, javascript, python, go, rust, java }

public class GrammarMap {
    private static final Map<String, GrammarName> EXT_MAP = Map.of(
        ".ts", GrammarName.typescript,
        ".tsx", GrammarName.tsx,
        ".js", GrammarName.javascript,
        ".py", GrammarName.python,
        ".go", GrammarName.go,
        ".rs", GrammarName.rust,
        ".java", GrammarName.java
    );

    public GrammarName forPath(String filePath) {
        for (var ext : EXT_MAP.keySet()) {
            if (filePath.toLowerCase().endsWith(ext)) {
                return EXT_MAP.get(ext);
            }
        }
        return null;
    }
}
```

### 符号提取

```java
public record CodeSymbol(
    String name,
    SymbolKind kind,
    int line,
    int column,
    int endLine,
    int endColumn,
    String parent
) {}

public enum SymbolKind { function, class, interface, type, enum, method, property, namespace }

public class SymbolExtractor {
    private static final Map<GrammarName, String> QUERIES = Map.of(
        GrammarName.java, """
            (class_declaration name: (identifier) @name) @class
            (interface_declaration name: (identifier) @name) @interface
            (method_declaration name: (identifier) @name) @method
            (field_declaration declarator: (variable_declarator name: (identifier) @name)) @property
            """
    );

    public List<CodeSymbol> extract(Path filePath, String source) {
        var grammar = grammarMap.forPath(filePath.toString());
        var parser = treeSitterParser(grammar);
        var tree = parser.parse(source);
        var query = new Query(language, QUERIES.get(grammar));
        return query.matches(tree.rootNode).stream()
            .map(this::matchToSymbol)
            .toList();
    }
}
```

### 代码搜索

```java
public record CodeMatch(int line, int column, CodeMatchKind kind, String snippet) {}

public enum CodeMatchKind { call, definition, reference }

public class CodeSearch {
    public List<CodeMatch> findInCode(Path filePath, String source, String name) {
        var tree = parser.parse(source);
        var matches = new ArrayList<CodeMatch>();
        walk(tree.rootNode, node -> {
            if (!isIdentifier(node) || !node.text().equals(name)) return;
            var kind = classify(node);
            matches.add(new CodeMatch(
                node.startPosition().row() + 1,
                node.startPosition().column() + 1,
                kind,
                sourceLines[node.startPosition().row()]
            ));
        });
        return matches;
    }
}
```

## 代码编辑 (code/)

### 编辑块解析

```java
public record EditBlock(String path, String search, String replace, int offset) {}

public class EditBlocks {
    private static final Pattern BLOCK_RE = Pattern.compile(
        "^(\\S[^\\n]*)\\n<{7} SEARCH\\n([\\s\\S]*?)\\n?={7}\\n([\\s\\S]*?)\\n?>{7} REPLACE",
        Pattern.MULTILINE
    );

    public List<EditBlock> parse(String text) {
        var out = new ArrayList<EditBlock>();
        var m = BLOCK_RE.matcher(text);
        while (m.find()) {
            out.add(new EditBlock(
                m.group(1).trim(),
                m.group(2),
                m.group(3),
                m.start()
            ));
        }
        return out;
    }
}
```

### 原子文件写入

```java
public class AtomicEdit {
    public ApplyResult apply(EditBlock block, Path rootDir) {
        var absTarget = resolvePath(rootDir, block.path());
        if (!isUnder(absTarget, rootDir)) {
            return new ApplyResult(block.path(), "path-escape");
        }
        if (block.search().isEmpty()) {
            Files.createDirectories(absTarget.getParent());
            Files.writeString(absTarget, block.replace());
            return new ApplyResult(block.path(), "created");
        }
        var content = Files.readString(absTarget);
        if (!content.contains(block.search())) {
            return new ApplyResult(block.path(), "not-found");
        }
        var updated = content.replace(block.search(), block.replace());
        writeAtomic(absTarget, updated);
        return new ApplyResult(block.path(), "applied");
    }

    private void writeAtomic(Path path, String content) throws IOException {
        var tmp = path.resolveSibling(path.getFileName() + "." + PID + "." + timestamp + ".tmp");
        Files.writeString(tmp, content);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
```

### Diff 预览

```java
public class DiffPreview {
    public List<String> formatEditBlockDiff(EditBlock block, int contextLines, int maxLines) {
        var search = block.search().isEmpty() ? List.of() : block.search().split("\n");
        var replace = block.replace().split("\n");

        // 找公共前缀/后缀
        int leading = commonPrefixLen(search, replace);
        int trailing = commonSuffixLen(search, replace, leading);

        // ... 生成 unified diff
    }

    public List<SplitDiffRow> formatSplitDiff(EditBlock block, int startLine) {
        // 左右两栏：删除 vs 添加
    }
}
```

## 工程生命周期 (code/EngineeringLifecycleRuntime.java)

状态机：idle → armed → planning → approved → executing → checkpoint → complete

```java
public class EngineeringLifecycleRuntime {
    private EngineeringLifecycleMode mode = "off";
    private EngineeringLifecycleState state = "idle";
    private List<PlanStep> planSteps = new ArrayList<>();
    private Set<String> completedStepIds = new HashSet<>();
    private boolean mutatedSinceLastStep = false;

    public ToolInterceptor guardToolCall = (name, args) -> {
        if (mode == "off") return null;
        if (!isHighRiskLifecycleToolCall(name, args)) return null;
        if (state != "approved" && state != "executing") {
            return """
                {"error": "%s blocked by Engineering Lifecycle",
                 "rejectedReason": "engineering-lifecycle",
                 "state": "%s",
                 "nextAction": "submit_plan"}""".formatted(name, state);
        }
        return null;
    };
}

public enum LifecyclePolicyRisk { safe, mutation, high-risk }

public class LifecyclePolicy {
    private static final Set<String> SAFE_TOOLS = Set.of(
        "read_file", "list_directory", "search_files", "search_content",
        "glob", "get_file_info", "semantic_search", "web_search",
        "todo_write", "ask_choice", "submit_plan"
    );

    private static final Set<String> HIGH_RISK_TOOLS = Set.of(
        "multi_edit", "move_file", "delete_file", "run_background"
    );

    public LifecyclePolicyDecision classify(String toolName, Map<String, Object> args) {
        if (HIGH_RISK_TOOLS.contains(toolName)) {
            return new LifecyclePolicyDecision(toolName, "high-risk", "high-risk-tool");
        }
        if (SAFE_TOOLS.contains(toolName)) {
            return new LifecyclePolicyDecision(toolName, "safe", "safe-tool");
        }
        // package.json, tsconfig 等高风险路径检测
        if (toolName.equals("write_file") && isPackagePath(args.get("path"))) {
            return new LifecyclePolicyDecision(toolName, "high-risk", "package-or-config-path");
        }
        return new LifecyclePolicyDecision(toolName, "safe", "default-safe");
    }
}
```

## 检查点 (code/Checkpoint.java)

会话快照：创建/恢复/删除，支持自动快照和手动快照。

```java
public record Checkpoint(
    String id,
    String name,
    Path rootDir,
    Instant createdAt,
    CheckpointSource source,
    List<CheckpointFile> files,
    long bytes
) {}

public record CheckpointFile(Path path, String content) {}

public class CheckpointStore {
    private Path storeRoot(Path rootDir) {
        return Path.of(System.getProperty("user.home"),
            ".reasonix", "sessions", sanitizeRoot(rootDir), "checkpoints");
    }

    public CheckpointMeta createCheckpoint(Path rootDir, String name,
            CheckpointSource source, List<Path> paths) {
        var files = paths.stream().map(p -> {
            if (Files.exists(p)) {
                return new CheckpointFile(p.relative(rootDir), Files.readString(p));
            }
            return new CheckpointFile(p.relative(rootDir), null);
        }).toList();

        var checkpoint = new Checkpoint(id(), name, rootDir, Instant.now(), source, files, bytes);
        saveCheckpoint(checkpoint);
        return toMeta(checkpoint);
    }

    public RestoreResult restore(Path rootDir, String idOrName) {
        var meta = findCheckpoint(rootDir, idOrName);
        var checkpoint = loadCheckpoint(rootDir, meta.id());
        var result = new RestoreResult();

        for (var file : checkpoint.files()) {
            var abs = rootDir.resolve(file.path());
            if (file.content() == null) {
                Files.deleteIfExists(abs);
                result.removed().add(file.path());
            } else {
                Files.writeString(abs, file.content());
                result.restored().add(file.path());
            }
        }
        return result;
    }
}
```

## 语义搜索 (index/semantic/)

Ollama/OpenAI-compatible embedding + 余弦相似度搜索。

### 向量化

```java
public interface EmbeddingProvider {
    Float32Array embed(String text, CancellationSignal signal);
    Float32Array[] embedAll(List<String> texts, ProgressCallback onProgress);
}

public class OllamaEmbedding implements EmbeddingProvider {
    private final String baseUrl;
    private final String model;

    public Float32Array embed(String text, CancellationSignal signal) {
        var res = HttpClient.newHttpClient().send(
            Request.POST("%s/api/embeddings".formatted(baseUrl))
                .header("Content-Type", "application/json")
                .body("""
                    {"model": "%s", "prompt": "%s"}""".formatted(model,
                        text.replace("\"", "\\\"")).getBytes()),
            HttpResponse.BodyHandlers.ofString()
        );
        var json = JSON.parse(res.body());
        return toFloat32Array(json.get("embedding"));
    }
}

public class OpenAICompatEmbedding implements EmbeddingProvider {
    // OpenAI-compatible API (LocalAI, etc.)
}
```

### 语义存储

```java
public class SemanticStore {
    private List<IndexEntry> entries = new ArrayList<>();
    private Map<String, List<IndexEntry>> byPath = new HashMap<>();
    private int dim = 0;

    public void add(List<IndexEntry> newEntries) {
        for (var e : newEntries) {
            entries.add(e);
            byPath.computeIfAbsent(e.path(), k -> new ArrayList<>()).add(e);
        }
        appendToJsonl(newEntries);
    }

    public List<SearchHit> search(Float32Array query, int topK, float minScore) {
        var heap = new PriorityQueue<SearchHit>(Comparator.comparingDouble(h -> h.score()));
        for (var entry : entries) {
            var score = cosine(query, entry.embedding());
            if (score < minScore) continue;
            heap.add(new SearchHit(entry, score));
            if (heap.size() > topK) heap.poll();
        }
        return heap.stream().sorted(Comparator.comparingDouble(h -> -h.score())).toList();
    }

    private float cosine(Float32Array a, Float32Array b) {
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

### 分块器

```java
public record CodeChunk(Path path, int startLine, int endLine, String text) {}

public class Chunker {
    public List<CodeChunk> chunk(Path filePath, String content,
            ChunkerOptions opts) {
        int windowLines = opts.windowLines();
        int overlap = opts.overlap();
        int maxChars = opts.maxChunkChars();

        var lines = content.split("\n");
        var chunks = new ArrayList<CodeChunk>();
        int stride = Math.max(1, windowLines - overlap);

        for (int start = 0; start < lines.length; start += stride) {
            int end = Math.min(lines.length, start + windowLines);
            var text = String.join("\n", Arrays.copyOfRange(lines, start, end)).trim();
            if (text.isEmpty()) continue;

            var chunk = new CodeChunk(filePath, start + 1, end, text);
            chunks.addAll(safeSplit(chunk, maxChars));
        }
        return chunks;
    }
}
```

## 存储层 (DuckDB)

采用 **DuckDB** 作为统一存储引擎，内嵌式无需独立进程，支持 SQL 查询和向量化执行。

### Maven 依赖

```xml
<dependency>
    <groupId>org.duckdb</groupId>
    <artifactId>duckdb-jdbc</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 连接管理

```java
public class DuckDBConnectionManager {
    private static final String DB_PATH = System.getProperty("user.home") + "/.reasonix/reasonix.db";

    public static Connection getConnection() throws SQLException {
        String jdbcUrl = "jdbc:duckdb:" + DB_PATH;
        Connection conn = DriverManager.getConnection(jdbcUrl);
        conn.setAutoCommit(false);
        return conn;
    }

    public static void initializeSchema(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memories (
                    id VARCHAR PRIMARY KEY,
                    type VARCHAR NOT NULL,
                    content VARCHAR NOT NULL,
                    metadata JSON,
                    workspace VARCHAR,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_pinned BOOLEAN DEFAULT FALSE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id VARCHAR PRIMARY KEY,
                    workspace_root VARCHAR NOT NULL,
                    model VARCHAR,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    cost_usd DECIMAL(10, 6) DEFAULT 0,
                    cache_hit_ratio DECIMAL(5, 4) DEFAULT 0,
                    turn_count INTEGER DEFAULT 0,
                    is_active BOOLEAN DEFAULT TRUE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id VARCHAR PRIMARY KEY,
                    session_id VARCHAR NOT NULL,
                    role VARCHAR NOT NULL,
                    content VARCHAR,
                    reasoning VARCHAR,
                    tool_calls JSON,
                    tool_results JSON,
                    usage JSON,
                    model VARCHAR,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (session_id) REFERENCES sessions(id)
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memories_type ON memories(type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memories_workspace ON memories(workspace)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_workspace ON sessions(workspace_root)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id)");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mcp_servers (
                    id VARCHAR PRIMARY KEY,
                    name VARCHAR NOT NULL,
                    command VARCHAR NOT NULL,
                    args JSON,
                    env JSON,
                    config JSON,
                    workspace VARCHAR,
                    enabled BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS hooks (
                    id VARCHAR PRIMARY KEY,
                    event VARCHAR NOT NULL,
                    action VARCHAR NOT NULL,
                    skill_id VARCHAR,
                    config JSON,
                    workspace VARCHAR,
                    enabled BOOLEAN DEFAULT TRUE,
                    priority INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS workspace_configs (
                    workspace VARCHAR PRIMARY KEY,
                    skill_paths JSON,
                    mcp_servers JSON,
                    model VARCHAR,
                    temperature DECIMAL(3,2),
                    maxTokens INTEGER,
                    customSettings JSON,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mcp_workspace ON mcp_servers(workspace)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_hooks_event ON hooks(event)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_hooks_workspace ON hooks(workspace)");

            conn.commit();
        }
    }
}
```

### 记忆存储 (DuckDBMemoryStore)

```java
public class DuckDBMemoryStore implements MemoryStore {

    public void remember(String content, MemoryType type, Map<String, Object> metadata) {
        String id = UUID.randomUUID().toString();

        try (var conn = DuckDBConnectionManager.getConnection();
             var pstmt = conn.prepareStatement("""
                 INSERT INTO memories (id, type, content, metadata, workspace, created_at, accessed_at)
                 VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
             """)
        ) {
            pstmt.setString(1, id);
            pstmt.setString(2, type.name().toLowerCase());
            pstmt.setString(3, content);
            pstmt.setString(4, new ObjectMapper().writeValueAsString(metadata));
            pstmt.setString(5, metadata.getOrDefault("workspace", "").toString());
            pstmt.execute();
            conn.commit();
        }
    }

    public List<Memory> recall(String query, MemoryType type, int limit) {
        String sql = """
            SELECT * FROM memories
            WHERE type = ?
              AND (content LIKE ? OR ? = 'all')
            ORDER BY
                CASE WHEN is_pinned THEN 1 ELSE 2 END,
                accessed_at DESC
            LIMIT ?
        """;

        try (var conn = DuckDBConnectionManager.getConnection();
             var pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, type.name().toLowerCase());
            pstmt.setString(2, "%" + query + "%");
            pstmt.setString(3, type == null ? "all" : type.name());
            pstmt.setInt(4, limit);

            var rs = pstmt.executeQuery();
            return parseMemories(rs);
        }
    }
}
```

### 会话存储 (DuckDBSessionStore)

```java
public class DuckDBSessionStore implements SessionStore {

    public void appendMessage(String sessionId, ChatMessage message) {
        String id = UUID.randomUUID().toString();

        try (var conn = DuckDBConnectionManager.getConnection();
             var pstmt = conn.prepareStatement("""
                 INSERT INTO messages (id, session_id, role, content, reasoning, tool_calls, tool_results, usage, model)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
             """)
        ) {
            pstmt.setString(1, id);
            pstmt.setString(2, sessionId);
            pstmt.setString(3, message.role());
            pstmt.setString(4, message.content());
            pstmt.setString(5, message.reasoning());
            pstmt.setString(6, toJson(message.toolCalls()));
            pstmt.setString(7, toJson(message.toolResults()));
            pstmt.setString(8, toJson(message.usage()));
            pstmt.setString(9, message.model());
            pstmt.execute();

            updateSessionStats(conn, sessionId, message.usage());
            conn.commit();
        }
    }

    public List<ChatMessage> getSessionMessages(String sessionId) {
        try (var conn = DuckDBConnectionManager.getConnection();
             var stmt = conn.createStatement()
        ) {
            var rs = stmt.executeQuery("""
                SELECT * FROM messages
                WHERE session_id = '""" + sessionId + """'
                ORDER BY created_at
            """);
            return parseMessages(rs);
        }
    }

    private void updateSessionStats(Connection conn, String sessionId, Usage usage) throws SQLException {
        String sql = """
            UPDATE sessions SET
                updated_at = CURRENT_TIMESTAMP,
                turn_count = turn_count + 1,
                cost_usd = cost_usd + ?
            WHERE id = ?
        """;

        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, usage.calculateCost());
            pstmt.setString(2, sessionId);
            pstmt.execute();
        }
    }
}
```

### 成本分析查询

```java
public class AnalyticsQuery {

    public Map<String, Object> getCostBreakdown(String workspace, DateRange range) {
        String sql = """
            SELECT
                model,
                COUNT(*) as session_count,
                SUM(turn_count) as total_turns,
                SUM(cost_usd) as total_cost,
                AVG(cache_hit_ratio) as avg_cache_hit
            FROM sessions
            WHERE workspace_root = ?
              AND created_at BETWEEN ? AND ?
            GROUP BY model
        """;
        // ...
    }

    public double getCacheHitRatio(String sessionId) {
        String sql = """
            SELECT usage FROM messages
            WHERE session_id = ? AND usage IS NOT NULL
        """;
        // 计算加权平均缓存命中率
    }
}
```

### 备份与迁移

```java
public class BackupTool {

    public void exportToParquet(String table, Path outputPath) {
        try (var conn = DuckDBConnectionManager.getConnection();
             var stmt = conn.createStatement()
        ) {
            stmt.execute("COPY " + table + " TO '" + outputPath + "' (FORMAT PARQUET)");
        }
    }

    public void importFromParquet(String table, Path inputPath) {
        try (var conn = DuckDBConnectionManager.getConnection();
             var stmt = conn.createStatement()
        ) {
            stmt.execute("COPY " + table + " FROM '" + inputPath + "' (FORMAT PARQUET)");
        }
    }
}
```

## 事件持久化 (adapters/)

JSONL 格式保留用于事件溯源和重放功能，DuckDB 作为主要存储。

```java
public interface EventSink {
    void append(Event ev);
    Promise<Void> flush();
    Promise<Void> close();
}

public class JsonlEventSink implements EventSink {
    private final BufferedWriter writer;
    private int buffered = 0;

    public JsonlEventSink(Path path) {
        Files.createDirectories(path.getParent());
        this.writer = Files.newBufferedWriter(path,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void append(Event ev) {
        if (ev.type() == "model.delta") return;
        writer.write(JSON.stringify(ev));
        writer.newLine();
        buffered++;
    }
}
```

### 存储方案对比

| 组件 | 存储方案 | 查询能力 |
|------|----------|----------|
| **记忆 (Memory)** | DuckDB | ✅ 全文搜索、类型过滤、时间排序 |
| **会话 (Session)** | DuckDB | ✅ 按工作区、时间范围检索 |
| **消息 (Messages)** | DuckDB | ✅ 按会话加载、聚合分析 |
| **事件日志 (Event Log)** | JSONL 保留 | ✅ 重放、调试 |

## 计划持久化 (code/PlanState.java)

```java
public record PlanState(
    int version,
    List<PlanStep> steps,
    List<String> completedStepIds,
    Map<String, StepCompletion> stepCompletions,
    Instant updatedAt,
    String body,
    String summary
) {}

public class PlanStore {
    public PlanState load(String sessionName) {
        var path = planStatePath(sessionName);
        if (!Files.exists(path)) return null;
        var raw = Files.readString(path);
        return JSON.parse(raw, PlanState.class);
    }

    public void save(String sessionName, PlanState state) {
        var path = planStatePath(sessionName);
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.stringify(state));
    }

    public void archive(String sessionName) {
        var active = planStatePath(sessionName);
        if (!Files.exists(active)) return;
        var archive = active.resolveSibling(
            active.getFileName() + "." + Instant.now() + ".done.json");
        Files.move(active, archive);
    }
}
```

## 移植优先级（更新）

| 优先级 | 模块 | 工作量 | 风险 | 说明 |
|--------|------|--------|------|------|
| P0 | Core Loop + Memory | 高 | 中 | 关键路径 |
| P0 | ToolRegistry + LangChain4j MCP | 中 | 低 | LangChain4j 已实现 |
| P0 | Tamboui TUI | 高 | 中 | 全功能 UI |
| P0 | **ClassSourceFinder + ZipReader** | 高 | **中** | Java JAR 解析需原生实现 |
| P0 | **TreeSitter 替代** | 高 | **高** | Java 无 TreeSitter，可用 ANTLR |
| P1 | LangChain4j ModelClient | 低 | 低 | 已有 DeepSeek 支持 |
| P1 | Context Manager | 中 | 中 | 折叠算法 |
| P1 | **Semantic Search** | 高 | **高** | embedding 向量化需 LangChain4j |
| P2 | ACP Protocol | 低 | 低 | 协议定义 |
| P2 | **Checkpoints + PlanStore** | 中 | 低 | 文件系统操作 |
| P2 | **Lifecycle Runtime** | 中 | 中 | 状态机 |
| P3 | Transcript | 低 | 低 | 可后置 |

## 已知挑战（更新）

### 1. Tree-sitter → ANTLR

Java 无 Tree-sitter，可用 ANTLR4 替代：

```java
public class AntlrSymbolExtractor {
    private final Map<GrammarName, Lexer> lexers;
    private final Map<GrammarName, Parser> parsers;

    public List<CodeSymbol> extract(Path filePath, String source) {
        var grammar = grammarMap.forPath(filePath.toString());
        var lexer = lexers.get(grammar);
        var tokens = lexer.getAllTokens();
        var parser = parsers.get(grammar);
        var tree = parser.parse(source);
        return walkTree(tree);
    }
}
```

### 2. embedding 向量化

LangChain4j 支持多种 embedding provider：

```java
@Bean
public EmbeddingModel embeddingModel() {
    return OllamaEmbeddingModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("nomic-embed-text")
        .build();
}

// 或 OpenAI-compatible
return OpenAiEmbeddingModel.builder()
    .apiKey(apiKey)
    .modelName("text-embedding-3-small")
    .build();
```

## API 客户端 (client/DeepSeekClient.java)

DeepSeek API 客户端实现，包含 Token 使用统计和流式响应处理。

```java
public class Usage {
    private long promptTokens;
    private long completionTokens;
    private long promptCacheHitTokens;
    private long promptCacheMissTokens;

    public double getCacheHitRatio() {
        long denom = promptCacheHitTokens + promptCacheMissTokens;
        return denom > 0 ? (double) promptCacheHitTokens / denom : 0;
    }

    public double calculateCost() {
        // DeepSeek v4-flash 定价
        double inputCost = (promptCacheHitTokens * 0.0028 + promptCacheMissTokens * 0.14) / 1_000_000;
        double outputCost = (completionTokens * 0.28) / 1_000_000;
        return inputCost + outputCost;
    }

    public static Usage fromApi(RawUsage raw) {
        if (raw == null) return new Usage();
        long promptTokens = raw.promptTokens != null ? raw.promptTokens : 
                           (raw.promptEvalCount != null ? raw.promptEvalCount : 0);
        long completionTokens = raw.completionTokens != null ? raw.completionTokens : 
                               (raw.evalCount != null ? raw.evalCount : 0);
        long cacheHitTokens = raw.promptCacheHitTokens != null ? raw.promptCacheHitTokens : 0;
        long cacheMissTokens = raw.promptCacheMissTokens != null ? raw.promptCacheMissTokens : 
                              Math.max(0, promptTokens - cacheHitTokens);
        return new Usage(promptTokens, completionTokens, cacheHitTokens, cacheMissTokens);
    }
}

public class ChatResponse {
    private String content;
    private String reasoningContent;
    private List<ToolCall> toolCalls;
    private Usage usage;
    private Object raw;
}

public class StreamChunk {
    private String contentDelta;
    private String reasoningDelta;
    private ToolCallDelta toolCallDelta;
    private Usage usage;
    private String finishReason;
    private Object raw;
}

public class DeepSeekClient {
    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final RateLimiter rateLimiter;
    private final RetryPolicy retryPolicy;

    public Mono<ChatResponse> chatCompletion(List<ChatMessage> messages, 
                                             List<ToolSpec> tools,
                                             ChatRequestOptions options) {
        // 构建请求体
        var request = new ChatCompletionRequest(messages, tools, options);
        return httpClient.post()
            .uri(baseUrl + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(sanitizeJsonTransport(request))
            .retrieve()
            .bodyToMono(ChatResponse.class)
            .retryWhen(retryPolicy.toRetrySpec());
    }

    public Flux<StreamChunk> streamChatCompletion(List<ChatMessage> messages,
                                                  List<ToolSpec> tools,
                                                  ChatRequestOptions options) {
        return httpClient.post()
            .uri(baseUrl + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(sanitizeJsonTransport(options.withStream(true)))
            .retrieve()
            .bodyToFlux(String.class)
            .map(this::parseSseChunk);
    }

    private Object sanitizeJsonTransport(Object value) {
        // 处理 UTF-16 孤立代理字符
        if (value instanceof String str) {
            return replaceLoneSurrogates(str);
        }
        return value;
    }
}
```

## 钩子系统 (hooks/HookManager.java)

钩子系统允许在特定事件发生时执行自定义 shell 命令，支持项目级和全局级配置。

```java
public enum HookEvent {
    PreToolUse,       // 工具调用前（可阻塞）
    PostToolUse,      // 工具调用后
    UserPromptSubmit, // 用户提交前（可阻塞）
    Stop              // 停止时
}

public interface HookConfig {
    String getMatch();           // 正则匹配，"*" 或省略表示所有工具
    String getCommand();         // 要执行的 shell 命令
    String getDescription();     // 人类可读描述
    long getTimeoutMs();         // 超时时间
    String getCwd();             // 工作目录
}

public interface ResolvedHook extends HookConfig {
    HookEvent getEvent();
    HookScope getScope();       // project 或 global
    Path getSource();           // 来源配置文件路径
}

public enum HookOutcomeDecision {
    PASS,      // exit 0
    BLOCK,     // exit 2（仅阻塞事件）
    WARN,      // 其他非零退出码
    TIMEOUT,   // 超时被杀死
    ERROR      // 启动失败
}

public class HookOutcome {
    private ResolvedHook hook;
    private HookOutcomeDecision decision;
    private Integer exitCode;
    private String stdout;
    private String stderr;
    private long durationMs;
    private boolean truncated;
}

public class HookReport {
    private HookEvent event;
    private List<HookOutcome> outcomes;
    private boolean blocked;
}

public class HookManager {
    private boolean trustProjectHooks;
    private List<ResolvedHook> hooks;

    public HookManager(HookSettings settings) {
        this.hooks = loadHooks(settings);
        this.trustProjectHooks = settings.isTrustProjectHooks();
    }

    private List<ResolvedHook> loadHooks(HookSettings settings) {
        List<ResolvedHook> out = new ArrayList<>();
        // 项目级钩子优先加载（需要信任）
        if (settings.getProjectRoot() != null && trustProjectHooks) {
            Path projPath = projectSettingsPath(settings.getProjectRoot());
            HookSettings projectSettings = readSettingsFile(projPath);
            if (projectSettings != null) {
                appendResolved(out, projectSettings, HookScope.PROJECT, projPath);
            }
        }
        // 全局钩子
        Path globalPath = globalSettingsPath(settings.getHomeDir());
        HookSettings globalSettings = readSettingsFile(globalPath);
        if (globalSettings != null) {
            appendResolved(out, globalSettings, HookScope.GLOBAL, globalPath);
        }
        return out;
    }

    public HookReport runHooks(HookEvent event, HookContext context) {
        List<HookOutcome> outcomes = new ArrayList<>();
        boolean blocked = false;

        for (ResolvedHook hook : hooks) {
            if (hook.getEvent() != event) continue;
            // 检查 match 正则
            if (hook.getMatch() != null && !"*".equals(hook.getMatch())) {
                if (!matches(hook.getMatch(), context.getToolName())) {
                    continue;
                }
            }
            HookOutcome outcome = executeHook(hook, context);
            outcomes.add(outcome);
            if (outcome.getDecision() == HookOutcomeDecision.BLOCK) {
                blocked = true;
                // 阻塞事件：PreToolUse 和 UserPromptSubmit
                if (isBlockingEvent(event)) {
                    break;
                }
            }
        }

        return new HookReport(event, outcomes, blocked);
    }

    private HookOutcome executeHook(ResolvedHook hook, HookContext context) {
        long startTime = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder()
                .command(getShellCommand(hook.getCommand()))
                .directory(Paths.get(hook.getCwd()))
                .redirectErrorStream(false);

            // 设置环境变量
            Map<String, String> env = pb.environment();
            env.put("REASONIX_HOOK_EVENT", hook.getEvent().name());
            if (context.getToolName() != null) {
                env.put("REASONIX_TOOL_NAME", context.getToolName());
            }

            Process process = pb.start();
            boolean completed = process.waitFor(hook.getTimeoutMs(), TimeUnit.MILLISECONDS);
            
            String stdout = new String(process.getInputStream().readAllBytes()).trim();
            String stderr = new String(process.getErrorStream().readAllBytes()).trim();
            long duration = System.currentTimeMillis() - startTime;

            if (!completed) {
                process.destroyForcibly();
                return new HookOutcome(hook, HookOutcomeDecision.TIMEOUT, null, stdout, stderr, duration, false);
            }

            int exitCode = process.exitValue();
            HookOutcomeDecision decision = switch (exitCode) {
                case 0 -> HookOutcomeDecision.PASS;
                case 2 -> isBlockingEvent(hook.getEvent()) ? HookOutcomeDecision.BLOCK : HookOutcomeDecision.WARN;
                default -> HookOutcomeDecision.WARN;
            };
            return new HookOutcome(hook, decision, exitCode, stdout, stderr, duration, false);

        } catch (Exception e) {
            return new HookOutcome(hook, HookOutcomeDecision.ERROR, null, "", 
                                  e.getMessage(), System.currentTimeMillis() - startTime, false);
        }
    }

    private boolean isBlockingEvent(HookEvent event) {
        return event == HookEvent.PreToolUse || event == HookEvent.UserPromptSubmit;
    }
}
```

## 工作区管理 (workspaces/WorkspaceManager.java)

工作区管理负责追踪项目目录、会话统计和最近工作区记录。

```java
public class WorkspaceInfo {
    private Path path;
    private boolean current;
    private int sessions;
    private LocalDateTime lastActive;

    // Builder pattern
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final WorkspaceInfo info = new WorkspaceInfo();
        public Builder path(Path path) { info.path = path; return this; }
        public Builder current(boolean current) { info.current = current; return this; }
        public Builder sessions(int sessions) { info.sessions = sessions; return this; }
        public Builder lastActive(LocalDateTime lastActive) { info.lastActive = lastActive; return this; }
        public WorkspaceInfo build() { return info; }
    }
}

public class WorkspaceManager {
    private final Config config;
    private final SessionManager sessionManager;

    public WorkspaceManager(Config config, SessionManager sessionManager) {
        this.config = config;
        this.sessionManager = sessionManager;
    }

    public List<WorkspaceInfo> listKnownWorkspaces(Path currentRoot) {
        Map<String, WorkspaceStats> stats = collectSessionWorkspaceStats();
        Map<String, String> pathByKey = collectPathByKey(stats.keySet());
        
        List<WorkspaceInfo> out = new ArrayList<>();
        Map<String, WorkspaceInfo> seen = new LinkedHashMap<>();

        // 添加当前工作区
        addWorkspace(seen, currentRoot, true, stats, pathByKey);
        // 添加配置中的工作区
        addWorkspace(seen, config.getWorkspaceDir(), false, stats, pathByKey);
        // 添加最近工作区
        for (String path : config.getRecentWorkspaces()) {
            addWorkspace(seen, Paths.get(path), false, stats, pathByKey);
        }
        // 添加会话中的工作区
        for (String key : stats.keySet()) {
            String path = pathByKey.get(key);
            if (path != null) {
                addWorkspace(seen, Paths.get(path), false, stats, pathByKey);
            }
        }

        return new ArrayList<>(seen.values());
    }

    private void addWorkspace(Map<String, WorkspaceInfo> seen, Path path, boolean current,
                              Map<String, WorkspaceStats> stats, Map<String, String> pathByKey) {
        if (path == null) return;
        Path resolved = path.toAbsolutePath().normalize();
        if (!isDirectory(resolved)) return;
        
        String key = normalizeWorkspace(resolved);
        WorkspaceInfo existing = seen.get(key);
        
        if (existing != null) {
            existing.setCurrent(existing.isCurrent() || current);
            WorkspaceStats s = stats.get(key);
            if (s != null) {
                existing.setSessions(s.sessions);
                existing.setLastActive(s.lastActive);
            }
            return;
        }

        WorkspaceStats s = stats.get(key);
        WorkspaceInfo info = WorkspaceInfo.builder()
            .path(resolved)
            .current(current)
            .sessions(s != null ? s.sessions : 0)
            .lastActive(s != null ? s.lastActive : null)
            .build();
        seen.put(key, info);
    }

    private Map<String, WorkspaceStats> collectSessionWorkspaceStats() {
        Map<String, WorkspaceStats> stats = new HashMap<>();
        for (Session session : sessionManager.listSessions()) {
            String rawWorkspace = session.getMeta().getWorkspace();
            if (rawWorkspace == null || rawWorkspace.isBlank()) continue;
            
            Path path = Paths.get(rawWorkspace).toAbsolutePath().normalize();
            if (!isDirectory(path)) continue;
            
            String key = normalizeWorkspace(path);
            WorkspaceStats cur = stats.computeIfAbsent(key, k -> new WorkspaceStats());
            cur.sessions++;
            if (cur.lastActive == null || session.getMtime().isAfter(cur.lastActive)) {
                cur.lastActive = session.getMtime();
            }
        }
        return stats;
    }

    private Map<String, String> collectPathByKey(Set<String> keys) {
        Map<String, String> pathByKey = new HashMap<>();
        // 从会话中收集路径映射
        for (Session session : sessionManager.listSessions()) {
            String rawWorkspace = session.getMeta().getWorkspace();
            if (rawWorkspace == null || rawWorkspace.isBlank()) continue;
            
            Path path = Paths.get(rawWorkspace).toAbsolutePath().normalize();
            if (!isDirectory(path)) continue;
            
            String key = normalizeWorkspace(path);
            pathByKey.put(key, path.toString());
        }
        return pathByKey;
    }

    public void rememberWorkspace(Path path) {
        Path resolved = path.toAbsolutePath().normalize();
        config.pushRecentWorkspace(resolved.toString());
    }

    private boolean isDirectory(Path path) {
        try {
            return Files.isDirectory(path);
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeWorkspace(Path path) {
        // 规范化工作区路径，处理大小写等差异
        return path.toString().toLowerCase();
    }

    private static class WorkspaceStats {
        int sessions;
        LocalDateTime lastActive;
    }
}
```