/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.mcp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight native embedded Model Context Protocol (MCP) HTTP/JSON-RPC Server.
 * Runs on loopback 127.0.0.1:8765 to enable stealth AI agent automation.
 */
public class BraveMcpServer {
    private static final String TAG = "BraveMcpServer";
    public static final int DEFAULT_PORT = 8765;

    private final int mPort;
    private final String mAuthToken;
    private ServerSocket mServerSocket;
    private Thread mAcceptThread;
    private ExecutorService mThreadPool;
    private final AtomicBoolean mIsRunning = new AtomicBoolean(false);

    public BraveMcpServer(int port, String authToken) {
        this.mPort = port > 0 ? port : DEFAULT_PORT;
        this.mAuthToken = authToken != null ? authToken.trim() : "";
    }

    public synchronized void start() {
        if (mIsRunning.get()) {
            Log.w(TAG, "Server already running on port " + mPort);
            return;
        }

        try {
            mServerSocket = new ServerSocket(mPort, 50, InetAddress.getByName("127.0.0.1"));
            mIsRunning.set(true);
            mThreadPool = Executors.newCachedThreadPool();

            mAcceptThread = new Thread(() -> {
                Log.i(TAG, "Brave MCP Server listening on http://127.0.0.1:" + mPort);
                while (mIsRunning.get() && mServerSocket != null && !mServerSocket.isClosed()) {
                    try {
                        Socket socket = mServerSocket.accept();
                        mThreadPool.execute(() -> handleClientSocket(socket));
                    } catch (Exception e) {
                        if (mIsRunning.get()) {
                            Log.e(TAG, "Exception accepting socket connection", e);
                        }
                    }
                }
            }, "BraveMcpAcceptThread");
            mAcceptThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MCP server on port " + mPort, e);
            mIsRunning.set(false);
        }
    }

    public synchronized void stop() {
        if (!mIsRunning.get()) return;
        mIsRunning.set(false);
        try {
            if (mServerSocket != null && !mServerSocket.isClosed()) {
                mServerSocket.close();
            }
        } catch (Exception ignored) {}

        if (mThreadPool != null) {
            mThreadPool.shutdownNow();
        }
        Log.i(TAG, "Brave MCP Server stopped");
    }

    public boolean isRunning() {
        return mIsRunning.get();
    }

    public int getPort() {
        return mPort;
    }

