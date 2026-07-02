// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a single API key.
 */
public class ApiKeyConfig {
    final String key;
    String type = "static";          // static | timed | onetime
    String name = "";
    List<String> permissions = new ArrayList<>();
    List<String> ipWhitelist = new ArrayList<>();
    int rateLimit = 60;              // max requests per minute
    String expireAt = null;          // ISO timestamp for timed keys
    boolean used = false;            // for onetime keys

    public ApiKeyConfig(String key) {
        this.key = key;
    }

    public String getKey() { return key; }
    public String getType() { return type; }
    public String getName() { return name; }
    public List<String> getPermissions() { return permissions; }
    public List<String> getIpWhitelist() { return ipWhitelist; }
    public int getRateLimit() { return rateLimit; }
    public String getExpireAt() { return expireAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
}
