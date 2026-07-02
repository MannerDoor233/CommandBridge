// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.api;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class AuthFilter extends Filter {

    private final String apiKey;

    public AuthFilter(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String requestKey = exchange.getRequestHeaders().getFirst("X-API-Key");

        if (requestKey == null || !requestKey.equals(apiKey)) {
            String response = "{\"success\":false,\"error\":\"Unauthorized\"}";
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(401, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
            return;
        }

        chain.doFilter(exchange);
    }

    @Override
    public String description() {
        return "Authenticates requests using X-API-Key header";
    }
}
