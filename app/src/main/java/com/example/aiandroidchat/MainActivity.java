package com.example.aiandroidchat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String BRAND_NAME = "Smart Human";
    private static final String PREFS = "ai_chat_prefs";
    private static final String KEY_LEGACY_HISTORY = "history";
    private static final String KEY_CONVERSATIONS = "conversations";
    private static final String KEY_CURRENT_CONVERSATION_ID = "current_conversation_id";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_GATEWAY_API_KEY = "gateway_api_key";
    private static final String KEY_GATEWAY_API_KEY_VERSION = "gateway_api_key_version";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_MODEL = "model";
    private static final int GATEWAY_API_KEY_VERSION = 2;
    private static final String DEFAULT_MODEL = "gpt-5.4-mini";
    private static final String[] DEFAULT_MODELS = {
            "gpt-5.4-mini",
            "gpt-5.4",
            "gpt-5.5",
            "gpt-5.3-codex",
            "gpt-5.3-codex-spark"
    };

    private final List<Conversation> conversations = new ArrayList<>();
    private final List<ChatMessage> messages = new ArrayList<>();
    private final NiumaClient client = new NiumaClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SharedPreferences prefs;
    private Conversation currentConversation;
    private LinearLayout root;
    private LinearLayout messagesLayout;
    private ScrollView scrollView;
    private EditText input;
    private Button sendButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private ChatMessage pendingMessage;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        ensureDefaults();
        loadConversations();
        setContentView(buildView());
        installKeyboardInsetWatcher();
        renderMessages();
        if (!isLoggedIn()) {
            scrollView.postDelayed(() -> showAuthDialog(false), 300);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 248, 250));
        root.setPadding(dp(14), dp(14) + systemBarHeight("status_bar"),
                dp(14), dp(10) + systemBarHeight("navigation_bar"));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("AI Chat");
        title.setTextColor(Color.rgb(24, 30, 38));
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER_VERTICAL);
        topBar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button newButton = topButton("新建");
        newButton.setOnClickListener(v -> newConversation());
        topBar.addView(newButton, new LinearLayout.LayoutParams(dp(68), dp(44)));

        Button historyButton = topButton("历史");
        historyButton.setOnClickListener(v -> showHistoryDialog());
        topBar.addView(historyButton, new LinearLayout.LayoutParams(dp(68), dp(44)));

        Button accountButton = topButton(isLoggedIn() ? "账号" : "登录");
        accountButton.setOnClickListener(v -> {
            if (isLoggedIn()) {
                showAccountDialog();
            } else {
                showAuthDialog(false);
            }
        });
        topBar.addView(accountButton, new LinearLayout.LayoutParams(dp(68), dp(44)));

        root.addView(topBar);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        messagesLayout = new LinearLayout(this);
        messagesLayout.setOrientation(LinearLayout.VERTICAL);
        messagesLayout.setPadding(0, dp(10), 0, dp(10));
        scrollView.addView(messagesLayout, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(94, 105, 120));
        statusText.setTextSize(13);
        statusText.setVisibility(View.GONE);
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(statusText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(0, dp(8), 0, 0);

        input = new EditText(this);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setHint("输入消息");
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackgroundResource(R.drawable.input_background);
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollToBottom();
            }
        });
        composer.addView(input, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        sendButton = new Button(this);
        sendButton.setText("发送");
        sendButton.setTextColor(Color.WHITE);
        sendButton.setAllCaps(false);
        sendButton.setBackgroundResource(R.drawable.send_button);
        sendButton.setOnClickListener(v -> sendMessage());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(82), dp(52));
        sendParams.leftMargin = dp(8);
        composer.addView(sendButton, sendParams);

        root.addView(composer);
        return root;
    }

    private Button topButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void installKeyboardInsetWatcher() {
        View content = root;
        content.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect rect = new Rect();
                content.getWindowVisibleDisplayFrame(rect);
                int height = content.getRootView().getHeight();
                int keyboard = height - rect.bottom;
                boolean keyboardVisible = keyboard > height * 0.15f;
                int bottom = keyboardVisible
                        ? keyboard + dp(8)
                        : dp(10) + systemBarHeight("navigation_bar");
                root.setPadding(dp(14), dp(14) + systemBarHeight("status_bar"), dp(14), bottom);
                if (keyboardVisible) {
                    scrollToBottom();
                }
            }
        });
    }

    private void renderMessages() {
        messagesLayout.removeAllViews();
        if (messages.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("开始一段对话吧。聊天记录会保存在本机。");
            empty.setTextColor(Color.rgb(94, 105, 120));
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            messagesLayout.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(180)));
            return;
        }

        for (ChatMessage message : messages) {
            TextView bubble = new TextView(this);
            bubble.setText(message.content);
            bubble.setTextSize(16);
            bubble.setLineSpacing(dp(2), 1.0f);
            bubble.setTextColor(message.isUser() ? Color.WHITE : Color.rgb(27, 34, 43));
            bubble.setBackgroundResource(message.isUser()
                    ? R.drawable.chat_bubble_user
                    : R.drawable.chat_bubble_assistant);
            bubble.setGravity(Gravity.START);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = message.isUser() ? Gravity.END : Gravity.START;
            params.setMargins(message.isUser() ? dp(50) : 0, dp(6),
                    message.isUser() ? 0 : dp(50), dp(6));
            messagesLayout.addView(bubble, params);
        }
        scrollToBottom();
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (!isLoggedIn()) {
            showAuthDialog(false);
            Toast.makeText(this, "请先登录" + BRAND_NAME + "账号", Toast.LENGTH_SHORT).show();
            return;
        }

        input.setText("");
        hideKeyboard();
        ensureActiveConversation();
        if (messages.isEmpty()) {
            currentConversation.title = titleFromText(text);
        }
        messages.add(new ChatMessage(ChatMessage.ROLE_USER, text));
        pendingMessage = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "正在连接服务器...");
        messages.add(pendingMessage);
        saveCurrentConversation();
        renderMessages();
        setLoading(true, "正在连接服务器...");

        ArrayList<ChatMessage> requestMessages = new ArrayList<>(messages);
        String model = getModel();
        executor.execute(() -> {
            try {
                String apiKey = ensureGatewayApiKey();
                client.ping(apiKey);
                runOnUiThread(() -> updatePendingMessage("服务器已连通，正在生成回复..."));
                String answer = client.chat(apiKey, model, requestMessages);
                runOnUiThread(() -> {
                    replacePendingMessage(answer);
                    saveCurrentConversation();
                    renderMessages();
                    setLoading(false, "");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    replacePendingMessage(userFacingError(e));
                    saveCurrentConversation();
                    renderMessages();
                    setLoading(false, "");
                });
            }
        });
    }

    private String userFacingError(Exception e) {
        String message = e.getMessage() == null ? "未知错误" : e.getMessage();
        if (message.contains("503") || message.contains("Service temporarily unavailable")) {
            return "服务临时不可用，请稍后重试，或在账号里切换模型。";
        }
        if (message.contains("assigned to any group")) {
            return "当前账号的 API Key 尚未分配可用分组，请联系管理员给账号分配分组。";
        }
        if (message.contains("没有可用分组")) {
            return "当前账号还没有可用分组，注册后请等待后台开通，或联系管理员开通分组。";
        }
        return "请求失败：" + message;
    }

    private void showAuthDialog(boolean registerMode) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), 0, dp(18), 0);

        TextView note = new TextView(this);
        note.setText(registerMode
                ? "注册" + BRAND_NAME + "账号。填写邮箱和密码，先发送验证码，再输入验证码完成注册。"
                : "使用" + BRAND_NAME + "账号登录后，App 会自动准备对话所需的 API Key。");
        note.setTextColor(Color.rgb(94, 105, 120));
        note.setTextSize(14);
        layout.addView(note);

        EditText emailInput = new EditText(this);
        emailInput.setHint("邮箱");
        emailInput.setSingleLine(true);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setText(prefs.getString(KEY_EMAIL, ""));
        layout.addView(emailInput);

        EditText passwordInput = new EditText(this);
        passwordInput.setHint("密码（至少6位）");
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);

        EditText codeInput = new EditText(this);
        codeInput.setHint("邮箱验证码");
        codeInput.setSingleLine(true);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        layout.addView(codeInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(registerMode ? "注册" + BRAND_NAME : "登录" + BRAND_NAME)
                .setView(layout)
                .setPositiveButton(registerMode ? "注册" : "登录", null)
                .setNeutralButton(registerMode ? "发送验证码" : "去注册", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String email = emailInput.getText().toString().trim();
                String password = passwordInput.getText().toString();
                String code = codeInput.getText().toString().trim();
                if (!validateAuthInput(email, password, registerMode && code.isEmpty())) {
                    return;
                }
                submitAuth(dialog, registerMode, email, password, code);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (!registerMode) {
                    dialog.dismiss();
                    showAuthDialog(true);
                    return;
                }
                String email = emailInput.getText().toString().trim();
                if (email.isEmpty() || !email.contains("@")) {
                    Toast.makeText(this, "请先填写有效邮箱", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendVerifyCode(email);
            });
        });
        dialog.show();
    }

    private void submitAuth(AlertDialog dialog, boolean registerMode, String email, String password, String code) {
        setLoading(true, "正在登录...");
        executor.execute(() -> {
            try {
                JSONObject data = registerMode
                        ? client.register(email, password, code)
                        : client.login(email, password);
                saveAuth(email, data);
                ensureGatewayApiKey();
                runOnUiThread(() -> {
                    dialog.dismiss();
                    setLoading(false, "");
                    Toast.makeText(this, registerMode ? "注册并登录成功，可以直接聊天" : "登录成功", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false, "");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void sendVerifyCode(String email) {
        setLoading(true, "正在发送验证码...");
        executor.execute(() -> {
            try {
                int countdown = client.sendVerifyCode(email);
                runOnUiThread(() -> {
                    setLoading(false, "");
                    Toast.makeText(this, "验证码已发送，" + countdown + "秒后可重发", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false, "");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean validateAuthInput(String email, String password, boolean missingCode) {
        if (email.isEmpty() || !email.contains("@")) {
            Toast.makeText(this, "请输入有效邮箱", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "密码至少6位", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (missingCode) {
            Toast.makeText(this, "请填写邮箱验证码", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void showAccountDialog() {
        String email = prefs.getString(KEY_EMAIL, "");
        String keyState = prefs.getString(KEY_GATEWAY_API_KEY, "").isEmpty() ? "未创建" : "已保存";
        new AlertDialog.Builder(this)
                .setTitle(BRAND_NAME + "账号")
                .setMessage("当前账号：" + email + "\nAPI Key：" + keyState + "\n模型：" + getModel())
                .setPositiveButton("模型选择", (dialog, which) -> showModelDialog())
                .setNegativeButton("退出登录", (dialog, which) -> logout())
                .setNeutralButton("关闭", null)
                .show();
    }

    private void showModelDialog() {
        setLoading(true, "正在读取模型列表...");
        executor.execute(() -> {
            ArrayList<String> models = new ArrayList<>();
            try {
                if (isLoggedIn()) {
                    models.addAll(client.models(ensureGatewayApiKey()));
                }
            } catch (Exception ignored) {
                // Fall back to the known working models.
            }
            if (models.isEmpty()) {
                for (String model : DEFAULT_MODELS) {
                    models.add(model);
                }
            }
            runOnUiThread(() -> {
                setLoading(false, "");
                showModelChoices(models);
            });
        });
    }

    private void showModelChoices(List<String> models) {
        String[] items = models.toArray(new String[0]);
        int checked = 0;
        String current = getModel();
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(current)) {
                checked = i;
                break;
            }
        }
        final int[] selected = {checked};
        new AlertDialog.Builder(this)
                .setTitle("模型选择")
                .setSingleChoiceItems(items, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton("保存", (dialog, which) -> {
                    prefs.edit().putString(KEY_MODEL, items[selected[0]]).apply();
                    Toast.makeText(this, "已切换到 " + items[selected[0]], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHistoryDialog() {
        ensureActiveConversation();
        String[] items = new String[conversations.size()];
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conversation = conversations.get(i);
            items[i] = conversation.title + "\n" + formatTime(conversation.updatedAt);
        }
        new AlertDialog.Builder(this)
                .setTitle("历史对话")
                .setItems(items, (dialog, which) -> switchConversation(conversations.get(which).id))
                .setPositiveButton("新建对话", (dialog, which) -> newConversation())
                .setNegativeButton("删除当前", (dialog, which) -> confirmDeleteCurrentConversation())
                .setNeutralButton("关闭", null)
                .show();
    }

    private void newConversation() {
        saveCurrentConversation();
        Conversation conversation = Conversation.create("新对话");
        conversations.add(0, conversation);
        currentConversation = conversation;
        messages.clear();
        saveConversations();
        renderMessages();
        input.requestFocus();
        showKeyboard();
    }

    private void switchConversation(String id) {
        saveCurrentConversation();
        for (Conversation conversation : conversations) {
            if (conversation.id.equals(id)) {
                currentConversation = conversation;
                messages.clear();
                messages.addAll(conversation.messages);
                prefs.edit().putString(KEY_CURRENT_CONVERSATION_ID, id).apply();
                renderMessages();
                return;
            }
        }
    }

    private void confirmDeleteCurrentConversation() {
        if (conversations.size() <= 1 && messages.isEmpty()) {
            Toast.makeText(this, "当前没有可删除的对话", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除当前对话")
                .setMessage("只删除本机保存的这段聊天记录。")
                .setPositiveButton("删除", (dialog, which) -> {
                    conversations.remove(currentConversation);
                    if (conversations.isEmpty()) {
                        currentConversation = Conversation.create("新对话");
                        conversations.add(currentConversation);
                    } else {
                        currentConversation = conversations.get(0);
                    }
                    messages.clear();
                    messages.addAll(currentConversation.messages);
                    saveConversations();
                    renderMessages();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void ensureDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        if (prefs.getString(KEY_MODEL, "").isEmpty()) {
            editor.putString(KEY_MODEL, DEFAULT_MODEL);
        }
        String savedModel = prefs.getString(KEY_MODEL, "");
        if ("gpt-5.2".equals(savedModel) || "gpt-4o-mini".equals(savedModel)) {
            editor.putString(KEY_MODEL, DEFAULT_MODEL);
        }
        editor.apply();
    }

    private boolean isLoggedIn() {
        return !prefs.getString(KEY_ACCESS_TOKEN, "").isEmpty();
    }

    private void saveAuth(String email, JSONObject data) {
        if (data == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_EMAIL, email)
                .putString(KEY_ACCESS_TOKEN, data.optString("access_token", ""))
                .putString(KEY_REFRESH_TOKEN, data.optString("refresh_token", ""));
        JSONObject user = data.optJSONObject("user");
        if (user != null) {
            editor.putString(KEY_EMAIL, user.optString("email", email));
        }
        editor.apply();
    }

    private String ensureGatewayApiKey() throws Exception {
        String key = prefs.getString(KEY_GATEWAY_API_KEY, "");
        int version = prefs.getInt(KEY_GATEWAY_API_KEY_VERSION, 0);
        if (!key.isEmpty() && version >= GATEWAY_API_KEY_VERSION) {
            return key;
        }
        key = client.ensureMobileApiKey(prefs.getString(KEY_ACCESS_TOKEN, ""));
        prefs.edit()
                .putString(KEY_GATEWAY_API_KEY, key)
                .putInt(KEY_GATEWAY_API_KEY_VERSION, GATEWAY_API_KEY_VERSION)
                .apply();
        return key;
    }

    private void logout() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_GATEWAY_API_KEY)
                .remove(KEY_GATEWAY_API_KEY_VERSION)
                .apply();
        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
        showAuthDialog(false);
    }

    private String getModel() {
        String model = prefs.getString(KEY_MODEL, BuildConfig.OPENAI_MODEL);
        return model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim();
    }

    private void loadConversations() {
        conversations.clear();
        String raw = prefs.getString(KEY_CONVERSATIONS, "");
        if (!raw.isEmpty()) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    conversations.add(Conversation.fromJson(array.getJSONObject(i)));
                }
            } catch (JSONException ignored) {
                conversations.clear();
            }
        }
        if (conversations.isEmpty()) {
            migrateLegacyHistory();
        }
        if (conversations.isEmpty()) {
            conversations.add(Conversation.create("新对话"));
        }

        String currentId = prefs.getString(KEY_CURRENT_CONVERSATION_ID, "");
        currentConversation = conversations.get(0);
        for (Conversation conversation : conversations) {
            if (conversation.id.equals(currentId)) {
                currentConversation = conversation;
                break;
            }
        }
        messages.clear();
        messages.addAll(currentConversation.messages);
        saveConversations();
    }

    private void migrateLegacyHistory() {
        String raw = prefs.getString(KEY_LEGACY_HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            if (array.length() == 0) {
                return;
            }
            Conversation conversation = Conversation.create("历史对话");
            conversation.messages.clear();
            for (int i = 0; i < array.length(); i++) {
                conversation.messages.add(ChatMessage.fromJson(array.getJSONObject(i)));
            }
            conversation.refreshTitle();
            conversation.updatedAt = System.currentTimeMillis();
            conversations.add(conversation);
        } catch (JSONException ignored) {
            conversations.clear();
        }
    }

    private void saveCurrentConversation() {
        ensureActiveConversation();
        currentConversation.messages.clear();
        currentConversation.messages.addAll(messages);
        currentConversation.updatedAt = System.currentTimeMillis();
        currentConversation.refreshTitle();
        conversations.remove(currentConversation);
        conversations.add(0, currentConversation);
        saveConversations();
    }

    private void ensureActiveConversation() {
        if (currentConversation == null) {
            currentConversation = Conversation.create("新对话");
            conversations.add(0, currentConversation);
        }
    }

    private void saveConversations() {
        JSONArray array = new JSONArray();
        for (Conversation conversation : conversations) {
            try {
                array.put(conversation.toJson(pendingMessage));
            } catch (JSONException ignored) {
                // Skip malformed conversations.
            }
        }
        prefs.edit()
                .putString(KEY_CONVERSATIONS, array.toString())
                .putString(KEY_CURRENT_CONVERSATION_ID, currentConversation.id)
                .apply();
    }

    private void setLoading(boolean loading, String status) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusText.setText(status == null ? "" : status);
        statusText.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!loading);
        input.setEnabled(!loading);
    }

    private void updatePendingMessage(String status) {
        if (pendingMessage != null) {
            int index = messages.indexOf(pendingMessage);
            if (index >= 0) {
                pendingMessage = new ChatMessage(ChatMessage.ROLE_ASSISTANT, waitingText());
                messages.set(index, pendingMessage);
            }
        }
        renderMessages();
        if (statusText != null) {
            statusText.setText(status);
        }
    }

    private void replacePendingMessage(String content) {
        if (pendingMessage != null) {
            int index = messages.indexOf(pendingMessage);
            if (index >= 0) {
                messages.set(index, new ChatMessage(ChatMessage.ROLE_ASSISTANT, content));
                pendingMessage = null;
                return;
            }
        }
        messages.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, content));
        pendingMessage = null;
    }

    private String waitingText() {
        String[] tips = {
                "服务器已连通，正在生成回复...",
                "连接正常，正在思考...",
                "请求已送达，稍等一下..."
        };
        return tips[random.nextInt(tips.length)];
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        input.postDelayed(() -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    private String titleFromText(String text) {
        String clean = text.replace('\n', ' ').trim();
        if (clean.length() <= 18) {
            return clean.isEmpty() ? "新对话" : clean;
        }
        return clean.substring(0, 18) + "...";
    }

    private String formatTime(long time) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(time));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int systemBarHeight(String name) {
        int resourceId = getResources().getIdentifier(name + "_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private static final class Conversation {
        final String id;
        String title;
        long updatedAt;
        final List<ChatMessage> messages = new ArrayList<>();

        private Conversation(String id, String title, long updatedAt) {
            this.id = id;
            this.title = title;
            this.updatedAt = updatedAt;
        }

        static Conversation create(String title) {
            return new Conversation("c_" + System.currentTimeMillis(), title, System.currentTimeMillis());
        }

        void refreshTitle() {
            for (ChatMessage message : messages) {
                if (message.isUser()) {
                    String clean = message.content.replace('\n', ' ').trim();
                    if (!clean.isEmpty()) {
                        title = clean.length() <= 18 ? clean : clean.substring(0, 18) + "...";
                    }
                    return;
                }
            }
            if (title == null || title.trim().isEmpty()) {
                title = "新对话";
            }
        }

        JSONObject toJson(ChatMessage excludedMessage) throws JSONException {
            JSONObject object = new JSONObject();
            object.put("id", id);
            object.put("title", title);
            object.put("updatedAt", updatedAt);
            JSONArray array = new JSONArray();
            for (ChatMessage message : messages) {
                if (message == excludedMessage) {
                    continue;
                }
                array.put(message.toJson());
            }
            object.put("messages", array);
            return object;
        }

        static Conversation fromJson(JSONObject object) {
            Conversation conversation = new Conversation(
                    object.optString("id", "c_" + System.currentTimeMillis()),
                    object.optString("title", "新对话"),
                    object.optLong("updatedAt", System.currentTimeMillis()));
            JSONArray array = object.optJSONArray("messages");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) {
                        conversation.messages.add(ChatMessage.fromJson(item));
                    }
                }
            }
            conversation.refreshTitle();
            return conversation;
        }
    }
}
