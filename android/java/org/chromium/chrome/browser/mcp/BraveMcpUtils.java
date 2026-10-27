/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.mcp;

import java.security.SecureRandom;

/**
 * Utility functions for Brave MCP Server and Stealth Automation.
 */
public final class BraveMcpUtils {
    private static final String HEX_CHARS = "0123456789abcdef";

    private BraveMcpUtils() {}

    /**
     * Generates a cryptographically strong 32-byte (64 hex character) random token.
     */
    public static String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(HEX_CHARS.charAt((b >> 4) & 0x0F));
            sb.append(HEX_CHARS.charAt(b & 0x0F));
        }
        return sb.toString();
    }

    /**
     * Escapes a raw string to safely embed it within a JavaScript single- or double-quoted literal.
     */
    public static String escapeJsString(String input) {
        if (input == null) return "null";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\'':
                    sb.append("\\\'");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\u2028':
                    sb.append("\\u2028");
                    break;
                case '\u2029':
                    sb.append("\\u2029");
                    break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Generates standard Claude Desktop / Cursor configuration JSON snippet.
     */
    public static String buildClaudeConfigJson(int port, String token) {
        return "{\n"
                + "  \"mcpServers\": {\n"
                + "    \"brave-android\": {\n"
                + "      \"url\": \"http://127.0.0.1:" + port + "/mcp\",\n"
                + "      \"headers\": {\n"
                + "        \"Authorization\": \"Bearer " + token + "\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";
    }
}
