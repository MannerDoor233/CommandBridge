// SPDX-License-Identifier: MIT

package com.haavk.commandbridge.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON parser and serializer with zero external dependencies.
 */
public class JsonUtil {

    /**
     * Parse a JSON object string into a Map. Supports strings, numbers, booleans, and null.
     */
    public static Map<String, Object> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty JSON");
        }

        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("Not a JSON object");
        }

        // Remove outer braces
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        Map<String, Object> result = new LinkedHashMap<>();

        if (inner.isEmpty()) {
            return result;
        }

        // Simple key-value parsing (handles basic cases without external libs)
        int i = 0;
        while (i < inner.length()) {
            // Skip whitespace and commas
            while (i < inner.length() && (inner.charAt(i) == ' ' || inner.charAt(i) == ',' || inner.charAt(i) == '\t' || inner.charAt(i) == '\n' || inner.charAt(i) == '\r')) {
                i++;
            }
            if (i >= inner.length()) break;

            // Parse key (quoted string)
            if (inner.charAt(i) != '"') {
                throw new IllegalArgumentException("Expected quoted key at position " + i);
            }
            i++; // skip opening quote
            StringBuilder keyBuilder = new StringBuilder();
            while (i < inner.length() && inner.charAt(i) != '"') {
                if (inner.charAt(i) == '\\' && i + 1 < inner.length()) {
                    i++;
                    switch (inner.charAt(i)) {
                        case '"': keyBuilder.append('"'); break;
                        case '\\': keyBuilder.append('\\'); break;
                        case '/': keyBuilder.append('/'); break;
                        case 'n': keyBuilder.append('\n'); break;
                        case 'r': keyBuilder.append('\r'); break;
                        case 't': keyBuilder.append('\t'); break;
                        default: keyBuilder.append(inner.charAt(i));
                    }
                } else {
                    keyBuilder.append(inner.charAt(i));
                }
                i++;
            }
            i++; // skip closing quote
            String key = keyBuilder.toString();

            // Skip colon
            while (i < inner.length() && (inner.charAt(i) == ' ' || inner.charAt(i) == ':' || inner.charAt(i) == '\t')) {
                i++;
            }

            // Parse value
            if (i >= inner.length()) break;

            char c = inner.charAt(i);
            Object value;

            if (c == '"') {
                // String value
                i++; // skip opening quote
                StringBuilder valBuilder = new StringBuilder();
                while (i < inner.length() && inner.charAt(i) != '"') {
                    if (inner.charAt(i) == '\\' && i + 1 < inner.length()) {
                        i++;
                        switch (inner.charAt(i)) {
                            case '"': valBuilder.append('"'); break;
                            case '\\': valBuilder.append('\\'); break;
                            case '/': valBuilder.append('/'); break;
                            case 'n': valBuilder.append('\n'); break;
                            case 'r': valBuilder.append('\r'); break;
                            case 't': valBuilder.append('\t'); break;
                            default: valBuilder.append(inner.charAt(i));
                        }
                    } else {
                        valBuilder.append(inner.charAt(i));
                    }
                    i++;
                }
                i++; // skip closing quote
                value = valBuilder.toString();
            } else if (c == 't' || c == 'f') {
                // Boolean
                if (inner.startsWith("true", i)) {
                    value = true;
                    i += 4;
                } else if (inner.startsWith("false", i)) {
                    value = false;
                    i += 5;
                } else {
                    throw new IllegalArgumentException("Invalid boolean at position " + i);
                }
            } else if (c == 'n') {
                // null
                if (inner.startsWith("null", i)) {
                    value = null;
                    i += 4;
                } else {
                    throw new IllegalArgumentException("Invalid value at position " + i);
                }
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                // Number
                int start = i;
                if (c == '-') i++;
                while (i < inner.length() && inner.charAt(i) >= '0' && inner.charAt(i) <= '9') i++;
                boolean isDouble = false;
                if (i < inner.length() && inner.charAt(i) == '.') {
                    isDouble = true;
                    i++;
                    while (i < inner.length() && inner.charAt(i) >= '0' && inner.charAt(i) <= '9') i++;
                }
                String numStr = inner.substring(start, i);
                if (isDouble) {
                    value = Double.parseDouble(numStr);
                } else {
                    long longVal = Long.parseLong(numStr);
                    if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE) {
                        value = (int) longVal;
                    } else {
                        value = longVal;
                    }
                }
            } else {
                throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + i);
            }

            result.put(key, value);

            // Skip to next key or end
            while (i < inner.length() && inner.charAt(i) != ',' && inner.charAt(i) != '}') i++;
        }

        return result;
    }

    /**
     * Escape a string for use in JSON output.
     */
    public static String escapeJson(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Serialize an object to JSON string. Only handles Strings, Numbers, Booleans, null.
     */
    public static String serialize(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(escapeJson(entry.getKey()));
            sb.append(':');
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof String) {
                sb.append(escapeJson((String) val));
            } else if (val instanceof Boolean || val instanceof Number) {
                sb.append(val);
            } else {
                sb.append(escapeJson(val.toString()));
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
