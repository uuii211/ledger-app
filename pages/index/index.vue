<template>
    <view class="page">
        <!-- 灵动岛 -->
        <DynamicIsland ref="island" @saved="loadData" />

        <!-- 今日概览卡片 -->
        <view class="summary-card">
            <view class="summary-item">
                <text class="summary-label">收入</text>
                <text class="summary-value income">+{{ summary.income }}</text>
            </view>
            <view class="summary-divider"></view>
            <view class="summary-item">
                <text class="summary-label">支出</text>
                <text class="summary-value expense">-{{ summary.expense }}</text>
            </view>
            <view class="summary-divider"></view>
            <view class="summary-item">
                <text class="summary-label">结余</text>
                <text :class="['summary-value', balance >= 0 ? 'income' : 'expense']">
                    {{ balance >= 0 ? '+' : '' }}{{ balance.toFixed(2) }}
                </text>
            </view>
        </view>

        <!-- 操作栏 -->
        <view class="action-bar">
            <button class="btn-scan" @click="checkClipboard">🔍 检测账单</button>
            <button class="btn-add" @click="goAdd">+ 记一笔</button>
        </view>

        <!-- 交易列表 -->
        <view class="list-container">
            <view class="date-group" v-for="group in groupedList" :key="group.date">
                <view class="date-header">
                    <text class="date-text">{{ group.date }}</text>
                    <text class="date-total">
                        收入 {{ group.income }} 支出 {{ group.expense }}
                    </text>
                </view>
                <view class="transaction-item" v-for="item in group.items" :key="item.id"
                      @longpress="onLongPress(item)">
                    <view class="item-left">
                        <view class="item-icon" :style="{ background: item.type === 1 ? '#e8f5e9' : '#ffebee' }">
                            {{ getIcon(item) }}
                        </view>
                        <view class="item-info">
                            <text class="item-category">{{ item.categoryName || '未分类' }}</text>
                            <text class="item-source">{{ item.sourceName || '' }} {{ item.note || '' }}</text>
                        </view>
                    </view>
                    <text :class="item.type === 1 ? 'item-amount income' : 'item-amount expense'">
                        {{ item.type === 1 ? '+' : '-' }}{{ item.amount }}
                    </text>
                </view>
            </view>
            <view v-if="list.length === 0" class="empty">
                <text class="empty-icon">📝</text>
                <text class="empty-text">暂无记录，点击下方按钮开始记账</text>
            </view>
        </view>

        <!-- 浮动添加按钮 -->
        <view class="fab" @click="goAdd">
            <text class="fab-text">+</text>
        </view>
    </view>
</template>

<script>
import { api } from '@/api/index.js'
import { parseTransaction } from '@/utils/parser.js'
import DynamicIsland from '@/components/DynamicIsland.vue'

