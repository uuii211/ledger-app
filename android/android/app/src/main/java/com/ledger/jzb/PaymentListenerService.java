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
    private static final String CHAN_FG = "listener_fg";
    private static final String PREFS = "ledger_state";
    private static final int FG_NOTIFY_ID = 3001;
    private NotificationManager nm;
    private SharedPreferences prefs;

    public static volatile boolean isConnected = false;
    public static volatile long lastConnectTime = 0;

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
        "com.yitong.mbank.psbc",
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
            NotificationChannel ch1 = new NotificationChannel(CHAN_ID, "支付检测", NotificationManager.IMPORTANCE_HIGH);
            ch1.setShowBadge(false); ch1.enableVibration(true); nm.createNotificationChannel(ch1);
            NotificationChannel ch2 = new NotificationChannel(CHAN_FG, "监听状态", NotificationManager.IMPORTANCE_MIN);
            ch2.setShowBadge(false); ch2.setSound(null, null);
            nm.createNotificationChannel(ch2);
        }
        appendLog("服务已启动");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isConnected = true;
        lastConnectTime = System.currentTimeMillis();
        startForegroundNotification();
        appendLog("监听已连接 · 监控App数=" + WATCH.length);
        prefs.edit().putLong("last_connect_time", lastConnectTime).putBoolean("was_connected", true).apply();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isConnected = false;
        appendLog("⚠ 监听已断开！请重新授权通知权限");
        try { requestRebind(new android.content.ComponentName(this, PaymentListenerService.class)); } catch (Exception e) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isConnected = false;
        stopForeground(true);
        appendLog("⚠ 服务被销毁");
    }

    private void startForegroundNotification() {
        Notification fg = new NotificationCompat.Builder(this, CHAN_FG)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("记账宝监听中")
            .setContentText("自动识别支付通知")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build();
        startForeground(FG_NOTIFY_ID, fg);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null) return;
            String pkg = sbn.getPackageName(); if (pkg == null) return;
            if (getPackageName().equals(pkg)) return;

            boolean watch = false;
            for (String w : WATCH) if (pkg.equals(w)) { watch = true; break; }
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

            // 只记录可能跟支付相关的通知（含¥或金额数字X.XX或支付关键词），普通聊天不记日志
            boolean maybePayment = text.matches(".*[¥￥].*") ||
                text.matches(".*\\d+\\.\\d{2}.*") ||
                text.contains("支付") || text.contains("收款") || text.contains("转账") ||
                text.contains("[微信红包]") || text.contains("[红包]") || text.contains("消费") || text.contains("存入") ||
                text.contains("入账") || text.contains("退款") || text.contains("扣款") ||
                text.contains("提现") || text.contains("到账") || text.contains("动账") || text.contains("元") ||
                text.contains("支出") || text.contains("收入") || text.contains("付款");

            boolean isWxPkg = "com.tencent.mm".equals(pkg);
            boolean isAliPkg = "com.eg.android.AlipayGphone".equals(pkg);
            PendingTx tx = parseTx(text, pkg);
            if (!maybePayment && tx == null) {
                if (isWxPkg || isAliPkg) appendLog(pkg + " | 已过滤(非支付)");
                return;
            }

            appendLog(pkg + " | " + text);
            if (tx == null) { appendLog("  -> 未匹配"); return; }

            appendLog("  -> 匹配! " + tx.source + " ¥" + String.format("%.2f", tx.amount));

            synchronized (pendingList) {
                String key = tx.raw != null ? tx.raw : (tx.source + "_" + tx.amount + "_" + tx.type);
                for (PendingTx p : pendingList) {
                    String pk = p.raw != null ? p.raw : (p.source + "_" + p.amount + "_" + p.type);
                    if (pk.equals(key)) { appendLog("  -> 去重跳过"); return; }
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
            // 红包：需[微信红包]或[红包]标签或"红包"开头，排除聊天提及
            if (text.contains("[微信红包]") || text.contains("[红包]") || text.trim().startsWith("红包")) {
                r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信红包", 1);
                return r != null ? r : new PendingTx("微信红包", 0, 1, text);
            }
            // 转账判断核心："向XX"=我转给别人(支出)，不含"向"或"向你"=别人转给我(收入)
            // 例: "向建鹏 [转账] 请收款"→支出  "向你转账"→收入
            if (text.contains("转账")) {
                boolean isIncome = !text.contains("向") || text.contains("向你") || text.contains("给你");
                int ttype = isIncome ? 1 : 2;
                r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信转账", ttype);
                return r != null ? r : new PendingTx("微信转账", 0, ttype, text);
            }
            // 零钱提现到账（通常不含金额，amount=0 让用户手动填）
            if (text.contains("零钱提现") || (text.contains("提现") && (text.contains("到账") || text.contains("成功")))) {
                r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信零钱提现", 1);
                return r != null ? r : new PendingTx("微信零钱提现", 0, 1, text);
            }
            r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "微信支付", 2);
            if (r != null) return r;
            return null;
        }

        if (isAli) {
            PendingTx r = match(text, "(?:支付|付款|消费|支出|扣款).*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", "支付宝", 2);
            if (r != null) return r;
            r = match(text, "(?:提现|收款|转账.*?(?:收到|到账)|退款|收入|转入).*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", "支付宝", 1);
            if (r != null) return r;
            r = match(text, "[¥￥]\\s*(\\d+\\.?\\d{1,2})", "支付宝", 2);
            if (r != null) return r;
            return null;
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

        // 优先兜底：文本被截断无金额时直接匹配（如招行通知"快捷支..."）
        if (text.contains("快捷") || text.contains("快捷支")) {
            appendLog("  -> 兜底匹配(截断) " + bank);
            return new PendingTx(bank, 0, 2, text);
        }

        // 关键词含"快捷"以兼容通知栏截断("快捷支...")
        PendingTx r = match(text, "(?:消费|支出|扣款|快捷|支付|付款).*?[¥￥]\\s*(\\d+\\.?\\d{1,2})", bank, 2);
        if (r != null) return r;
        r = match(text, "(?:消费|支出|扣款|快捷|支付|付款).*?(\\d+\\.\\d{2})\\s*元?", bank, 2);
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
                if (d > 0 && d < 10000000 && ns.indexOf('.') > 0 && ns.substring(0, ns.indexOf('.')).length() <= 5) {
                    int tp = (text.contains("存入") || text.contains("入账") || text.contains("收款") || text.contains("人民币")) ? 1 : 2;
                    return new PendingTx(bank, d, tp, text);
                }
            }
        } catch (Exception e) {}
        // 兜底：银行支付通知但金额被截断，amount=0 让用户手动填入
        if (text.contains("快捷") || text.contains("快捷支") || text.contains("财付通") ||
            text.contains("支付") || text.contains("消费") || text.contains("扣款") ||
            text.contains("动账") || text.contains("交易")) {
            return new PendingTx(bank, 0, 2, text);
        }
        if (text.contains("存入") || text.contains("入账") || text.contains("收款") || text.contains("到账")) {
            return new PendingTx(bank, 0, 1, text);
        }
        return null;
    }

    private PendingTx match(String text, String regex, String source, int type) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) {
                double amount = Double.parseDouble(m.group(1));
                if (amount > 0 && amount < 10000000) return new PendingTx(source, amount, type, text);
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

    public static PendingTx popLatest() {
        synchronized (pendingList) {
            if (pendingList.isEmpty()) return null;
            return pendingList.remove(pendingList.size() - 1);
        }
    }
}
