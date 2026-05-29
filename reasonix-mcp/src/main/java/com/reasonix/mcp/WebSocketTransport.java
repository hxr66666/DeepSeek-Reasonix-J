package com.reasonix.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class WebSocketTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTransport.class);

    private final WebSocket webSocket;
    private final HttpClient httpClient;
    private final AtomicReference<CompletableFuture<String>> responseHolder = new AtomicReference<>();

    public WebSocketTransport(String url) {
        this.httpClient = HttpClient.newHttpClient();
        this.webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        log.info("WebSocket connected to MCP server");
                        webSocket.request(1);
                    }

                    @Override
                    public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            CompletableFuture<String> future = responseHolder.get();
                            if (future != null) {
                                future.complete(buffer.toString());
                            }
                            buffer.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        log.error("WebSocket error", error);
                        CompletableFuture<String> future = responseHolder.get();
                        if (future != null) {
                            future.completeExceptionally(error);
                        }
                    }
                }).join();
    }

    @Override
    public synchronized String sendAndReceive(String json) throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        responseHolder.set(future);

        webSocket.sendText(json, true);

        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("MCP WebSocket response timeout");
        } catch (ExecutionException e) {
            throw new IOException("MCP WebSocket error", e.getCause());
        }
    }

    @Override
    public void send(String json) throws Exception {
        webSocket.sendText(json, true);
    }

    @Override
    public void close() {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing");
    }
}
