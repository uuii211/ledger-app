<template>
    <view class="island-container" v-if="visible">
        <view class="island" :class="{ expanded: isExpanded }" @click="toggleExpand">
            <!-- 收起状态 -->
            <view class="island-collapsed" v-if="!isExpanded">
                <view class="island-icon">{{ isExpense ? '💸' : '💰' }}</view>
                <view class="island-text">
                    <text class="island-source">{{ data.sourceName }}</text>
                    <text :class="isExpense ? 'amount-expense' : 'amount-income'">
                        {{ isExpense ? '-' : '+' }}¥{{ data.amount }}
                    </text>
                </view>
                <view class="island-arrow">▼</view>
            </view>

            <!-- 展开状态 -->
            <view class="island-expanded" v-if="isExpanded" @click.stop>
                <view class="expanded-header">
                    <text class="expanded-title">确认记账</text>
                    <text class="expanded-close" @click="dismiss">✕</text>
                </view>
                <view class="expanded-body">
                    <view class="expanded-row">
                        <text class="label">来源</text>
                        <text class="value">{{ data.sourceName }}</text>
                    </view>
                    <view class="expanded-row">
                        <text class="label">类型</text>
                        <picker :range="['收入', '支出']" :value="data.type - 1" @change="onTypeChange">
                            <text :class="isExpense ? 'tag-expense' : 'tag-income'">
                                {{ isExpense ? '支出' : '收入' }}
                            </text>
                        </picker>
                    </view>
                    <view class="expanded-row">
                        <text class="label">金额</text>
                        <input class="amount-input" type="digit" v-model="amountStr" placeholder="0.00" />
                    </view>
                    <view class="expanded-row">
                        <text class="label">分类</text>
                        <picker :range="categoryNames" @change="onCategoryChange">
                            <text class="value pickable">{{ categoryNames[categoryIndex] || '选择分类' }}</text>
                        </picker>
                    </view>
                    <view class="expanded-row">
                        <text class="label">日期</text>
                        <picker mode="date" :value="data.transDate" @change="onDateChange">
                            <text class="value pickable">{{ data.transDate }}</text>
                        </picker>
                    </view>
                    <view class="expanded-row">
                        <text class="label">备注</text>
                        <input class="note-input" v-model="data.note" placeholder="添加备注" />
                    </view>
                </view>
                <view class="expanded-actions">
                    <button class="btn-dismiss" @click="dismiss">忽略</button>
                    <button class="btn-confirm" @click="confirm">确认记账</button>
                </view>
            </view>
        </view>
    </view>
</template>

<script>
import { api } from '@/api/index.js'

export default {
    name: 'DynamicIsland',
    data() {
        return {
            visible: false,
            isExpanded: false,
            data: {
                amount: 0,
                type: 2,
                sourceName: '',
                sourceId: null,
                transDate: '',
                note: '',
                categoryId: null
            },
            categories: [],
            categoryIndex: 0,
            amountStr: ''
        }
    },
    computed: {
        isExpense() { return this.data.type === 2 },
        categoryNames() {
            return this.categories.map(c => c.name + (c.type === 1 ? '(收)' : ''))
        },
    },
    methods: {
        async show(parsed) {
            this.data = {
                amount: parsed.amount,
                type: parsed.type || 2,
                sourceName: parsed.sourceName || '',
                sourceId: parsed.sourceId || null,
                transDate: new Date().toISOString().split('T')[0],
                note: parsed.rawText ? parsed.rawText.substring(0, 50) : '',
                categoryId: null
            }
            this.amountStr = String(parsed.amount)
            this.isExpanded = false
            this.visible = true

            // 加载分类
            try {
                const res = await api.getCategories(this.data.type)
                this.categories = res.data || []
                if (this.categories.length) {
                    this.categoryIndex = 0
                    this.data.categoryId = this.categories[0].id
                }
            } catch (e) { /* ignore */ }

            // 匹配来源
            try {
                const res = await api.getSources()
                const sources = res.data || []
                const matched = sources.find(s => s.name === this.data.sourceName || s.name.includes(this.data.sourceName) || this.data.sourceName.includes(s.name))
                if (matched) this.data.sourceId = matched.id
            } catch (e) { /* ignore */ }
        },
        toggleExpand() {
            this.isExpanded = !this.isExpanded
        },
        onTypeChange(e) {
            const type = e.detail.value + 1
            this.data.type = type
            this.categoryIndex = 0
            api.getCategories(type).then(res => {
                this.categories = res.data || []
                if (this.categories.length) {
                    this.categoryIndex = 0
                    this.data.categoryId = this.categories[0].id
                }
            })
        },
        onCategoryChange(e) {
            this.categoryIndex = e.detail.value
            this.data.categoryId = this.categories[this.categoryIndex].id
        },
        onDateChange(e) {
            this.data.transDate = e.detail.value
        },
        async confirm() {
            const amount = parseFloat(this.amountStr) || this.data.amount
            if (amount <= 0) {
                uni.showToast({ title: '请输入有效金额', icon: 'none' })
                return
            }
            try {
                await api.addTransaction({
                    type: this.data.type,
                    amount: amount,
                    categoryId: this.data.categoryId,
                    sourceId: this.data.sourceId,
                    transDate: this.data.transDate,
                    note: this.data.note
                })
                uni.showToast({ title: '记账成功', icon: 'success' })
                this.visible = false
                this.$emit('saved')
            } catch (e) {
                uni.showToast({ title: '保存失败', icon: 'none' })
            }
        },
        dismiss() {
            this.visible = false
            this.isExpanded = false
        }
    }
}
</script>

