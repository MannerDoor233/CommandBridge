// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified API response format.
 * All endpoints return: { "success": bool, "code": int, "message": string, "data": object|null }
 */
public class ApiResponse {

    private ApiResponse() {}

    /** Build a success response. */
    public static String success(Object data, String message) {
        return build(true, 200, message, data);
    }

    /** Build a success response with default message. */
    public static String success(Object data) {
        return success(data, "操作成功");
    }

    /** Build an error response. */
    public static String error(int code, String message) {
        return build(false, code, message, null);
    }

    /** Build an error response for 400 Bad Request. */
    public static String badRequest(String message) {
        return error(400, message);
    }

    /** Build an error response for 401 Unauthorized. */
    public static String unauthorized(String message) {
        return error(401, message);
    }

    /** Build an error response for 403 Forbidden. */
    public static String forbidden(String message) {
        return error(403, message);
    }

    /** Build an error response for 404 Not Found. */
    public static String notFound(String message) {
        return error(404, message);
    }

    /** Build an error response for 405 Method Not Allowed. */
    public static String methodNotAllowed() {
        return error(405, "仅支持 POST 方法");
    }

    /** Build an error response for 429 Too Many Requests. */
    public static String rateLimited(String message) {
        return error(429, message);
    }

    /** Build an error response for 500 Internal Server Error. */
    public static String internalError(String message) {
        return error(500, "服务器内部错误: " + message);
    }

    /** Build an error response for missing required parameter. */
    public static String missingParam(String paramName) {
        return error(400, "缺少必要参数: " + paramName);
    }

    /**
     * Create a raw JSON response string.
     */
    private static String build(boolean success, int code, String message, Object data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":").append(success);
        sb.append(",\"code\":").append(code);
        sb.append(",\"message\":").append(JsonUtil.escapeJson(message));
        sb.append(",\"data\":");
        if (data == null) {
            sb.append("null");
        } else if (data instanceof String) {
            sb.append((String) data);
        } else if (data instanceof Map) {
            sb.append(JsonUtil.serialize((Map<String, Object>) data));
        } else {
            sb.append(data.toString());
        }
        sb.append("}");
        return sb.toString();
    }
}
