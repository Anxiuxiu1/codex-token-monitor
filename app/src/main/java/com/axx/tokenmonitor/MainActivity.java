package com.axx.tokenmonitor;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String APP_VERSION = "1.3";
    private static final String API = "https://api.deepseek.com/user/balance";
    private static final String CACHE_URL = "http://127.0.0.1:8765/cache?scope=today";
    private static final String KEY_ALIAS = "token_monitor_api_key_v1";
    private static final String PREF_KEY_ENC = "key_enc";
    private static final String PREF_KEY_LEGACY = "key";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView big, sub, status, keyLabel;
    private TextView warn, today, average, forecast, toppedValue, grantedValue;
    private TextView cacheTitle, cacheDetail, cacheMeta;
    private TextView historySummary;
    private LinearLayout historyList;
    private TextView cacheLanValue;
    private Spark spark;
    private MixBar mixBar;
    private Ring cacheRing;
    private EditText keyInput;
    private EditText cacheUrlInput;
    private String cacheLanUrl = "";
    private File diagFile;
    private Runnable tick;
    private double lastTopped, lastGranted;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#17141B"));
        getWindow().setNavigationBarColor(Color.parseColor("#17141B"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(30), dp(22), dp(34));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#17141B"), Color.parseColor("#25181D")});
        root.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("DEEPSEEK  ·  TOKEN WALLET", 11, "#BFA8A0");
        title.setLetterSpacing(0.18f);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView live = text("● LIVE", 10, "#F6C56B");
        live.setLetterSpacing(0.12f);
        live.setGravity(Gravity.CENTER);
        live.setPadding(dp(10), dp(5), dp(10), dp(5));
        live.setBackground(round("#2A241E", "#55F6C56B", 1, 12));
        header.addView(live);
        root.addView(header);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(17), dp(18), dp(16));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#2C1E23"), Color.parseColor("#211B23")});
        heroBg.setCornerRadius(dp(22));
        heroBg.setStroke(dp(1), Color.parseColor("#66F6C56B"));
        hero.setBackground(heroBg);
        hero.setElevation(dp(3));

        TextView balanceLabel = text("实时可用余额", 11, "#BFA8A0");
        balanceLabel.setLetterSpacing(0.12f);
        hero.addView(balanceLabel);

        big = text("—", 44, "#FFF1DE");
        big.setPadding(0, dp(10), 0, 0);
        hero.addView(big);

        sub = text("", 13, "#BFA8A0");
        sub.setPadding(0, dp(6), 0, 0);
        hero.addView(sub);

        status = text("等待第一次同步", 11, "#8A7A72");
        status.setPadding(0, dp(13), 0, 0);
        hero.addView(status);

        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(18);
        root.addView(hero, hlp);

        warn = text("余额偏低，记得充值", 12, "#FFFFFF");
        warn.setGravity(Gravity.CENTER);
        warn.setPadding(dp(14), dp(9), dp(14), dp(9));
        warn.setBackground(round("#F06A5B", null, 0, 12));
        warn.setVisibility(View.GONE);
        LinearLayout.LayoutParams wlp = cardLp();
        wlp.topMargin = dp(10);
        root.addView(warn, wlp);

        // 消耗脉搏
        LinearLayout chartCard = new LinearLayout(this);
        chartCard.setOrientation(LinearLayout.VERTICAL);
        chartCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        chartCard.setBackground(round("#221E27", "#66F6C56B", 1, 18));
        chartCard.setElevation(dp(2));

        LinearLayout chartHead = new LinearLayout(this);
        chartHead.setOrientation(LinearLayout.HORIZONTAL);
        chartHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView chartTitle = text("消耗脉搏", 12, "#FFF1DE");
        chartHead.addView(chartTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chartRange = text("近 7 天", 10, "#8A7A72");
        chartHead.addView(chartRange);
        chartCard.addView(chartHead);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(14), 0, dp(10));
        today = metric(metrics, "今日消耗");
        average = metric(metrics, "7 日日均");
        forecast = metric(metrics, "预计可用");
        chartCard.addView(metrics);

        TextView chartHint = text("余额下降曲线", 10, "#8A7A72");
        chartHint.setLetterSpacing(0.1f);
        chartCard.addView(chartHint);

        spark = new Spark(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76));
        slp.topMargin = dp(7);
        chartCard.addView(spark, slp);

        LinearLayout.LayoutParams clp = cardLp();
        clp.topMargin = dp(10);
        root.addView(chartCard, clp);

        // 每日历史
        LinearLayout historyCard = new LinearLayout(this);
        historyCard.setOrientation(LinearLayout.VERTICAL);
        historyCard.setPadding(dp(16), dp(15), dp(16), dp(12));
        historyCard.setBackground(round("#221E27", "#33F6C56B", 1, 18));

        LinearLayout historyHead = new LinearLayout(this);
        historyHead.setOrientation(LinearLayout.HORIZONTAL);
        historyHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView historyTitle = text("历史消耗", 12, "#FFF1DE");
        historyHead.addView(historyTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        historySummary = text("近 7 天", 10, "#8A7A72");
        historyHead.addView(historySummary);
        historyCard.addView(historyHead);

        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyList.setPadding(0, dp(8), 0, 0);
        historyCard.addView(historyList);

        LinearLayout.LayoutParams hclp = cardLp();
        hclp.topMargin = dp(10);
        root.addView(historyCard, hclp);

        // 钱包构成
        LinearLayout mixCard = new LinearLayout(this);
        mixCard.setOrientation(LinearLayout.VERTICAL);
        mixCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        mixCard.setBackground(round("#221E27", "#33F6C56B", 1, 18));

        TextView mixTitle = text("钱包构成", 12, "#FFF1DE");
        mixCard.addView(mixTitle);
        mixBar = new MixBar(this);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(18));
        mlp.topMargin = dp(14);
        mixCard.addView(mixBar, mlp);

        LinearLayout mixLegend = new LinearLayout(this);
        mixLegend.setOrientation(LinearLayout.HORIZONTAL);
        mixLegend.setPadding(0, dp(11), 0, 0);
        toppedValue = legend(mixLegend, "充值余额", "#F06A5B");
        grantedValue = legend(mixLegend, "赠送余额", "#F6C56B");
        mixCard.addView(mixLegend);
        LinearLayout.LayoutParams mclp = cardLp();
        mclp.topMargin = dp(10);
        root.addView(mixCard, mclp);

        // Codex 缓存命中
        LinearLayout cacheCard = new LinearLayout(this);
        cacheCard.setOrientation(LinearLayout.HORIZONTAL);
        cacheCard.setGravity(Gravity.CENTER_VERTICAL);
        cacheCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        cacheCard.setBackground(round("#221E27", "#66F6C56B", 1, 18));

        cacheRing = new Ring(this);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(dp(86), dp(86));
        cacheCard.addView(cacheRing, ringLp);

        LinearLayout cacheInfo = new LinearLayout(this);
        cacheInfo.setOrientation(LinearLayout.VERTICAL);
        cacheInfo.setPadding(dp(16), 0, 0, 0);
        cacheCard.addView(cacheInfo, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        cacheTitle = text("今日 Codex 缓存命中", 13, "#FFF1DE");
        cacheInfo.addView(cacheTitle);
        cacheDetail = text("等待本机统计源", 11, "#BFA8A0");
        cacheDetail.setPadding(0, dp(7), 0, 0);
        cacheInfo.addView(cacheDetail);
        cacheMeta = text("只读 token 计数，不读取对话内容", 10, "#8A7A72");
        cacheMeta.setPadding(0, dp(7), 0, 0);
        cacheInfo.addView(cacheMeta);

        LinearLayout.LayoutParams cclp = cardLp();
        cclp.topMargin = dp(10);
        root.addView(cacheCard, cclp);

        // 操作区
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);

        TextView topup = pill("去官方充值", "#1E1A21", "#F6C56B");
        topup.setBackground(round("#1E1A21", "#66F6C56B", 1, 16));
        topup.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://platform.deepseek.com/top_up")));
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.rightMargin = dp(6);
        actions.addView(topup, tlp);

        TextView refresh = pill("刷新", "#F06A5B", "#FFF1DE");
        refresh.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().rotationBy(360).setDuration(380).start();
            fetch();
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rlp.leftMargin = dp(6);
        actions.addView(refresh, rlp);
        root.addView(actions);

        // API Key
        TextView keyTitle = text("连接密钥", 12, "#FFF1DE");
        keyTitle.setPadding(0, dp(28), 0, 0);
        root.addView(keyTitle);

        keyLabel = text("", 11, "#8A7A72");
        keyLabel.setPadding(0, dp(7), 0, dp(11));
        root.addView(keyLabel);

        LinearLayout keyBox = new LinearLayout(this);
        keyBox.setOrientation(LinearLayout.HORIZONTAL);
        keyBox.setGravity(Gravity.CENTER_VERTICAL);

        keyInput = new EditText(this);
        keyInput.setHint("sk-...");
        keyInput.setTextSize(14);
        keyInput.setTextColor(Color.parseColor("#FFF1DE"));
        keyInput.setHintTextColor(Color.parseColor("#6E6059"));
        keyInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        keyInput.setBackground(round("#1E1A21", "#33F6C56B", 1, 14));
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams inp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        inp.rightMargin = dp(10);
        keyBox.addView(keyInput, inp);

        TextView save = pill("保存", "#F6C56B", "#17141B");
        keyBox.addView(save);
        root.addView(keyBox, cardLp());

        save.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            String entered = keyInput.getText().toString().trim();
            boolean saved = entered.isEmpty()
                    ? clearApiKey()
                    : saveApiKey(entered);
            if (!saved) {
                status.setText("Key 加密保存失败，请重试");
                return;
            }
            keyInput.setText("");
            updateKeyLabel();
            fetch();
        });

        TextView cacheUrlLabel = text("缓存统计源（可选）", 11, "#8A7A72");
        cacheUrlLabel.setPadding(0, dp(18), 0, dp(9));
        root.addView(cacheUrlLabel);

        LinearLayout cacheUrlBox = new LinearLayout(this);
        cacheUrlBox.setOrientation(LinearLayout.HORIZONTAL);
        cacheUrlBox.setGravity(Gravity.CENTER_VERTICAL);
        cacheUrlInput = new EditText(this);
        cacheUrlInput.setTextSize(12);
        cacheUrlInput.setTextColor(Color.parseColor("#FFF1DE"));
        cacheUrlInput.setHintTextColor(Color.parseColor("#6E6059"));
        cacheUrlInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        cacheUrlInput.setBackground(round("#1E1A21", "#33F6C56B", 1, 14));
        cacheUrlInput.setSingleLine(true);
        cacheUrlInput.setText(getSharedPreferences("prefs", MODE_PRIVATE)
                .getString("cache_url", CACHE_URL));
        LinearLayout.LayoutParams cup = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cup.rightMargin = dp(10);
        cacheUrlBox.addView(cacheUrlInput, cup);
        TextView saveCacheUrl = pill("应用", "#F6C56B", "#17141B");
        saveCacheUrl.setOnClickListener(v -> {
            getSharedPreferences("prefs", MODE_PRIVATE).edit()
                    .putString("cache_url", cacheUrlInput.getText().toString().trim()).apply();
            fetchCache();
        });
        cacheUrlBox.addView(saveCacheUrl);
        root.addView(cacheUrlBox, cardLp());

        LinearLayout lanBox = new LinearLayout(this);
        lanBox.setOrientation(LinearLayout.HORIZONTAL);
        lanBox.setGravity(Gravity.CENTER_VERTICAL);
        lanBox.setPadding(0, dp(10), 0, 0);
        cacheLanValue = text("当前电脑局域网地址：—", 10, "#8A7A72");
        cacheLanValue.setSingleLine(true);
        cacheLanValue.setTextIsSelectable(true);
        lanBox.addView(cacheLanValue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView copyLan = pill("复制", "#1E1A21", "#F6C56B");
        copyLan.setBackground(round("#1E1A21", "#66F6C56B", 1, 16));
        copyLan.setOnClickListener(v -> {
            if (cacheLanUrl == null || cacheLanUrl.isEmpty()) {
                Toast.makeText(this, "还没有发现电脑地址", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Codex cache URL", cacheLanUrl));
            Toast.makeText(this, "电脑地址已复制", Toast.LENGTH_SHORT).show();
        });
        lanBox.addView(copyLan);

        TextView diag = pill("日志", "#1E1A21", "#BFA8A0");
        diag.setBackground(round("#1E1A21", "#33F6C56B", 1, 16));
        diag.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "Token monitor diagnostic log", readDiagLog()));
            Toast.makeText(this, "诊断日志已复制", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = dp(6);
        lanBox.addView(diag, dlp);
        root.addView(lanBox);

        scroll.addView(root);
        setContentView(scroll);
        initDiagLog();
        diagLog("APP_START version=" + APP_VERSION);
        updateKeyLabel();
        fetch();
        fetchCache();

        tick = () -> {
            if (!isFinishing()) {
                fetch();
                fetchCache();
                h.postDelayed(tick, 60000);
            }
        };
        h.postDelayed(tick, 60000);
    }

    private TextView text(String s, int sp, String color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.START);
        return t;
    }

    private TextView pill(String s, String bg, String fg) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(Color.parseColor(fg));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(9), dp(18), dp(9));
        t.setBackground(round(bg, null, 0, 16));
        return t;
    }

    private TextView metric(LinearLayout row, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView l = text(label, 10, "#8A7A72");
        box.addView(l);
        TextView v = text("—", 16, "#FFF1DE");
        v.setPadding(0, dp(4), 0, 0);
        box.addView(v);
        row.addView(box, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    private TextView legend(LinearLayout row, String label, String color) {
        TextView v = text("●  " + label + "  —", 11, color);
        v.setSingleLine(true);
        row.addView(v, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    private GradientDrawable round(String fill, String stroke, int strokeDp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(fill));
        d.setCornerRadius(dp(radiusDp));
        if (stroke != null) {
            d.setStroke(dp(strokeDp), Color.parseColor(stroke));
        }
        return d;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void initDiagLog() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = getFilesDir();
        }
        diagFile = new File(dir, "diagnostic.log");
    }

    private void diagLog(String message) {
        if (diagFile == null) {
            return;
        }
        try {
            String stamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date());
            FileOutputStream out = new FileOutputStream(diagFile, true);
            out.write((stamp + " " + message + "\n").getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
        }
    }

    private String readDiagLog() {
        if (diagFile == null || !diagFile.exists()) {
            return "暂无诊断日志";
        }
        java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(diagFile), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                recent.addLast(line);
                while (recent.size() > 120) {
                    recent.removeFirst();
                }
            }
            reader.close();
        } catch (Exception e) {
            return "日志读取失败：" + e.getClass().getSimpleName();
        }
        if (recent.isEmpty()) {
            return "暂无诊断日志";
        }
        StringBuilder result = new StringBuilder();
        for (String line : recent) {
            result.append(line).append('\n');
        }
        return result.toString();
    }

    private void updateKeyLabel() {
        String k = readApiKey();
        keyLabel.setText(k == null || k.isEmpty() ? "还没填 API Key" :
                "已加密保存：" + k.substring(0, Math.min(6, k.length())) + "…" +
                        k.substring(Math.max(0, k.length() - 4)));
    }

    private boolean saveApiKey(String key) {
        try {
            String encrypted = encryptSecret(key);
            return getSharedPreferences("prefs", MODE_PRIVATE).edit()
                    .putString(PREF_KEY_ENC, encrypted)
                    .remove(PREF_KEY_LEGACY)
                    .commit();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean clearApiKey() {
        return getSharedPreferences("prefs", MODE_PRIVATE).edit()
                .remove(PREF_KEY_ENC)
                .remove(PREF_KEY_LEGACY)
                .commit();
    }

    private String readApiKey() {
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String encrypted = prefs.getString(PREF_KEY_ENC, "");
        if (encrypted != null && !encrypted.isEmpty()) {
            try {
                return decryptSecret(encrypted);
            } catch (Exception e) {
                prefs.edit().remove(PREF_KEY_ENC).apply();
                return "";
            }
        }

        // Migrate the first version that stored the key in plain text.
        String legacy = prefs.getString(PREF_KEY_LEGACY, "");
        if (legacy == null || legacy.isEmpty()) {
            return "";
        }
        if (saveApiKey(legacy)) {
            return legacy;
        }
        return legacy;
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build();
            generator.init(spec);
            generator.generateKey();
        }
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(
                KEY_ALIAS, null);
        if (entry == null) {
            throw new IllegalStateException("Keystore entry unavailable");
        }
        return entry.getSecretKey();
    }

    private String encryptSecret(String value) throws Exception {
        SecretKey key = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(value.getBytes("UTF-8"));
        return "v1:" + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    private String decryptSecret(String value) throws Exception {
        String[] parts = value.split(":", 3);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalArgumentException("Unknown key format");
        }
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(parts[2], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(),
                new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), "UTF-8");
    }

    private void addPoint(double v) {
        try {
            SharedPreferences p = getSharedPreferences("prefs", MODE_PRIVATE);
            JSONArray a = new JSONArray(p.getString("hist", "[]"));
            long now = System.currentTimeMillis();
            if (a.length() > 0) {
                JSONArray last = a.getJSONArray(a.length() - 1);
                if (Math.abs(last.getDouble(1) - v) < 0.0001 && now - last.getLong(0) < 600000) {
                    return;
                }
            }
            JSONArray pt = new JSONArray();
            pt.put(now);
            pt.put(v);
            a.put(pt);
            while (a.length() > 500) {
                a.remove(0);
            }
            p.edit().putString("hist", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void refreshStats(double nowV) {
        try {
            JSONArray a = new JSONArray(getSharedPreferences("prefs", MODE_PRIVATE).getString("hist", "[]"));
            long now = System.currentTimeMillis();
            long start7 = now - 7L * 24 * 3600 * 1000;
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.set(java.util.Calendar.HOUR_OF_DAY, 0);
            c.set(java.util.Calendar.MINUTE, 0);
            c.set(java.util.Calendar.SECOND, 0);
            c.set(java.util.Calendar.MILLISECOND, 0);
            long dayStart = c.getTimeInMillis();

            java.util.List<Float> pts = new java.util.ArrayList<>();
            double spend = 0, spend7 = 0, prev = -1;
            long first7 = -1;
            boolean prevToday = false;
            for (int i = 0; i < a.length(); i++) {
                JSONArray p = a.getJSONArray(i);
                long t = p.getLong(0);
                double v = p.getDouble(1);
                if (t >= start7) {
                    pts.add((float) v);
                    if (first7 < 0) {
                        first7 = t;
                    }
                    if (prev >= 0 && v < prev) {
                        spend7 += prev - v;
                    }
                }
                if (t >= dayStart) {
                    if (prevToday && v < prev) {
                        spend += prev - v;
                    }
                    prevToday = true;
                } else {
                    prevToday = false;
                }
                prev = v;
            }
            double avg = spend7 / Math.max(1d,
                    first7 > 0 ? (now - first7) / (double) DAY_MS : 1d);
            if (first7 <= 0 || pts.size() < 2) {
                avg = 0;
            }
            spark.setVals(pts);
            today.setText("¥" + money(spend));
            average.setText("¥" + money(avg));
            forecast.setText(avg > 0.0001
                    ? Math.max(0, Math.round(nowV / avg)) + " 天" : "—");
            warn.setVisibility(nowV < 10 ? View.VISIBLE : View.GONE);
            refreshHistory(a, now);
        } catch (Exception ignored) {
        }
    }

    private void refreshHistory(JSONArray history, long now) {
        java.util.Map<String, DayStat> days = new java.util.LinkedHashMap<>();
        SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String previousDay = "";
        for (int i = 0; i < history.length(); i++) {
            try {
                JSONArray point = history.getJSONArray(i);
                long stamp = point.getLong(0);
                double balance = point.getDouble(1);
                String key = keyFormat.format(new Date(stamp));
                DayStat stat = days.get(key);
                if (stat == null) {
                    stat = new DayStat();
                    days.put(key, stat);
                }
                if (!key.equals(previousDay)) {
                    stat.previous = -1;
                    previousDay = key;
                }
                if (stat.previous >= 0 && balance < stat.previous) {
                    stat.spend += stat.previous - balance;
                }
                stat.previous = balance;
                stat.endBalance = balance;
            } catch (Exception ignored) {
            }
        }

        historyList.removeAllViews();
        SimpleDateFormat keyFormatForRow = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat labelFormat = new SimpleDateFormat("MM-dd E", Locale.CHINA);
        java.util.Calendar day = java.util.Calendar.getInstance();
        day.setTimeInMillis(now);
        day.set(java.util.Calendar.HOUR_OF_DAY, 0);
        day.set(java.util.Calendar.MINUTE, 0);
        day.set(java.util.Calendar.SECOND, 0);
        day.set(java.util.Calendar.MILLISECOND, 0);

        double total = 0;
        int shown = 0;
        for (int i = 0; i < 7; i++) {
            String key = keyFormatForRow.format(day.getTime());
            DayStat stat = days.get(key);
            if (stat != null && !Double.isNaN(stat.endBalance)) {
                total += stat.spend;
                addHistoryRow(labelFormat.format(day.getTime()), stat.spend, stat.endBalance);
                shown++;
            } else {
                addHistoryRow(labelFormat.format(day.getTime()), 0, Double.NaN);
            }
            day.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }
        historySummary.setText(shown == 0
                ? "暂无记录" : "7 天合计 ¥" + money(total));
    }

    private void addHistoryRow(String label, double spend, double balance) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, 0);

        TextView date = text(label, 11, "#BFA8A0");
        row.addView(date, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.36f));

        TextView used = text(spend > 0 ? "−¥" + money(spend) : "—", 12,
                spend > 0 ? "#F06A5B" : "#6E6059");
        used.setGravity(Gravity.END);
        row.addView(used, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.28f));

        TextView end = text(Double.isNaN(balance) ? "余额 —" : "余额 ¥" + money(balance),
                12, Double.isNaN(balance) ? "#6E6059" : "#FFF1DE");
        end.setGravity(Gravity.END);
        row.addView(end, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.36f));
        historyList.addView(row);
    }

    private String money(double v) {
        return String.format(Locale.getDefault(), "%.2f", v);
    }

    private static class DayStat {
        double spend;
        double previous = -1;
        double endBalance = Double.NaN;
    }

    private void fetchCache() {
        new Thread(() -> {
            String err = null;
            String scope = "today";
            double rate = 0;
            long cached = 0, input = 0;
            int samples = 0;
            String lanUrl = "";
            String configured = getSharedPreferences("prefs", MODE_PRIVATE)
                    .getString("cache_url", CACHE_URL);
            String queryScope = cacheScope(configured);
            String successUrl = null;
            String[] candidates = new String[]{
                    configured,
                    "http://10.0.2.2:8765/cache?scope=" + queryScope
            };
            diagLog("CACHE_FETCH configured=" + configured + " scope=" + queryScope);
            for (String candidate : candidates) {
                if (candidate == null || candidate.isEmpty() ||
                        (successUrl != null && successUrl.equals(candidate))) {
                    continue;
                }
                if (candidate.equals(configured) && successUrl != null) {
                    continue;
                }
                diagLog("CACHE_TRY url=" + candidate);
                try {
                    JSONObject o = fetchCacheJson(candidate);
                    rate = o.optDouble("cache_hit_rate", 0);
                    cached = o.optLong("cached_input_tokens", 0);
                    input = o.optLong("input_tokens", 0);
                    samples = o.optInt("sample_count", 0);
                    scope = o.optString("scope", queryScope);
                    lanUrl = o.optString("lan_url", "");
                    successUrl = candidate;
                    err = null;
                    diagLog("CACHE_OK url=" + candidate + " scope=" + scope
                            + " requests=" + samples + " lan_url=" + lanUrl);
                    break;
                } catch (Exception e) {
                    err = "未连接本机统计源";
                    diagLog("CACHE_FAIL url=" + candidate + " error="
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }

            if (successUrl == null) {
                diagLog("DISCOVERY_START");
                String discovered = discoverCacheBridge(queryScope);
                if (discovered != null && !discovered.isEmpty()) {
                    diagLog("DISCOVERY_URL=" + discovered);
                    try {
                        JSONObject o = fetchCacheJson(discovered);
                        rate = o.optDouble("cache_hit_rate", 0);
                        cached = o.optLong("cached_input_tokens", 0);
                        input = o.optLong("input_tokens", 0);
                        samples = o.optInt("sample_count", 0);
                        scope = o.optString("scope", queryScope);
                        lanUrl = o.optString("lan_url", "");
                        successUrl = discovered;
                        err = null;
                        diagLog("CACHE_OK url=" + discovered + " scope=" + scope
                                + " requests=" + samples + " lan_url=" + lanUrl);
                    } catch (Exception e) {
                        diagLog("DISCOVERY_FETCH_FAIL url=" + discovered + " error="
                                + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                } else {
                    diagLog("DISCOVERY_NO_REPLY");
                }
            }

            if (successUrl != null && !successUrl.equals(configured)) {
                getSharedPreferences("prefs", MODE_PRIVATE).edit()
                        .putString("cache_url", successUrl).apply();
                final String adopted = successUrl;
                h.post(() -> {
                    if (cacheUrlInput != null) {
                        cacheUrlInput.setText(adopted);
                    }
                });
            }
            final String ferr = err;
            final double fr = rate;
            final long fc = cached, fi = input;
            final int fs = samples;
            final String fsco = scope;
            final String flan = lanUrl;
            h.post(() -> {
                if (ferr != null) {
                    diagLog("CACHE_UI_OFFLINE reason=" + ferr);
                    cacheRing.setRate(0, false);
                    cacheDetail.setText("未连接本机统计源");
                    cacheMeta.setText(ferr);
                    cacheLanValue.setText("当前电脑局域网地址：—");
                    return;
                }
                cacheLanUrl = flan;
                diagLog("CACHE_UI_OK lan_url=" + flan);
                cacheLanValue.setText(flan.isEmpty()
                        ? "当前电脑局域网地址：未发现"
                        : "当前电脑局域网地址：" + flan);
                String label = "all".equals(fsco) ? "全部" :
                        ("7d".equals(fsco) ? "近 7 天" : "今日");
                cacheTitle.setText(label + " Codex 缓存命中");
                cacheRing.setRate(fr, true);
                cacheDetail.setText(String.format(Locale.getDefault(),
                        "命中 %.1f%%  ·  %s 次请求", fr, fs));
                cacheMeta.setText("节省 " + shortNum(fc) + " / 输入 " +
                        shortNum(fi) + " tokens");
            });
        }).start();
    }

    private JSONObject fetchCacheJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(1200);
        c.setReadTimeout(2500);
        try {
            int code = c.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            return new JSONObject(read(c.getInputStream()));
        } finally {
            c.disconnect();
        }
    }

    private String cacheScope(String url) {
        if (url == null) {
            return "today";
        }
        int start = url.indexOf("scope=");
        if (start < 0) {
            return "today";
        }
        start += "scope=".length();
        int end = url.indexOf('&', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }

    private String discoverCacheBridge(String scope) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(1400);
            byte[] query = "CODEX_CACHE_BRIDGE".getBytes("UTF-8");
            InetAddress broadcast = InetAddress.getByName("255.255.255.255");
            socket.send(new DatagramPacket(query, query.length, broadcast, 8766));
            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            JSONObject o = new JSONObject(new String(
                    reply.getData(), 0, reply.getLength(), "UTF-8"));
            return o.optString("lan_url", "");
        } catch (Exception e) {
            diagLog("DISCOVERY_FAIL error=" + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return null;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private String shortNum(long v) {
        if (v >= 1000000) {
            return String.format(Locale.getDefault(), "%.2fM", v / 1000000d);
        }
        if (v >= 1000) {
            return String.format(Locale.getDefault(), "%.1fK", v / 1000d);
        }
        return String.valueOf(v);
    }

    private void fetch() {
        final String key = readApiKey();
        if (key == null || key.isEmpty()) {
            status.setText("先在下面填 API Key 并保存");
            return;
        }
        status.setText("刷新中…");
        new Thread(() -> {
            long started = System.currentTimeMillis();
            String err = null, total = null, granted = null, topped = null, cur = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("Authorization", "Bearer " + key);
                c.setRequestProperty("Accept", "application/json");
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                int code = c.getResponseCode();
                InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String body = read(is);
                if (code != 200) {
                    err = "HTTP " + code + " " + body;
                } else {
                    JSONObject o = new JSONObject(body);
                    JSONArray a = o.optJSONArray("balance_infos");
                    JSONObject f = (a != null && a.length() > 0) ? a.getJSONObject(0) : o;
                    cur = f.optString("currency", "");
                    total = f.optString("total_balance", "?");
                    granted = f.optString("granted_balance", "");
                    topped = f.optString("topped_up_balance", "");
                }
            } catch (Exception e) {
                err = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            final String ferr = err, ft = total, fg = granted, fto = topped, fc = cur;
            final long latency = Math.max(1, System.currentTimeMillis() - started);
            h.post(() -> {
                if (ferr != null) {
                    status.setText("出错：" + ferr);
                    return;
                }
                String sym = "CNY".equals(fc) ? "¥ " : (fc + " ");
                big.setText(sym + ft);
                try {
                    double v = Double.parseDouble(ft);
                    big.setTextColor(v < 10 ? Color.parseColor("#F06A5B") : Color.parseColor("#FFF1DE"));
                } catch (Exception ignored) {
                }
                sub.setText("充值余额 " + fto + "    赠送余额 " + fg);
                status.setText("更新于 " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())
                        + "  ·  连接 " + latency + " ms");
                try {
                    double v = Double.parseDouble(ft);
                    lastTopped = Double.parseDouble(fto);
                    lastGranted = Double.parseDouble(fg);
                    toppedValue.setText("●  充值余额  " + money(lastTopped));
                    grantedValue.setText("●  赠送余额  " + money(lastGranted));
                    mixBar.setVals(lastTopped, lastGranted);
                    addPoint(v);
                    refreshStats(v);
                } catch (Exception ignored) {
                }
            });
        }).start();
    }

    static class Ring extends View {
        private float rate;
        private boolean live;

        Ring(android.content.Context c) {
            super(c);
        }

        void setRate(double r, boolean on) {
            rate = (float) Math.max(0, Math.min(100, r));
            live = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            float size = Math.min(getWidth(), getHeight());
            float stroke = getResources().getDisplayMetrics().density * 7;
            RectF box = new RectF(stroke, stroke, size - stroke, size - stroke);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.parseColor("#352E38"));
            cv.drawArc(box, 135, 270, false, p);
            if (live) {
                p.setColor(Color.parseColor(rate >= 70 ? "#F6C56B" : "#F06A5B"));
                cv.drawArc(box, 135, 270 * rate / 100f, false, p);
            }

            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setTextAlign(Paint.Align.CENTER);
            t.setColor(Color.parseColor(live ? "#FFF1DE" : "#8A7A72"));
            t.setTextSize(getResources().getDisplayMetrics().density * 18);
            String text = live ? String.format(Locale.getDefault(), "%.0f%%", rate) : "—";
            float cy = size / 2f - (t.ascent() + t.descent()) / 2f;
            cv.drawText(text, size / 2f, cy, t);
        }
    }

    static class MixBar extends View {
        private double topped, granted;

        MixBar(android.content.Context c) {
            super(c);
        }

        void setVals(double t, double g) {
            topped = Math.max(0, t);
            granted = Math.max(0, g);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            float radius = getHeight() / 2f;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.parseColor("#302A34"));
            cv.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, p);
            double sum = topped + granted;
            if (sum <= 0) {
                drawCenter(cv, "暂无余额数据");
                return;
            }
            float cut = (float) (getWidth() * topped / sum);
            p.setColor(Color.parseColor("#F06A5B"));
            cv.drawRoundRect(0, 0, Math.min(getWidth(), Math.max(radius * 2, cut)),
                    getHeight(), radius, radius, p);
            if (granted > 0) {
                p.setColor(Color.parseColor("#F6C56B"));
                cv.drawRoundRect(Math.max(0, cut - radius), 0, getWidth(),
                        getHeight(), radius, radius, p);
            }
        }

        private void drawCenter(Canvas cv, String s) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(getResources().getDisplayMetrics().density * 9);
            p.setColor(Color.parseColor("#8A7A72"));
            cv.drawText(s, getWidth() / 2f, getHeight() / 2f -
                    (p.ascent() + p.descent()) / 2f, p);
        }
    }

    static class Spark extends View {
        private final java.util.List<Float> vals = new java.util.ArrayList<>();

        Spark(android.content.Context c) {
            super(c);
        }

        void setVals(java.util.List<Float> v) {
            vals.clear();
            vals.addAll(v);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(getResources().getDisplayMetrics().density * 1.8f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.parseColor("#F06A5B"));
            if (vals.size() < 2) {
                cv.drawLine(0, h * 0.6f, w, h * 0.6f, p);
                return;
            }
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (float v : vals) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            if (max - min < 0.01f) {
                max = min + 0.01f;
            }
            Path path = new Path();
            for (int i = 0; i < vals.size(); i++) {
                float x = i * w / (vals.size() - 1);
                float y = h - (vals.get(i) - min) / (max - min) * (h * 0.74f) - h * 0.13f;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            cv.drawPath(path, p);
            Path fill = new Path(path);
            fill.lineTo(w, h);
            fill.lineTo(0, h);
            fill.close();
            Paint fp = new Paint(p);
            fp.setStyle(Paint.Style.FILL);
            fp.setShader(new LinearGradient(0, 0, 0, h,
                    Color.parseColor("#55F06A5B"), Color.parseColor("#00F06A5B"),
                    Shader.TileMode.CLAMP));
            cv.drawPath(fill, fp);
        }
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line);
        }
        r.close();
        return sb.toString();
    }
}
