package com.reasonix.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private Config systemConfig;
    private Connection duckdb;
    private final String dbPath;

    public ConfigManager() {
        this(Path.of(System.getProperty("user.home"), ".reasonix", "reasonix.db").toString());
    }

    public ConfigManager(String dbPath) {
        this.dbPath = dbPath;
        this.systemConfig = loadSystemConfig();
        initDuckDB();
    }

    private Config loadSystemConfig() {
        Path configPath = Path.of(System.getProperty("user.home"), ".reasonix", "config.yaml");
        if (Files.exists(configPath)) {
            return new Config(configPath);
        }
        return new Config();
    }

    private void initDuckDB() {
        try {
            String jdbcUrl = "jdbc:duckdb:" + dbPath;
            duckdb = DriverManager.getConnection(jdbcUrl);
            duckdb.setAutoCommit(false);
            initializeSchema();
        } catch (SQLException e) {
            log.error("Failed to initialize DuckDB: {}", e.getMessage());
            throw new RuntimeException("DuckDB initialization failed", e);
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = duckdb.createStatement()) {
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
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

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

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memories_type ON memories(type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memories_workspace ON memories(workspace)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_workspace ON sessions(workspace_root)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mcp_workspace ON mcp_servers(workspace)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_hooks_event ON hooks(event)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_hooks_workspace ON hooks(workspace)");

            duckdb.commit();
        }
    }

    public Config getSystemConfig() {
        return systemConfig;
    }

    public Connection getDuckDBConnection() {
        return duckdb;
    }

    public void close() {
        try {
            if (duckdb != null && !duckdb.isClosed()) {
                duckdb.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to close DuckDB connection: {}", e.getMessage());
        }
    }
}
