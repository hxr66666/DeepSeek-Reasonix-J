package com.reasonix.mcp;

import java.io.Closeable;

public interface McpTransport extends Closeable {

    String sendAndReceive(String json) throws Exception;

    void send(String json) throws Exception;

    @Override
    void close();
}
