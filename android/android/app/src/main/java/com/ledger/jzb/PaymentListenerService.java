package com.ledger.jzb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentListenerService extends NotificationListenerService {

    private static final String CHAN_ID = "payment_island";
    private static final String PREFS = "ledger_state";
    private NotificationManager nm;
    private SharedPreferences prefs;

    public static final List<PendingTx> pendingList = new ArrayList<>();
    public static volatile PendingTx lastTx;

    public static class PendingTx {
        public String source; public double amount; public int type; public String raw;
        public PendingTx(String s, double a, int t, String r) { source=s; amount=a; type=t; raw=r; }
    }

    private static final String[] WATCH = {
        "com.tencent.mm", "com.eg.android.AlipayGphone",
        "com.icbc", "com.chinamworld.boc", "com.android.bankabc",
        "cmb.pb", "com.bankcomm.maidanba", "com.psbc.mobilebank",
        "com.bocec", "cn.com.spdb.mobilebank.per", "com.cmbc.mbank",
        "com.cebbank.mobile.cemb", "com.hxb.mobile.bank", "com.cib.finance",
        "com.unionpay", "cn.gov.pbc.dcep",
    };

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHAN_ID, "支付检测", NotificationManager.IMPORTANCE_HIGH);
            ch.setShowBadge(false); ch.enableVibration(true); nm.createNotificationChannel(ch);
        }
        appendLog("服务已启动");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        appendLog("监听已连接 · 全局监听=" + (prefs.getBoolean("listen_all", false) ? "开" : "关"));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null) return;
            String pkg = sbn.getPackageName(); if (pkg == null) return;
            if (getPackageName().equals(pkg)) return;

            boolean watch = prefs.getBoolean("listen_all", false);
            if (!watch) { for (String w : WATCH) if (pkg.equals(w)) { watch = true; break; } }
            if (!watch) return;

            Bundle extras = sbn.getNotification().extras;
            StringBuilder sb = new StringBuilder();
            CharSequence t = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence x = extras.getCharSequence(Notification.EXTRA_TEXT);
            CharSequence s = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
            CharSequence b = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (t != null) sb.append(t).append(" ");
            if (x != null) sb.append(x).append(" ");
            if (s != null) sb.append(s).append(" ");
            if (b != null) sb.append(b).append(" ");
            if (sbn.getNotification().tickerText != null) sb.append(sbn.getNotification().tickerText).append(" ");
            String text = sb.toString().trim();
            if (text.isEmpty()) return;

            appendLog(pkg + " | " + text);

            PendingTx tx = parseTx(text, pkg);
            if (tx == null) { appendLog("  -> 未匹配"); return; }

            appendLog("  -> 匹配! " + tx.source + " ¥" + String.format("%.2f", tx.amount));

            synchronized (pendingList) {
                String key = tx.source + "_" + tx.amount + "_" + tx.type;
                for (PendingTx p : pendingList) {
                    if ((p.source + "_" + p.amount + "_" + p.type).equals(key)) { appendLog("  -> 去重跳过"); return; }
                }
                pendingList.add(tx); lastTx = tx;
                if (pendingList.size() > 20) pendingList.remove(0);
            }

            postNotify(tx);
        } catch (Exception e) {
            appendLog("错误: " + e.getMessage());
        }
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {}

    private PendingTx parseTx(String text, String pkg) {
        text = text.replace("：", ":").replace("．", ".");
        boolean isWx = "com.tencent.mm".equals(pkg);
        boolean isAli = "com.eg.android.AlipayGphone".equals(pkg);

        if (isWx) {
            PendingTx r = match(text, "(?:微信支付|已支付).*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信支付", 2);
            if (r != null) return r;
            r = match(text, "(?:收款|收钱).*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信收款", 1);
            if (r != null) return r;
            r = match(text, "红包.*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信红包", 1);
            if (r != null) return r;
            if (text.contains("红包")) {
                r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信红包", 1);
                return r != null ? r : new PendingTx("微信红包", 0, 1, text);
            }
            if (text.contains("转账") && text.contains("请收款")) {
                r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信转账", 1);
                return r != null ? r : new PendingTx("微信转账", 0, 1, text);
            }
            if (text.contains("转账")) {
                r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信转账", 2);
                return r != null ? r : new PendingTx("微信转账", 0, 2, text);
            }
            r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信支付", 2);
            if (r != null) return r;
        }

        if (isAli) {
            PendingTx r = match(text, "(?:支付|付款|消费|支出|扣款).*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", "支付宝", 2);
            if (r != null) return r;
            r = match(text, "(?:提现|收款|转账.*?(?:收到|到账)|退款|收入|转入).*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", "支付宝", 1);
            if (r != null) return r;
            r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "支付宝", 2);
            if (r != null) return r;
        }

        String bank = "银行卡";
        if (text.contains("招商")) bank = "招商银行";
        else if (text.contains("工商")) bank = "工商银行";
        else if (text.contains("建设")) bank = "建设银行";
        else if (text.contains("农业")) bank = "农业银行";
        else if (text.contains("中国银行")) bank = "中国银行";
        else if (text.contains("交通")) bank = "交通银行";
        else if (text.contains("邮储")) bank = "邮储银行";
        else if (text.contains("云闪")) bank = "云闪付";
        else if (text.contains("财付通")) bank = "微信支付";

        PendingTx r = match(text, "(?:消费|支出|扣款|快捷支付|付款).*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", bank, 2);
        if (r != null) return r;
        r = match(text, "(?:消费|支出|扣款|快捷支付|付款).*?(\\d+\\.\\d{2})\\s*元?", bank, 2);
        if (r != null) return r;
        r = match(text, "(?:存入|入账|转入|收款|退款|收入|到账|人民币).*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", bank, 1);
        if (r != null) return r;
        r = match(text, "(?:存入|入账|转入|收款|退款|收入|到账|人民币).*?(\\d+\\.\\d{2})\\s*元?", bank, 1);
        if (r != null) return r;
        r = match(text, "[¥￥]\\s*(\\d+\\.\\d{1,2})", bank, 2);
        if (r != null) return r;
        try {
            Matcher ma = Pattern.compile("(\\d+\\.\\d{2})").matcher(text);
            while (ma.find()) {
                String ns = ma.group(1);
                double d = Double.parseDouble(ns);
                if (d > 0 && d < 100000 && ns.indexOf('.') > 0 && ns.substring(0, ns.indexOf('.')).length() <= 5) {
                    int tp = (text.contains("存入") || text.contains("入账") || text.contains("收款") || text.contains("人民币")) ? 1 : 2;
                    return new PendingTx(bank, d, tp, text);
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private PendingTx match(String text, String regex, String source, int type) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) {
                double amount = Double.parseDouble(m.group(1));
                if (amount > 0 && amount < 100000) return new PendingTx(source, amount, type, text);
            }
        } catch (Exception e) {}
        return null;
    }

    private void postNotify(PendingTx tx) {
        String emoji = tx.type == 1 ? "💰" : "💸";
        String label = tx.type == 1 ? "收入" : "支出";
        String amt = tx.amount > 0 ? "¥" + String.format("%.2f", tx.amount) : "金额待填";
        int color = tx.type == 1 ? 0xFF4CAF50 : 0xFFF44336;

        Intent i = new Intent(this, MainActivity.class);
        i.setAction("com.ledger.CONFIRM_TX");
        i.putExtra("source", tx.source); i.putExtra("amount", tx.amount); i.putExtra("type", tx.type); i.putExtra("raw", tx.raw);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pif = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) pif |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, (int)(System.nanoTime() % 100000), i, pif);

        Notification n = new NotificationCompat.Builder(this, CHAN_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(emoji + " " + tx.source + " " + label)
            .setContentText(amt + " — 点击记账")
            .setContentIntent(pi).setAutoCancel(true).setColor(color)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .build();
        nm.notify((int)(System.nanoTime() % 100000), n);
    }

    private void appendLog(String msg) {
        try {
            String current = prefs.getString("debug_log", "");
            String ts = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String line = ts + " " + msg;
            String updated = line + "\n" + current;
            if (updated.length() > 5000) updated = updated.substring(0, 5000);
            prefs.edit().putString("debug_log", updated).apply();
        } catch (Exception e) {}
    }

    public static String getDebugLog(Context ctx) {
        try {
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("debug_log", "暂无日志");
        } catch (Exception e) { return "读取日志失败"; }
    }

    public static List<PendingTx> drainPending() {
        synchronized (pendingList) {
            List<PendingTx> c = new ArrayList<>(pendingList);
            pendingList.clear();
            return c;
        }
    }
}
