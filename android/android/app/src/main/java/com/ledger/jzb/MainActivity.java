package com.ledger.jzb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import com.getcapacitor.BridgeActivity;
import org.json.JSONObject;
import java.util.List;

public class MainActivity extends BridgeActivity {

    private static final String CHAN_ID = "quick_add";
    private static final int NOTIFY_ID = 2001;
    private MediaSessionCompat mediaSession;
    private NotificationManager nm;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        registerJsBridge();
        handleIntent(getIntent());
    }

    @Override
    public void onResume() {
        super.onResume();
        registerJsBridge();
        checkPending();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void checkPending() {
        List<PaymentListenerService.PendingTx> list = PaymentListenerService.drainPending();
        if (list == null || list.isEmpty()) return;
        PaymentListenerService.PendingTx tx = list.get(list.size() - 1);
        WebView wv = getBridge() != null ? getBridge().getWebView() : null;
        if (wv == null) return;
        try {
            JSONObject json = new JSONObject();
            json.put("source", tx.source);
            json.put("amount", tx.amount);
            json.put("type", tx.type);
            json.put("raw", tx.raw != null ? tx.raw : "");
            String js = "if(window.pendingTxHandler)window.pendingTxHandler(" + json.toString() + ")";
            wv.postDelayed(() -> wv.evaluateJavascript(js, null), 500);
        } catch (Exception e) {}
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if ("com.ledger.QUICK_ADD".equals(action)) {
            triggerQuickAdd();
        } else if ("com.ledger.CONFIRM_TX".equals(action)) {
            String src = intent.getStringExtra("source");
            double amt = intent.getDoubleExtra("amount", -1);
            int type = intent.getIntExtra("type", -1);
            String raw = intent.getStringExtra("raw");

            if ((src == null || amt < 0) && PaymentListenerService.lastTx != null) {
                PaymentListenerService.PendingTx tx = PaymentListenerService.lastTx;
                src = tx.source; amt = tx.amount; type = tx.type; raw = tx.raw;
            }
            if (src == null) return;

            final String fSrc = src;
            final double fAmt = amt;
            final int fType = type;
            final String fRaw = raw;

            WebView wv = getBridge() != null ? getBridge().getWebView() : null;
            if (wv != null) {
                try {
                    JSONObject json = new JSONObject();
                    json.put("source", fSrc);
                    json.put("amount", fAmt);
                    json.put("type", fType);
                    json.put("raw", fRaw != null ? fRaw : "");
                    String js = "if(window.pendingTxHandler)window.pendingTxHandler(" + json.toString() + ")";
                    wv.postDelayed(() -> wv.evaluateJavascript(js, null), 300);
                } catch (Exception e) {}
            }
        }
    }

    private void triggerQuickAdd() {
        WebView wv = getBridge() != null ? getBridge().getWebView() : null;
        if (wv != null) {
            wv.post(() -> wv.evaluateJavascript(
                "if(window.quickAddHandler)window.quickAddHandler()", null));
        }
    }

    private void registerJsBridge() {
        WebView wv = getBridge() != null ? getBridge().getWebView() : null;
        if (wv != null) {
            wv.addJavascriptInterface(new AppBridge(), "NativeBridge");
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHAN_ID, "快捷记账", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("灵动岛快捷记账条");
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
    }

    class AppBridge {
        @JavascriptInterface
        public void start() { runOnUiThread(() -> doStart()); }
        @JavascriptInterface
        public void stop() { runOnUiThread(() -> doStop()); }
        @JavascriptInterface
        public void openNotificationSettings() {
            runOnUiThread(() -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }
        @JavascriptInterface
        public void openOverlaySettings() {
            runOnUiThread(() -> {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivity(i);
            });
        }
        @JavascriptInterface
        public void openAccessibilitySettings() {
            runOnUiThread(() -> startActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }
        @JavascriptInterface
        public boolean hasOverlayPermission() {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(MainActivity.this);
        }
        @JavascriptInterface
        public String getDebugLog() {
            try {
                return getSharedPreferences("ledger_state", Context.MODE_PRIVATE)
                    .getString("debug_log", "暂无日志");
            } catch (Exception e) { return "读取失败"; }
        }
        @JavascriptInterface
        public boolean getListenAll() {
            try {
                return getSharedPreferences("ledger_state", Context.MODE_PRIVATE)
                    .getBoolean("listen_all", false);
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface
        public void toggleListenAll() {
            try {
                SharedPreferences p = getSharedPreferences("ledger_state", Context.MODE_PRIVATE);
                boolean cur = p.getBoolean("listen_all", false);
                p.edit().putBoolean("listen_all", !cur).apply();
            } catch (Exception e) {}
        }
    }

    private void doStart() {
        if (mediaSession != null) return;
        mediaSession = new MediaSessionCompat(this, "LedgerQA");
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
        MediaMetadataCompat meta = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "记账宝")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "点击 + 快速记一笔")
            .build();
        mediaSession.setMetadata(meta);
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_STOP)
            .addCustomAction("add_action", "记一笔", R.drawable.ic_add)
            .build();
        mediaSession.setPlaybackState(state);
        Intent open = new Intent(this, MainActivity.class);
        int pif = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) pif |= PendingIntent.FLAG_MUTABLE;
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, open, pif);
        Intent add = new Intent(this, MainActivity.class);
        add.setAction("com.ledger.QUICK_ADD");
        PendingIntent piAdd = PendingIntent.getActivity(this, 1, add, pif);
        Notification notif = new NotificationCompat.Builder(this, CHAN_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("记账宝")
            .setContentText("点击 + 快速记一笔")
            .setContentIntent(piOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF4CAF50)
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0))
            .addAction(new NotificationCompat.Action(R.drawable.ic_add, "记一笔", piAdd))
            .build();
        nm.notify(NOTIFY_ID, notif);
    }

    private void doStop() {
        if (mediaSession != null) { mediaSession.release(); mediaSession = null; }
        nm.cancel(NOTIFY_ID);
    }
}