export default {
    components: { DynamicIsland },
    data() {
        return {
            list: [],
            summary: { income: '0', expense: '0' },
            currentMonth: ''
        }
    },
    computed: {
        balance() {
            return parseFloat(this.summary.income || 0) - parseFloat(this.summary.expense || 0)
        },
        groupedList() {
            const groups = {}
            this.list.forEach(item => {
                const date = item.transDate
                if (!groups[date]) groups[date] = { date, items: [], income: 0, expense: 0 }
                groups[date].items.push(item)
                if (item.type === 1) groups[date].income += parseFloat(item.amount)
                else groups[date].expense += parseFloat(item.amount)
            })
            return Object.values(groups)
        }
    },
    onShow() {
        this.loadData()
        // 检测剪贴板
        setTimeout(() => this.checkClipboard(), 800)
    },
    onPullDownRefresh() {
        this.loadData().then(() => uni.stopPullDownRefresh())
    },
    methods: {
        async loadData() {
            const now = new Date()
            this.currentMonth = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}`
            try {
                const [txRes, sumRes] = await Promise.all([
                    api.getTransactions({
                        pageNum: 1,
                        pageSize: 200,
                        startDate: this.currentMonth + '-01',
                        endDate: new Date(now.getFullYear(), now.getMonth()+1, 0).toISOString().split('T')[0]
                    }),
                    api.getSummary(this.currentMonth)
                ])
                this.list = txRes.rows || []
                if (sumRes.data) {
                    this.summary = {
                        income: sumRes.data.income || 0,
                        expense: sumRes.data.expense || 0
                    }
                }
            } catch (e) {
                console.error('加载失败', e)
            }
        },
        async checkClipboard() {
            try {
                const res = await uni.getClipboardData()
                const text = res.data
                if (text) {
                    const parsed = parseTransaction(text)
                    if (parsed && parsed.confidence === 'high') {
                        this.$refs.island.show(parsed)
                    } else if (parsed) {
                        // 中低置信度也显示但让用户确认
                        this.$refs.island.show(parsed)
                    }
                }
            } catch (e) { /* clipboard read may fail */ }
        },
        getIcon(item) {
            const icons = {
                '餐饮': '🍽', '交通': '🚗', '购物': '🛒', '娱乐': '🎮',
                '住房': '🏠', '医疗': '💊', '教育': '📚', '人情': '🎁',
                '日用': '🧴', '工资': '💰', '理财': '📈', '兼职': '💼',
                '退款': '↩', '其他': '📌'
            }
            return icons[item.categoryName] || (item.type === 1 ? '💰' : '💳')
        },
        goAdd() {
            uni.navigateTo({ url: '/pages/add/add' })
        },
        onLongPress(item) {
            uni.showActionSheet({
                itemList: ['编辑', '删除'],
                success: (res) => {
                    if (res.tapIndex === 0) {
                        uni.navigateTo({ url: `/pages/add/add?id=${item.id}` })
                    } else if (res.tapIndex === 1) {
                        uni.showModal({
                            title: '确认删除',
                            content: '确定要删除这条记录吗？',
                            success: (r) => {
                                if (r.confirm) {
                                    api.deleteTransaction(item.id).then(() => this.loadData())
                                }
                            }
                        })
                    }
                }
            })
        }
    }
}
</script>

<style scoped>
.page { padding-bottom: 100px; }

.summary-card {
    margin: 12px;
    padding: 16px;
    background: #fff;
    border-radius: 12px;
    display: flex;
    box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.summary-item { flex: 1; text-align: center; }
.summary-label { font-size: 12px; color: #999; display: block; margin-bottom: 6px; }
.summary-value { font-size: 18px; font-weight: bold; }
.summary-value.income { color: #4CAF50; }
.summary-value.expense { color: #F44336; }
.summary-divider { width: 1px; background: #eee; }

.action-bar {
    display: flex;
    gap: 10px;
    padding: 0 12px;
    margin-bottom: 12px;
}
.btn-scan {
    flex: 1; height: 40px; line-height: 40px;
    background: #fff; color: #4CAF50; border: 1px solid #4CAF50;
    border-radius: 20px; font-size: 14px; text-align: center;
}
.btn-add {
    flex: 1; height: 40px; line-height: 40px;
    background: #4CAF50; color: #fff;
    border-radius: 20px; font-size: 14px; text-align: center;
}

.list-container { padding: 0 12px; }

.date-group { margin-bottom: 16px; }
.date-header {
    display: flex; justify-content: space-between;
    padding: 8px 0;
}
.date-text { font-size: 14px; font-weight: bold; color: #333; }
.date-total { font-size: 11px; color: #999; }

.transaction-item {
    display: flex; justify-content: space-between; align-items: center;
    background: #fff; padding: 14px; border-radius: 10px;
    margin-bottom: 6px; box-shadow: 0 1px 2px rgba(0,0,0,0.03);
}
.item-left { display: flex; align-items: center; gap: 12px; }
.item-icon {
    width: 40px; height: 40px; border-radius: 10px;
    display: flex; align-items: center; justify-content: center; font-size: 18px;
}
.item-category { font-size: 14px; color: #333; display: block; }
.item-source { font-size: 11px; color: #999; margin-top: 2px; display: block; }
.item-amount { font-size: 16px; font-weight: bold; }
.item-amount.income { color: #4CAF50; }
.item-amount.expense { color: #F44336; }

.empty { text-align: center; padding: 60px 0; }
.empty-icon { font-size: 40px; display: block; margin-bottom: 12px; }
.empty-text { color: #999; font-size: 14px; }

.fab {
    position: fixed; bottom: 80px; right: 20px;
    width: 56px; height: 56px; border-radius: 28px;
    background: #4CAF50; box-shadow: 0 4px 12px rgba(76,175,80,0.4);
    display: flex; align-items: center; justify-content: center;
}
.fab-text { color: #fff; font-size: 28px; }
</style>
