<template>
    <view class="page">
        <!-- 月份选择 -->
        <view class="month-selector">
            <text class="month-arrow" @click="prevMonth">◀</text>
            <text class="month-text">{{ displayMonth }}</text>
            <text class="month-arrow" @click="nextMonth">▶</text>
        </view>

        <!-- 收支概览 -->
        <view class="summary-card">
            <view class="summary-item">
                <text class="s-label">收入</text>
                <text class="s-value income">+{{ summary.income || 0 }}</text>
            </view>
            <view class="summary-item">
                <text class="s-label">支出</text>
                <text class="s-value expense">-{{ summary.expense || 0 }}</text>
            </view>
            <view class="summary-item">
                <text class="s-label">结余</text>
                <text :class="['s-value', balance >= 0 ? 'income' : 'expense']">
                    {{ balance >= 0 ? '+' : '' }}{{ balance }}
                </text>
            </view>
        </view>

        <!-- 分类饼图(简易) -->
        <view class="chart-card">
            <view class="chart-title">支出分类排行</view>
            <view class="bar-list">
                <view class="bar-item" v-for="(cat, idx) in topCategories" :key="idx">
                    <view class="bar-info">
                        <text class="bar-name">{{ cat.name || '未分类' }}</text>
                        <text class="bar-amount">{{ cat.total }}</text>
                    </view>
                    <view class="bar-track">
                        <view class="bar-fill" :style="{ width: cat.percent + '%', background: barColors[idx % barColors.length] }"></view>
                    </view>
                </view>
                <view v-if="topCategories.length === 0" class="empty-chart">暂无数据</view>
            </view>
        </view>

        <!-- 月度趋势 -->
        <view class="chart-card">
            <view class="chart-title">月度趋势</view>
            <view class="trend-list">
                <view class="trend-row" v-for="item in trend" :key="item.month">
                    <text class="trend-month">{{ item.month }}月</text>
                    <view class="trend-bar-wrap">
                        <view class="trend-income" :style="{ width: trendBarWidth(item.income, 'in') }">{{ item.income }}</view>
                    </view>
                    <view class="trend-bar-wrap">
                        <view class="trend-expense" :style="{ width: trendBarWidth(item.expense, 'ex') }">{{ item.expense }}</view>
                    </view>
                </view>
            </view>
        </view>
    </view>
</template>

<script>
import { api } from '@/api/index.js'

export default {
    data() {
        return {
            year: new Date().getFullYear(),
            month: new Date().getMonth() + 1,
            summary: {},
            categoryStats: [],
            trend: [],
            barColors: ['#F44336','#FF9800','#FFEB3B','#4CAF50','#2196F3','#9C27B0','#00BCD4','#795548','#607D8B','#E91E63']
        }
    },
    computed: {
        displayMonth() { return `${this.year}年 ${this.month}月` },
        balance() {
            return (parseFloat(this.summary.income) - parseFloat(this.summary.expense)).toFixed(2)
        },
        topCategories() {
            const total = this.categoryStats.reduce((s, c) => s + parseFloat(c.total || 0), 0)
            return this.categoryStats.slice(0, 10).map(c => ({
                ...c, percent: total > 0 ? ((parseFloat(c.total) / total) * 100).toFixed(0) : 0
            }))
        }
    },
    mounted() { this.loadStats() },
    methods: {
        monthKey() { return `${this.year}-${String(this.month).padStart(2,'0')}` },
        prevMonth() {
            this.month--
            if (this.month < 1) { this.month = 12; this.year-- }
            this.loadStats()
        },
        nextMonth() {
            this.month++
            if (this.month > 12) { this.month = 1; this.year++ }
            this.loadStats()
        },
        async loadStats() {
            const key = this.monthKey()
            try {
                const [sumRes, catRes, trendRes] = await Promise.all([
                    api.getSummary(key),
                    api.getCategoryStats(key, 2),
                    api.getTrend(this.year)
                ])
                this.summary = sumRes.data || {}
                this.categoryStats = Object.values(catRes.data || {}).sort((a,b) => parseFloat(b.total)-parseFloat(a.total))
                this.trend = trendRes.data || []
            } catch (e) { console.error(e) }
        },
        trendBarWidth(val, type) {
            const max = Math.max(...this.trend.map(t => parseFloat(t.income||0) + parseFloat(t.expense||0)), 1)
            const pct = (parseFloat(val || 0) / max) * 60
            return Math.max(pct, type === 'in' ? 5 : 5) + '%'
        }
    }
}
</script>

<style scoped>
.page { padding: 12px; padding-bottom: 80px; }

.month-selector {
    display: flex; justify-content: center; align-items: center;
    gap: 20px; padding: 16px 0;
}
.month-arrow { font-size: 16px; color: #4CAF50; padding: 4px 8px; }
.month-text { font-size: 16px; font-weight: bold; }

.summary-card {
    display: flex; background: #fff; border-radius: 12px;
    padding: 16px; margin-bottom: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.summary-item { flex: 1; text-align: center; }
.s-label { font-size: 12px; color: #999; display: block; margin-bottom: 4px; }
.s-value { font-size: 16px; font-weight: bold; }
.income { color: #4CAF50; }
.expense { color: #F44336; }

.chart-card {
    background: #fff; border-radius: 12px; padding: 16px;
    margin-bottom: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.chart-title { font-size: 15px; font-weight: bold; margin-bottom: 12px; }

.bar-item { margin-bottom: 10px; }
.bar-info { display: flex; justify-content: space-between; margin-bottom: 4px; font-size: 12px; }
.bar-name { color: #333; }
.bar-amount { color: #999; }
.bar-track { height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }

.empty-chart { text-align: center; color: #999; padding: 20px; }

.trend-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 11px; }
.trend-month { width: 30px; color: #666; }
.trend-bar-wrap { flex: 1; height: 20px; display: flex; align-items: center; }
.trend-income { background: #4CAF50; color: #fff; height: 18px; border-radius: 4px; padding: 0 4px; white-space: nowrap; min-width: 30px; }
.trend-expense { background: #F44336; color: #fff; height: 18px; border-radius: 4px; padding: 0 4px; white-space: nowrap; min-width: 30px; }
</style>
