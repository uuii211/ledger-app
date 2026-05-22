// 交易信息解析器 — 从剪贴板/短信/通知中提取金额、来源、类型

const RULES = [
    // 微信支付
    { re: /微信支付.*?[¥￥]?\s*(\d+\.?\d{0,2})\s*元?/, source: '微信支付', type: 2 },
    { re: /向\s*(\S+)\s*付款\s*[¥￥]?\s*(\d+\.?\d{0,2})/, source: '微信支付', type: 2 },
    // 支付宝
    { re: /支付宝.*?支出.*?[¥￥]?\s*(\d+\.?\d{0,2})\s*元?/, source: '支付宝', type: 2 },
    { re: /支付宝.*?转账.*?[¥￥]?\s*(\d+\.?\d{0,2})\s*元?/, source: '支付宝', type: 1 },
    { re: /支付宝.*?收入.*?[¥￥]?\s*(\d+\.?\d{0,2})/, source: '支付宝', type: 1 },
    // 银行短信
    { re: /工商银行.*?(?:消费|支出|扣款).*?(\d+\.?\d{0,2})\s*元/, source: '工商银行', type: 2 },
    { re: /工商银行.*?(?:存入|收入|入账).*?(\d+\.?\d{0,2})\s*元/, source: '工商银行', type: 1 },
    { re: /建设银行.*?(?:消费|支出|扣款).*?(\d+\.?\d{0,2})\s*元/, source: '建设银行', type: 2 },
    { re: /建设银行.*?(?:存入|收入|入账).*?(\d+\.?\d{0,2})\s*元/, source: '建设银行', type: 1 },
    { re: /农业银行.*?(?:消费|支出|扣款).*?(\d+\.?\d{0,2})\s*元/, source: '农业银行', type: 2 },
    { re: /农业银行.*?(?:存入|收入|入账).*?(\d+\.?\d{0,2})\s*元/, source: '农业银行', type: 1 },
    { re: /招商银行.*?(?:消费|支出|扣款).*?(\d+\.?\d{0,2})\s*元/, source: '招商银行', type: 2 },
    { re: /招商银行.*?(?:存入|收入|入账).*?(\d+\.?\d{0,2})\s*元/, source: '招商银行', type: 1 },
    // 通用银行尾号匹配
    { re: /尾号\s*(\d{4}).*?(?:消费|支出|扣款).*?(\d+\.?\d{0,2})\s*元/, source: '银行卡', type: 2, group: 2 },
    { re: /尾号\s*(\d{4}).*?(?:存入|收入|入账).*?(\d+\.?\d{0,2})\s*元/, source: '银行卡', type: 1, group: 2 },
    // 通用金额匹配（兜底）
    { re: /(?:消费|支出|扣款|付款).*?[¥￥]?\s*(\d+\.?\d{0,2})\s*元?/, source: '其他', type: 2 },
    { re: /(?:收入|存入|入账|收款|转账).*?[¥￥]?\s*(\d+\.?\d{0,2})\s*元?/, source: '其他', type: 1 },
    // 纯金额 ¥符号
    { re: /付款[¥￥]\s*(\d+\.?\d{0,2})/, source: '其他', type: 2 },
    { re: /收款[¥￥]\s*(\d+\.?\d{0,2})/, source: '其他', type: 1 },
]

export function parseTransaction(text) {
    if (!text || typeof text !== 'string') return null

    for (const rule of RULES) {
        const match = text.match(rule.re)
        if (match) {
            const amountStr = match[rule.group || 1]
            const amount = parseFloat(amountStr)
            if (isNaN(amount) || amount <= 0) continue

            return {
                amount: amount,
                type: rule.type,
                sourceName: rule.source,
                rawText: text.substring(0, 100),
                confidence: rule.source !== '其他' && rule.source !== '银行卡' ? 'high' : 'medium'
            }
        }
    }
    return null
}