    private void handleClientSocket(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = socket.getOutputStream()) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0].toUpperCase();
            String uri = parts[1];

            int contentLength = 0;
            String authHeader = null;

            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                int colonIdx = headerLine.indexOf(':');
                if (colonIdx != -1) {
                    String headerName = headerLine.substring(0, colonIdx).trim().toLowerCase();
                    String headerValue = headerLine.substring(colonIdx + 1).trim();
                    if ("content-length".equals(headerName)) {
                        try {
                            contentLength = Integer.parseInt(headerValue);
                        } catch (NumberFormatException ignored) {}
                    } else if ("authorization".equals(headerName)) {
                        authHeader = headerValue;
                    }
                }
            }

            // Handle CORS preflight
            if ("OPTIONS".equals(method)) {
                sendHttpResponse(out, 200, "OK", "text/plain", "");
                return;
            }

            // Verify bearer token authorization if configured
            if (!mAuthToken.isEmpty()) {
                boolean authorized = false;
                if (authHeader != null) {
                    String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
                    authorized = mAuthToken.equals(token);
                }
                if (!authorized) {
                    JSONObject err = new JSONObject();
                    err.put("error", "Unauthorized: Invalid or missing Bearer token");
                    sendHttpResponse(out, 401, "Unauthorized", "application/json", err.toString());
                    return;
                }
            }

            // Handle GET /health or GET /status
            if ("GET".equals(method) && ("/health".equals(uri) || "/status".equals(uri))) {
                JSONObject status = new JSONObject();
                status.put("status", "ok");
                status.put("server", "Brave Android MCP Server");
                status.put("version", "1.0.0");
                sendHttpResponse(out, 200, "OK", "application/json", status.toString());
                return;
            }

            // Read request body
            StringBuilder body = new StringBuilder();
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = reader.read(buf, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                body.append(buf, 0, totalRead);
            }

            // Process JSON-RPC / MCP Request
            String responseJson = processJsonRpcRequest(body.toString());
            sendHttpResponse(out, 200, "OK", "application/json", responseJson);

        } catch (Exception e) {
            Log.e(TAG, "Error handling client connection", e);
        }
    }

    private String processJsonRpcRequest(String body) {
        Object reqId = JSONObject.NULL;
        try {
            JSONObject req = new JSONObject(body);
            if (req.has("id")) {
                reqId = req.get("id");
            }
            String method = req.optString("method", "");

            switch (method) {
                case "initialize":
                    return buildInitializeResponse(reqId);
                case "notifications/initialized":
                case "initialized":
                    return buildEmptySuccessResponse(reqId);
                case "tools/list":
                    return buildToolsListResponse(reqId);
                case "tools/call":
                    return handleToolCall(reqId, req.optJSONObject("params"));
                default:
                    return buildErrorResponse(reqId, -32601, "Method not found: " + method);
            }
        } catch (JSONException e) {
            return buildErrorResponse(reqId, -32700, "Parse error: " + e.getMessage());
        } catch (Exception e) {
            return buildErrorResponse(reqId, -32603, "Internal error: " + e.getMessage());
        }
    }

    private String buildInitializeResponse(Object reqId) throws JSONException {
        JSONObject res = new JSONObject();
        res.put("jsonrpc", "2.0");
        res.put("id", reqId);

        JSONObject result = new JSONObject();
        result.put("protocolVersion", "2024-11-05");

        JSONObject serverInfo = new JSONObject();
        serverInfo.put("name", "brave-android-stealth-mcp");
        serverInfo.put("version", "1.0.0");
        result.put("serverInfo", serverInfo);

        JSONObject capabilities = new JSONObject();
        JSONObject tools = new JSONObject();
        tools.put("listChanged", false);
        capabilities.put("tools", tools);
        result.put("capabilities", capabilities);

        res.put("result", result);
        return res.toString();
    }

    private String buildEmptySuccessResponse(Object reqId) throws JSONException {
        JSONObject res = new JSONObject();
        res.put("jsonrpc", "2.0");
        res.put("id", reqId);
        res.put("result", new JSONObject());
        return res.toString();
    }

    private String buildToolsListResponse(Object reqId) throws JSONException {
        JSONObject res = new JSONObject();
        res.put("jsonrpc", "2.0");
        res.put("id", reqId);

        JSONObject result = new JSONObject();
        JSONArray tools = new JSONArray();

        // 1. browser_navigate
        tools.put(createToolMeta(
                "browser_navigate",
                "Navigate the active tab or create a new tab with specified URL",
                new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("url", new JSONObject().put("type", "string").put("description", "URL to navigate to"))
                                .put("new_tab", new JSONObject().put("type", "boolean").put("description", "Whether to open in new tab (default: false)"))
                        )
                        .put("required", new JSONArray().put("url"))
        ));

        // 2. browser_get_dom
        tools.put(createToolMeta(
                "browser_get_dom",
                "Extract readable content from the active tab in markdown, plain text, or simplified DOM mode",
                new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("mode", new JSONObject().put("type", "string").put("enum", new JSONArray().put("markdown").put("text").put("dom")).put("description", "Extraction mode (default: markdown)"))
                                .put("selector", new JSONObject().put("type", "string").put("description", "Optional CSS selector to extract only specific subtree"))
                        )
        ));

        // 3. browser_click
        tools.put(createToolMeta(
                "browser_click",
                "Stealthily click an element using CSS selector or viewport coordinates (x, y)",
                new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("selector", new JSONObject().put("type", "string").put("description", "CSS selector of element to click"))
                                .put("x", new JSONObject().put("type", "number").put("description", "Viewport X coordinate"))
                                .put("y", new JSONObject().put("type", "number").put("description", "Viewport Y coordinate"))
                        )
        ));

        // 4. browser_type
        tools.put(createToolMeta(
                "browser_type",
                "Type text into a selector or currently active element with synthetic input/change events",
                new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("selector", new JSONObject().put("type", "string").put("description", "Optional CSS selector of target input"))
                                .put("text", new JSONObject().put("type", "string").put("description", "Text to type"))
                        )
                        .put("required", new JSONArray().put("text"))
        ));

        // 5. browser_screenshot
        tools.put(createToolMeta(
                "browser_screenshot",
                "Capture high-performance base64 JPEG screenshot of active page without CDP screencast overhead",
                new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("quality", new JSONObject().put("type", "number").put("description", "JPEG quality 10-100 (default: 80)"))
                        )
        ));

        // 6. browser_tab_management
        tools.put(createToolMeta(
                "browser_tab_management",
                "Manage browser tabs: list, switch, create new, or close tabs",
                new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("action", new JSONObject().put("type", "string").put("enum", new JSONArray().put("list").put("switch").put("new").put("close")).put("description", "Action to perform"))
                                .put("tab_id", new JSONObject().put("type", "number").put("description", "Tab ID for switch/close actions"))
                                .put("url", new JSONObject().put("type", "string").put("description", "URL for new tab action"))
                        )
                        .put("required", new JSONArray().put("action"))
        ));

        // 7. browser_evaluate_script
        tools.put(createToolMeta(
                "browser_evaluate_script",
                "Execute arbitrary JavaScript expression in page context",
                new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("script", new JSONObject().put("type", "string").put("description", "JavaScript expression to evaluate"))
                        )
                        .put("required", new JSONArray().put("script"))
        ));

        result.put("tools", tools);
        res.put("result", result);
        return res.toString();
    }

    private JSONObject createToolMeta(String name, String desc, JSONObject schema) throws JSONException {
        JSONObject tool = new JSONObject();
        tool.put("name", name);
        tool.put("description", desc);
        tool.put("inputSchema", schema);
        return tool;
    }

    private String handleToolCall(Object reqId, JSONObject params) throws JSONException {
        if (params == null) {
            return buildErrorResponse(reqId, -32602, "Missing params in tools/call");
        }
        String toolName = params.optString("name", "");
        JSONObject arguments = params.optJSONObject("arguments");
        if (arguments == null) {
            arguments = new JSONObject();
        }

        try {
            String output = BraveMcpToolHandler.executeTool(toolName, arguments);
            JSONObject res = new JSONObject();
            res.put("jsonrpc", "2.0");
            res.put("id", reqId);

            JSONObject result = new JSONObject();
            JSONArray content = new JSONArray();
            JSONObject textObj = new JSONObject();
            textObj.put("type", "text");
            textObj.put("text", output != null ? output : "");
            content.put(textObj);

            result.put("content", content);
            result.put("isError", false);
            res.put("result", result);
            return res.toString();
        } catch (Exception e) {
            JSONObject res = new JSONObject();
            res.put("jsonrpc", "2.0");
            res.put("id", reqId);

            JSONObject result = new JSONObject();
            JSONArray content = new JSONArray();
            JSONObject textObj = new JSONObject();
            textObj.put("type", "text");
            textObj.put("text", "Error: " + e.getMessage());
            content.put(textObj);

            result.put("content", content);
            result.put("isError", true);
            res.put("result", result);
            return res.toString();
        }
    }

    private String buildErrorResponse(Object reqId, int code, String message) {
        try {
            JSONObject res = new JSONObject();
            res.put("jsonrpc", "2.0");
            res.put("id", reqId);
            JSONObject err = new JSONObject();
            err.put("code", code);
            err.put("message", message);
            res.put("error", err);
            return res.toString();
        } catch (JSONException e) {
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    private void sendHttpResponse(OutputStream out, int statusCode, String statusText, String contentType, String body) {
        try {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
                    + "Content-Type: " + contentType + "; charset=utf-8\r\n"
                    + "Content-Length: " + bodyBytes.length + "\r\n"
                    + "Connection: close\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
                    + "Access-Control-Allow-Headers: Content-Type, Authorization\r\n"
                    + "\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            if (bodyBytes.length > 0) {
                out.write(bodyBytes);
            }
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "Failed to send HTTP response", e);
        }
    }
}
