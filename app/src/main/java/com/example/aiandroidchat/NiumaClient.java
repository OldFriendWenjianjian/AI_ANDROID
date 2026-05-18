package com.example.aiandroidchat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class NiumaClient {
    private static final String BASE_URL = "https://www.niumacode.cc";
    private static final String API_BASE = BASE_URL + "/api/v1";
    private static final int TIMEOUT_MS = 60_000;
    private static final String FALLBACK_MODEL = "gpt-5.4-mini";

    JSONObject publicSettings() throws IOException, JSONException {
        return request("GET", API_BASE + "/settings/public", null, null).optJSONObject("data");
    }

    JSONObject login(String email, String password) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        return request("POST", API_BASE + "/auth/login", body, null).optJSONObject("data");
    }

    JSONObject register(String email, String password, String verifyCode) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        if (verifyCode != null && !verifyCode.trim().isEmpty()) {
            body.put("verify_code", verifyCode.trim());
        }
        return request("POST", API_BASE + "/auth/register", body, null).optJSONObject("data");
    }

    int sendVerifyCode(String email) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("email", email);
        JSONObject data = request("POST", API_BASE + "/auth/send-verify-code", body, null).optJSONObject("data");
        return data == null ? 60 : data.optInt("countdown", 60);
    }

    JSONObject profile(String accessToken) throws IOException, JSONException {
        return request("GET", API_BASE + "/auth/me", null, accessToken).optJSONObject("data");
    }

    List<String> models(String apiKey) throws IOException, JSONException {
        HttpURLConnection connection = open("GET", BASE_URL + "/v1/models", null);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);

        int code = connection.getResponseCode();
        String response = readAll(code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IOException(parseError(code, response));
        }

        java.util.ArrayList<String> models = new java.util.ArrayList<>();
        JSONObject root = new JSONObject(response);
        JSONArray data = root.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "");
                if (!id.isEmpty()) {
                    models.add(id);
                }
            }
        }
        return models;
    }

    void ping(String apiKey) throws IOException, JSONException {
        HttpURLConnection connection = open("GET", BASE_URL + "/v1/models", null);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);

        int code = connection.getResponseCode();
        String response = readAll(code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IOException(parseError(code, response));
        }
    }

    String ensureMobileApiKey(String accessToken) throws IOException, JSONException {
        int groupId = firstAvailableGroupId(accessToken);
        if (groupId <= 0) {
            throw new IOException("当前账号没有可用分组，无法创建可用 API Key。请联系管理员给账号分配分组。");
        }

        JSONObject list = request("GET", API_BASE + "/keys?page=1&page_size=100", null, accessToken).optJSONObject("data");
        JSONArray items = extractItems(list);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String name = item.optString("name", "");
            String key = firstNonEmpty(item, "key", "api_key", "token");
            int existingGroupId = item.optInt("group_id", 0);
            if ("AI Android Chat".equals(name) && key.startsWith("sk-") && existingGroupId > 0) {
                return key;
            }
        }

        JSONObject body = new JSONObject();
        body.put("name", "AI Android Chat");
        body.put("group_id", groupId);
        JSONObject created = request("POST", API_BASE + "/keys", body, accessToken).optJSONObject("data");
        String key = firstNonEmpty(created, "key", "api_key", "token");
        if (key.isEmpty() && created != null) {
            key = firstNonEmpty(created.optJSONObject("api_key"), "key", "api_key", "token");
        }
        if (key.isEmpty()) {
            throw new IOException("已登录，但未能创建或读取 API Key。请到网站控制台创建 API Key。");
        }
        return key;
    }

    private int firstAvailableGroupId(String accessToken) throws IOException, JSONException {
        JSONObject root = request("GET", API_BASE + "/groups/available", null, accessToken);
        Object data = root.opt("data");
        JSONArray groups;
        if (data instanceof JSONArray) {
            groups = (JSONArray) data;
        } else if (data instanceof JSONObject) {
            groups = extractItems((JSONObject) data);
        } else {
            groups = new JSONArray();
        }

        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) {
                continue;
            }
            int id = group.optInt("id", group.optInt("group_id", 0));
            boolean enabled = group.optBoolean("enabled", true);
            String status = group.optString("status", "enabled");
            if (id > 0 && enabled && !"disabled".equalsIgnoreCase(status)) {
                return id;
            }
        }
        return 0;
    }

    String chat(String apiKey, String model, List<ChatMessage> history) throws IOException, JSONException {
        try {
            return chatOnce(apiKey, model, history);
        } catch (IOException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (!FALLBACK_MODEL.equals(model) && (message.contains("503") || message.contains("Service temporarily unavailable"))) {
                return chatOnce(apiKey, FALLBACK_MODEL, history);
            }
            throw e;
        }
    }

    String visionChat(String apiKey, String model, List<ChatMessage> history, byte[] jpegBytes, String prompt)
            throws IOException, JSONException {
        try {
            return visionChatOnce(apiKey, model, history, jpegBytes, prompt);
        } catch (IOException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (!FALLBACK_MODEL.equals(model) && (message.contains("503") || message.contains("Service temporarily unavailable"))) {
                return visionChatOnce(apiKey, FALLBACK_MODEL, history, jpegBytes, prompt);
            }
            throw e;
        }
    }

    private String chatOnce(String apiKey, String model, List<ChatMessage> history) throws IOException, JSONException {
        HttpURLConnection connection = open("POST", BASE_URL + "/v1/chat/completions", null);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);

        JSONObject body = new JSONObject();
        body.put("model", model == null || model.trim().isEmpty() ? FALLBACK_MODEL : model.trim());
        body.put("messages", buildMessages(history));
        body.put("stream", false);
        writeBody(connection, body);

        int code = connection.getResponseCode();
        String response = readAll(code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IOException(parseError(code, response));
        }
        JSONObject root = new JSONObject(response);
        JSONArray choices = root.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject message = choices.optJSONObject(0).optJSONObject("message");
            if (message != null) {
                return message.optString("content", response);
            }
        }
        return response;
    }

    private String visionChatOnce(String apiKey, String model, List<ChatMessage> history, byte[] jpegBytes, String prompt)
            throws IOException, JSONException {
        HttpURLConnection connection = open("POST", BASE_URL + "/v1/chat/completions", null);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);

        JSONObject body = new JSONObject();
        body.put("model", model == null || model.trim().isEmpty() ? FALLBACK_MODEL : model.trim());
        body.put("messages", buildVisionMessages(history, jpegBytes, prompt));
        body.put("stream", false);
        writeBody(connection, body);

        int code = connection.getResponseCode();
        String response = readAll(code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IOException(parseError(code, response));
        }
        JSONObject root = new JSONObject(response);
        JSONArray choices = root.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject message = choices.optJSONObject(0).optJSONObject("message");
            if (message != null) {
                return message.optString("content", response);
            }
        }
        return response;
    }

    private JSONArray buildMessages(List<ChatMessage> history) throws JSONException {
        JSONArray messages = new JSONArray();
        JSONObject system = new JSONObject();
        system.put("role", ChatMessage.ROLE_SYSTEM);
        system.put("content", "你是一个简洁、可靠的中文 AI 助手。");
        messages.put(system);

        int start = Math.max(0, history.size() - 20);
        for (int i = start; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            JSONObject item = new JSONObject();
            item.put("role", message.role);
            item.put("content", message.content);
            messages.put(item);
        }
        return messages;
    }

    private JSONArray buildVisionMessages(List<ChatMessage> history, byte[] jpegBytes, String prompt) throws JSONException {
        JSONArray messages = buildMessages(history);
        JSONObject user = new JSONObject();
        user.put("role", ChatMessage.ROLE_USER);

        JSONArray content = new JSONArray();
        JSONObject text = new JSONObject();
        text.put("type", "text");
        text.put("text", prompt == null || prompt.trim().isEmpty()
                ? "请识别并描述这张图片，尽量用中文给出关键内容。"
                : prompt.trim());
        content.put(text);

        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/jpeg;base64," + android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP));
        JSONObject image = new JSONObject();
        image.put("type", "image_url");
        image.put("image_url", imageUrl);
        content.put(image);

        user.put("content", content);
        messages.put(user);
        return messages;
    }

    private JSONObject request(String method, String url, JSONObject body, String accessToken) throws IOException, JSONException {
        HttpURLConnection connection = open(method, url, accessToken);
        if (body != null) {
            writeBody(connection, body);
        }

        int code = connection.getResponseCode();
        String response = readAll(code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new IOException(parseError(code, response));
        }
        JSONObject root = new JSONObject(response);
        if (root.optInt("code", 0) != 0) {
            throw new IOException(root.optString("message", response));
        }
        return root;
    }

    private HttpURLConnection open(String method, String url, String accessToken) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (accessToken != null && !accessToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        }
        if (!"GET".equals(method)) {
            connection.setDoOutput(true);
        }
        return connection;
    }

    private void writeBody(HttpURLConnection connection, JSONObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
    }

    private JSONArray extractItems(JSONObject data) {
        if (data == null) {
            return new JSONArray();
        }
        JSONArray items = data.optJSONArray("items");
        if (items != null) {
            return items;
        }
        items = data.optJSONArray("data");
        if (items != null) {
            return items;
        }
        items = data.optJSONArray("records");
        return items == null ? new JSONArray() : items;
    }

    private String firstNonEmpty(JSONObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String parseError(int code, String response) {
        try {
            JSONObject root = new JSONObject(response);
            String message = root.optString("message", "");
            if (message.isEmpty()) {
                JSONObject error = root.optJSONObject("error");
                message = error == null ? response : error.optString("message", response);
            }
            return "请求失败 " + code + ": " + message;
        } catch (JSONException ignored) {
            return "请求失败 " + code + ": " + response;
        }
    }

    private String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
