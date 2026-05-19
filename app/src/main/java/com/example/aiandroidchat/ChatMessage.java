package com.example.aiandroidchat;

import org.json.JSONException;
import org.json.JSONObject;

final class ChatMessage {
    static final String ROLE_USER = "user";
    static final String ROLE_ASSISTANT = "assistant";
    static final String ROLE_SYSTEM = "system";
    static final String IMAGE_MODE_PHOTO = "photo";
    static final String IMAGE_MODE_SHEET_MUSIC = "sheet_music";
    static final String IMAGE_MODE_GENERATED = "generated_image";

    final String role;
    final String content;
    final long createdAt;
    final String imageThumbnailBase64;
    final String imageMode;

    ChatMessage(String role, String content) {
        this(role, content, System.currentTimeMillis(), "");
    }

    ChatMessage(String role, String content, long createdAt) {
        this(role, content, createdAt, "");
    }

    ChatMessage(String role, String content, long createdAt, String imageThumbnailBase64) {
        this(role, content, createdAt, imageThumbnailBase64, IMAGE_MODE_PHOTO);
    }

    ChatMessage(String role, String content, long createdAt, String imageThumbnailBase64, String imageMode) {
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.imageThumbnailBase64 = imageThumbnailBase64 == null ? "" : imageThumbnailBase64;
        this.imageMode = imageMode == null || imageMode.trim().isEmpty() ? IMAGE_MODE_PHOTO : imageMode.trim();
    }

    boolean isUser() {
        return ROLE_USER.equals(role);
    }

    boolean hasImage() {
        return !imageThumbnailBase64.isEmpty();
    }

    static ChatMessage photo(String prompt, String thumbnailBase64, String imageMode) {
        return new ChatMessage(ROLE_USER, prompt, System.currentTimeMillis(), thumbnailBase64, imageMode);
    }

    static ChatMessage generatedImage(String caption, String thumbnailBase64) {
        return new ChatMessage(ROLE_ASSISTANT, caption, System.currentTimeMillis(),
                thumbnailBase64, IMAGE_MODE_GENERATED);
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("role", role);
        object.put("content", content);
        object.put("createdAt", createdAt);
        if (hasImage()) {
            object.put("imageThumbnailBase64", imageThumbnailBase64);
            object.put("imageMode", imageMode);
        }
        return object;
    }

    static ChatMessage fromJson(JSONObject object) {
        return new ChatMessage(
                object.optString("role", ROLE_ASSISTANT),
                object.optString("content", ""),
                object.optLong("createdAt", System.currentTimeMillis()),
                object.optString("imageThumbnailBase64", ""),
                object.optString("imageMode", IMAGE_MODE_PHOTO));
    }
}
