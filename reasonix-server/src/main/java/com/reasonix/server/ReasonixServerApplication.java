package com.reasonix.server;

import com.reasonix.common.util.PathUtils;
import com.reasonix.core.adapters.DuckDBMemoryStore;
import com.reasonix.core.adapters.DuckDBSessionStore;
import com.reasonix.core.adapters.LangChain4jModelClient;
import com.reasonix.core.adapters.jsonl.JsonlEventSink;
import com.reasonix.core.context.ContextManager;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Map;

@SpringBootApplication
public class ReasonixServerApplication {

    private static final Logger log = LoggerFactory.getLogger(ReasonixServerApplication.class);

    public static void main(String[] args) {
        Path globalConfigPath = PathUtils.getGlobalConfigPath();
        SpringApplication app = new SpringApplication(ReasonixServerApplication.class);
        app.setDefaultProperties(Collections.singletonMap(
                "spring.config.location",
                "file:"+globalConfigPath.toAbsolutePath()
        ));
        app.run(args);
    }

    @Bean
    public Connection duckDBConnection(@Value("${reasonix.db.path:reasonix}") String dbPath) throws SQLException {
        String url = "jdbc:duckdb:" + dbPath;
        Connection conn = DriverManager.getConnection(url);
        conn.setAutoCommit(false);
        initializeSchema(conn);
        return conn;
    }

    private void initializeSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memories (
                    id VARCHAR PRIMARY KEY,
                    type VARCHAR NOT NULL,
                    content TEXT NOT NULL,
                    metadata TEXT,
                    workspace VARCHAR DEFAULT '',
                    is_pinned BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id VARCHAR PRIMARY KEY,
                    workspace_root VARCHAR NOT NULL,
                    model VARCHAR DEFAULT 'deepseek-v4-flash',
                    cost_usd DOUBLE DEFAULT 0.0,
                    cache_hit_ratio DOUBLE DEFAULT 0.0,
                    turn_count INTEGER DEFAULT 0,
                    is_active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id VARCHAR PRIMARY KEY,
                    session_id VARCHAR NOT NULL,
                    role VARCHAR NOT NULL,
                    content TEXT,
                    reasoning TEXT,
                    tool_calls TEXT,
                    usage TEXT,
                    model VARCHAR,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            conn.commit();
        }
    }

    @Bean
    public ModelClient modelClient(
            @Value("${reasonix.api.key:}") String apiKey,
            @Value("${reasonix.api.base-url:https://api.deepseek.com}") String baseUrl) {

        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("deepseek-chat")
                .build();

        StreamingChatLanguageModel streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("deepseek-chat")
                .build();

        return new LangChain4jModelClient(chatModel, streamingModel);
    }

    @Bean
    public MemoryStore memoryStore(Connection conn) {
        return new DuckDBMemoryStore(conn);
    }

    @Bean
    public SessionStore sessionStore(Connection conn) {
        return new DuckDBSessionStore(conn);
    }

    @Bean
    public EventSink eventSink(@Value("${reasonix.log.path:logs/reasonix.jsonl}") String logPath) {
        return new JsonlEventSink(Path.of(logPath));
    }

    @Bean
    public ToolRegistry toolRegistry(@Value("${reasonix.workspace:}") String workspace) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(ShellTool.toToolDefinition());
        registry.register(FileReadTool.toToolDefinition());
        registry.register(FileWriteTool.toToolDefinition());
        registry.register(ListDirectoryTool.toToolDefinition());
        registry.register(SearchContentTool.toToolDefinition());

        Path ws = workspace.isBlank() ? Path.of(".").toAbsolutePath() : Path.of(workspace);
        registry.setDefaultContext(new ToolContext(ws, Map.of(), "default"));

        return registry;
    }

    @Bean
    public ContextManager contextManager() {
        return new ContextManager();
    }
}
