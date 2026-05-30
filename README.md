# 记账宝 - Android 手机记账 App

自动检测微信、支付宝、银行收支通知，一键记账。纯本地存储，无需联网。

## 功能

- **自动检测**：监听微信支付/转账/红包、支付宝收支、各大银行消费/入账通知
- **灵动岛确认**：检测到交易后弹出通知 → 点击 → App 内灵动岛确认记账
- **手动记账**：分类、来源、金额、日期、备注，完整记账功能
- **月度统计**：支出分类排行榜
- **数据本地化**：所有数据存在手机 localStorage，`doClear` 会彻底清除备份
- **常驻通知**：通知栏显示"点击 + 快速记一笔"，随时可记账

## 技术栈

| 层 | 技术 |
|------|------|
| 前端 | Vue 3 (CDN) + localStorage |
| 原生 | Java + Capacitor WebView |
| 通知监听 | `NotificationListenerService` |
| 构建 | Gradle (JDK 21, Android SDK 36) |
| 最低支持 | Android 7.0 (API 24) |

## 项目结构

```
E:\Projects\ledger-app\
├── 记账宝.apk              ← 安装包
├── ledger.html             ← 主前端（Vue3 SPA）
├── android\
│   └── android\app\src\main\
│       ├── AndroidManifest.xml
│       └── java\com\ledger\jzb\
│           ├── MainActivity.java          ← WebView桥接 + Intent处理
│           └── PaymentListenerService.java ← 通知监听 + 解析引擎
```

## 构建

```bash
# 1. 更新 HTML 到 assets
cp E:/Projects/ledger-app/ledger.html \
   E:/Projects/ledger-app/android/android/app/src/main/assets/public/index.html

# 2. 构建 APK
export JAVA_HOME="D:/develop/JDK_21/jdk21.0.11_10"
cd E:/Projects/ledger-app/android/android
echo "sdk.dir=C:/Users/ThinkPad/AppData/Local/Android/Sdk" > local.properties
./gradlew assembleDebug

# 3. 输出
cp app/build/outputs/apk/debug/app-debug.apk E:/Projects/ledger-app/记账宝.apk
```

## 安装

1. 安装 APK 到手机
2. **通知使用权**：设置 → 通知使用权 → 开启"记账宝"（必需，否则无法监听）
3. **悬浮窗权限**：设置 → 悬浮窗 → 开启"记账宝"（推荐）

## 支持检测的 App

| App | 包名 | 可检测 |
|-----|------|--------|
| 微信 | `com.tencent.mm` | 支付、收款、转账、红包 |
| 支付宝 | `com.eg.android.AlipayGphone` | 支付、收款、转账 |
| 工商银行 | `com.icbc` | 消费、存入、扣款 |
| 建设银行 | `com.chinamworld.boc` | 消费、存入、扣款 |
| 农业银行 | `com.android.bankabc` | 消费、存入、扣款 |
| 招商银行 | `cmb.pb` | 消费、存入、扣款 |
| 中国银行 | `com.bocec` | 消费、存入、扣款 |
| 交通银行 | `com.bankcomm.maidanba` | 消费、存入、扣款 |
| 邮储银行 | `com.psbc.mobilebank` | 消费、存入、扣款 |
| 云闪付 | `com.unionpay` | 消费、存入、扣款 |

## 已知问题

- **微信转账金额为 0**：微信转账通知不包含金额，需手动输入
- **广告误识别过滤**：银行促销/复盘/返现券等已被关键字过滤
- **银行摘要通知**：`[数字条]动账通知` 格式的摘要已被正则过滤

## 隐私

- 所有数据仅存手机 `localStorage`
- 通知监听仅在本地处理，不上传任何数据
- 仅解析含金额关键词的通知，普通聊天/广告不处理
- 清除数据时备份一并删除
