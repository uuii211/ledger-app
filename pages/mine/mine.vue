<template>
    <view class="page">
        <!-- 数据管理 -->
        <view class="section">
            <view class="section-title">数据管理</view>
            <view class="card">
                <view class="card-row" @click="confirmClear">
                    <text class="row-text">清除所有数据</text>
                    <text class="row-arrow">›</text>
                </view>
            </view>
        </view>

        <!-- 通知设置 -->
        <view class="section">
            <view class="section-title">通知检测</view>
            <view class="card">
                <view class="card-row">
                    <text class="row-text">通知监听服务</text>
                    <text class="row-hint" @click="openNotificationSettings">
                        {{ notificationEnabled ? '已开启' : '去开启' }}
                    </text>
                </view>
                <view class="card-row">
                    <text class="row-text">短信监听</text>
                    <text class="row-hint" @click="openSmsSettings">
                        {{ smsEnabled ? '已开启' : '去开启' }}
                    </text>
                </view>
            </view>
        </view>

        <!-- 关于 -->
        <view class="section">
            <view class="section-title">关于</view>
            <view class="card">
                <view class="card-row">
                    <text class="row-text">版本</text>
                    <text class="row-value">1.0.0</text>
                </view>
                <view class="card-row">
                    <text class="row-text">后端状态</text>
                    <text class="row-value" :style="{ color: serverOk ? '#4CAF50' : '#F44336' }">
                        {{ serverOk ? '正常' : '异常' }}
                    </text>
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
            notificationEnabled: false,
            smsEnabled: false,
            serverOk: false
        }
    },
    mounted() {
        this.checkServer()
        // 检查权限状态
        // #ifdef APP-PLUS
        // Notification listener check requires custom plugin
        // #endif
    },
    methods: {
        async checkServer() {
            try {
                await api.getSources()
                this.serverOk = true
            } catch (e) { this.serverOk = false }
        },
        confirmClear() {
            uni.showModal({
                title: '危险操作',
                content: '确定要清除所有记账数据吗？此操作不可恢复！',
                confirmText: '确定清除',
                confirmColor: '#F44336',
                success: (res) => {
                    if (res.confirm) {
                        api.clearTransactions().then(() => {
                            uni.showToast({ title: '已清除', icon: 'success' })
                        })
                    }
                }
            })
        },
        openNotificationSettings() {
            // #ifdef APP-PLUS
            plus.android.invoke('android.provider.Settings',
                'ACTION_NOTIFICATION_LISTENER_SETTINGS')
            // #endif
            uni.showToast({ title: '请在系统设置中开启通知权限', icon: 'none' })
        },
        openSmsSettings() {
            uni.showToast({ title: '请在系统设置中开启短信权限', icon: 'none' })
        }
    }
}
</script>

<style scoped>
.page { padding: 12px; padding-bottom: 80px; }

.section { margin-bottom: 20px; }
.section-title { font-size: 12px; color: #999; padding: 8px 4px; }

.card { background: #fff; border-radius: 12px; overflow: hidden; }
.card-row {
    display: flex; justify-content: space-between; align-items: center;
    padding: 14px 16px; border-bottom: 1px solid #f5f5f5;
}
.row-text { font-size: 14px; color: #333; }
.row-arrow { font-size: 18px; color: #ccc; }
.row-value { font-size: 13px; color: #999; }
.row-hint { font-size: 13px; color: #4CAF50; }
</style>
