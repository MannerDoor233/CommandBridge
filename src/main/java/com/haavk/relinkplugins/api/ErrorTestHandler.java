// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.haavk.relinkplugins.util.ApiResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Error testing endpoint — returns various error responses for client testing.
 * Only available in development mode.
 */
public class ErrorTestHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String query = exchange.getRequestURI().getQuery();

        // Parse ?type= parameter
        String type = "400";
        if (query != null) {
            Matcher m = Pattern.compile("type=(\\d+)").matcher(query);
            if (m.find()) type = m.group(1);
        }

        String json;
        switch (type) {
            case "400":
                json = ApiResponse.badRequest("参数 time 格式错误，预期格式: HH:mm");
                break;
            case "401":
                json = ApiResponse.unauthorized("X-API-Key 无效或已过期");
                break;
            case "403":
                json = ApiResponse.forbidden("当前 API Key 无权访问 /broadcast");
                break;
            case "404":
                json = ApiResponse.notFound("玩家 Notch 不在线");
                break;
            case "405":
                json = ApiResponse.methodNotAllowed();
                break;
            case "429":
                json = ApiResponse.rateLimited("请求过于频繁，请 5 秒后重试");
                break;
            case "500":
                json = ApiResponse.internalError("NullPointerException");
                break;
            default:
                json = ApiResponse.badRequest("未知错误类型: " + type);
        }

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        int code = extractCode(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private int extractCode(String json) {
        Matcher m = Pattern.compile("\"code\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 200;
    }
}
