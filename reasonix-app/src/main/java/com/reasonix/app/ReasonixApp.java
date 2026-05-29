package com.reasonix.app;

import com.reasonix.core.adapters.DuckDBMemoryStore;
import com.reasonix.core.adapters.DuckDBSessionStore;
import com.reasonix.core.adapters.LangChain4jModelClient;
import com.reasonix.core.adapters.jsonl.JsonlEventSink;
import com.reasonix.core.context.ContextManager;
import com.reasonix.core.loop.CacheFirstLoop;
import com.reasonix.core.memory.AppendOnlyLog;
import com.reasonix.core.memory.ImmutablePrefix;
import com.reasonix.core.memory.VolatileScratch;
import com.reasonix.core.ports.EventSink;
import com.reasonix.core.ports.MemoryStore;
import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.SessionStore;
import com.reasonix.core.tools.ToolContext;
import com.reasonix.core.tools.ToolRegistry;
import com.reasonix.core.tools.builtin.FileReadTool;
import com.reasonix.core.tools.builtin.FileWriteTool;
import com.reasonix.core.tools.builtin.ListDirectoryTool;
import com.reasonix.core.tools.builtin.SearchContentTool;
import com.reasonix.core.tools.shell.ShellTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class ReasonixApp {

    private static final Logger log = LoggerFactory.getLogger(ReasonixApp.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Path workspace;
    private final Path dbPath;

    private Connection connection;
    private ModelClient modelClient;
    private MemoryStore memoryStore;
    private SessionStore sessionStore;
    private EventSink eventSink;
    private ToolRegistry toolRegistry;
    private ContextManager contextManager;

    public ReasonixApp(String apiKey, String baseUrl, String model, Path workspace, Path dbPath) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.workspace = workspace;
        this.dbPath = dbPath;
    }

    public void initialize() throws SQLException {
        this.connection = initDuckDB(dbPath);
        this.modelClient = initModelClient(apiKey, baseUrl, model);
        this.memoryStore = new DuckDBMemoryStore(connection);
        this.sessionStore = new DuckDBSessionStore(connection);
        this.eventSink = new JsonlEventSink(workspace.resolve("logs/reasonix.jsonl"));
        this.toolRegistry = initToolRegistry(workspace);
        this.contextManager = new ContextManager();

        log.info("ReasonixApp initialized: model={}, workspace={}", model, workspace);
    }

    public CacheFirstLoop createLoop(String systemPrompt) {
        ImmutablePrefix prefix = new ImmutablePrefix(systemPrompt, toolRegistry.getToolSpecs(), List.of());
        AppendOnlyLog logEntries = new AppendOnlyLog();
        VolatileScratch scratch = new VolatileScratch();

        return new CacheFirstLoop(prefix, logEntries, scratch, toolRegistry, modelClient,
                contextManager, eventSink, model, 50);
    }

    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to close database connection", e);
        }
    }

    private Connection initDuckDB(Path dbPath) throws SQLException {
        String url = "jdbc:duckdb:" + dbPath.resolve("reasonix");
        Connection conn = DriverManager.getConnection(url);
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memories (
                    id VARCHAR PRIMARY KEY, type VARCHAR NOT NULL, content TEXT NOT NULL,
                    metadata TEXT, workspace VARCHAR DEFAULT '', is_pinned BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id VARCHAR PRIMARY KEY, workspace_root VARCHAR NOT NULL, model VARCHAR DEFAULT 'deepseek-v4-flash',
                    cost_usd DOUBLE DEFAULT 0.0, cache_hit_ratio DOUBLE DEFAULT 0.0, turn_count INTEGER DEFAULT 0,
                    is_active BOOLEAN DEFAULT TRUE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id VARCHAR PRIMARY KEY, session_id VARCHAR NOT NULL, role VARCHAR NOT NULL,
                    content TEXT, reasoning TEXT, tool_calls TEXT, usage TEXT, model VARCHAR,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            conn.commit();
        }
        return conn;
    }

    private ModelClient initModelClient(String apiKey, String baseUrl, String model) {
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey).baseUrl(baseUrl).modelName(model).build();
        StreamingChatLanguageModel streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey).baseUrl(baseUrl).modelName(model).build();
        return new LangChain4jModelClient(chatModel, streamingModel);
    }

    private ToolRegistry initToolRegistry(Path workspace) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(ShellTool.toToolDefinition());
        registry.register(FileReadTool.toToolDefinition());
        registry.register(FileWriteTool.toToolDefinition());
        registry.register(ListDirectoryTool.toToolDefinition());
        registry.register(SearchContentTool.toToolDefinition());
        registry.setDefaultContext(new ToolContext(workspace, Map.of(), "default"));
        return registry;
    }

    public ModelClient getModelClient() { return modelClient; }
    public MemoryStore getMemoryStore() { return memoryStore; }
    public SessionStore getSessionStore() { return sessionStore; }
    public EventSink getEventSink() { return eventSink; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    public ContextManager getContextManager() { return contextManager; }
}
