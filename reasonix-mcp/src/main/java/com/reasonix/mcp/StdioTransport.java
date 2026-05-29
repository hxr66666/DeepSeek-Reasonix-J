package com.reasonix.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class StdioTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);

    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final Thread stderrReader;

    public StdioTransport(String command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        pb.redirectErrorStream(false);
        this.process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        this.stderrReader = new Thread(() -> {
            try (BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    log.debug("MCP stderr: {}", line);
                }
            } catch (IOException ignored) {}
        }, "mcp-stderr-reader");
        this.stderrReader.setDaemon(true);
        this.stderrReader.start();
    }

    @Override
    public synchronized String sendAndReceive(String json) throws Exception {
        writer.write(json);
        writer.newLine();
        writer.flush();

        String response = reader.readLine();
        if (response == null) {
            throw new IOException("MCP server closed connection");
        }
        return response;
    }

    @Override
    public synchronized void send(String json) throws Exception {
        writer.write(json);
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() {
        try { writer.close(); } catch (IOException ignored) {}
        try { reader.close(); } catch (IOException ignored) {}
        process.destroyForcibly();
    }
}
