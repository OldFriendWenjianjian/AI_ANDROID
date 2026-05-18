package com.example.aiandroidchat;

import org.json.JSONException;
import org.json.JSONObject;

final class ChatMessage {
    static final String ROLE_USER = "user";
    static final String ROLE_ASSISTANT = "assistant";
    static final String ROLE_SYSTEM = "system";

    final String role;
    final String content;
    final long createdAt;

    ChatMessage(String role, String content) {
        this(role, content, System.currentTimeMillis());
    }

    ChatMessage(String role, String content, long createdAt) {
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    boolean isUser() {
        return ROLE_USER.equals(role);
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("role", role);
        object.put("content", content);
        object.put("createdAt", createdAt);
        return object;
    }

    static ChatMessage fromJson(JSONObject object) {
        return new ChatMessage(
                object.optString("role", ROLE_ASSISTANT),
                object.optString("content", ""),
                object.optLong("createdAt", System.currentTimeMillis()));
    }
}