<style scoped>
.island-container {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    z-index: 9999;
    display: flex;
    justify-content: center;
    padding-top: calc(var(--status-bar-height, 44px) + 8px);
}

.island {
    background: #1a1a1a;
    border-radius: 22px;
    min-width: 260px;
    max-width: 90vw;
    box-shadow: 0 8px 30px rgba(0,0,0,0.3);
    transition: all 0.3s ease;
    overflow: hidden;
}

.island.expanded {
    border-radius: 18px;
    min-width: 320px;
}

.island-collapsed {
    display: flex;
    align-items: center;
    padding: 10px 16px;
    gap: 10px;
}

.island-icon { font-size: 20px; }
.island-text { flex: 1; display: flex; justify-content: space-between; align-items: center; gap: 10px; }

.island-source { color: #ccc; font-size: 13px; }
.amount-expense { color: #ff6b6b; font-weight: bold; font-size: 16px; }
.amount-income { color: #51cf66; font-weight: bold; font-size: 16px; }

.island-arrow { color: #888; font-size: 10px; }

.island-expanded { padding: 0; }
.expanded-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 14px 18px;
    border-bottom: 1px solid #333;
}
.expanded-title { color: #fff; font-size: 15px; font-weight: bold; }
.expanded-close { color: #888; font-size: 18px; padding: 4px; }

.expanded-body { padding: 16px 18px; }
.expanded-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 0;
    border-bottom: 1px solid #2a2a2a;
}
.expanded-row .label { color: #999; font-size: 14px; }
.expanded-row .value { color: #fff; font-size: 14px; }

.tag-expense { color: #ff6b6b; background: rgba(255,107,107,0.15); padding: 2px 8px; border-radius: 4px; }
.tag-income { color: #51cf66; background: rgba(81,207,102,0.15); padding: 2px 8px; border-radius: 4px; }

.pickable { border-bottom: 1px dashed #555; }
.amount-input {
    color: #fff; font-size: 18px; font-weight: bold; text-align: right;
    width: 120px; background: transparent; border: none;
}
.note-input {
    color: #ccc; font-size: 13px; text-align: right;
    width: 160px; background: transparent; border: none;
}

.expanded-actions {
    display: flex;
    gap: 10px;
    padding: 14px 18px;
    border-top: 1px solid #333;
}
.btn-dismiss {
    flex: 1; height: 40px; line-height: 40px; text-align: center;
    background: #333; color: #ccc; border-radius: 20px; font-size: 14px;
}
.btn-confirm {
    flex: 2; height: 40px; line-height: 40px; text-align: center;
    background: #4CAF50; color: #fff; border-radius: 20px; font-size: 14px;
}
</style>
