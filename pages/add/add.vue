<template>
    <view class="page">
        <!-- 类型切换 -->
        <view class="type-switch">
            <view :class="['type-btn', form.type === 2 ? 'active-expense' : '']" @click="setType(2)">支出</view>
            <view :class="['type-btn', form.type === 1 ? 'active-income' : '']" @click="setType(1)">收入</view>
        </view>

        <!-- 金额输入 -->
        <view class="amount-section">
            <text class="currency">¥</text>
            <input class="amount-input" type="digit" v-model="amountStr" placeholder="0.00"
                   :focus="true" @blur="formatAmount" />
        </view>

        <!-- 表单 -->
        <view class="form-section">
            <view class="form-row" @click="showPicker('category')">
                <text class="form-label">分类</text>
                <text class="form-value">{{ selectedCategoryName || '选择分类' }}</text>
                <text class="form-arrow">›</text>
            </view>
            <view class="form-row" @click="showPicker('source')">
                <text class="form-label">来源</text>
                <text class="form-value">{{ selectedSourceName || '选择来源' }}</text>
                <text class="form-arrow">›</text>
            </view>
            <view class="form-row">
                <text class="form-label">日期</text>
                <picker mode="date" :value="form.transDate" @change="onDateChange">
                    <text class="form-value pickable">{{ form.transDate }}</text>
                </picker>
            </view>
            <view class="form-row">
                <text class="form-label">备注</text>
                <input class="note-input" v-model="form.note" placeholder="添加备注" />
            </view>
        </view>

        <!-- 保存按钮 -->
        <view class="save-section">
            <button class="btn-save" :class="form.type === 1 ? 'income-bg' : 'expense-bg'" @click="save">
                {{ isEdit ? '修改' : '记一笔' }}
            </button>
        </view>

        <!-- 分类选择器弹窗 -->
        <view class="picker-popup" v-if="pickType">
            <view class="picker-mask" @click="pickType = null"></view>
            <view class="picker-content">
                <view class="picker-header">
                    <text @click="pickType = null">取消</text>
                    <text class="picker-title">{{ pickType === 'category' ? '选择分类' : '选择来源' }}</text>
                    <text></text>
                </view>
                <scroll-view scroll-y class="picker-list">
                    <view v-if="pickType === 'category'" class="picker-item" v-for="cat in categories"
                          :key="cat.id" @click="selectCategory(cat)">
                        <text>{{ cat.name }}</text>
                        <text v-if="cat.id === form.categoryId" style="color: #4CAF50;">✓</text>
                    </view>
                    <view v-if="pickType === 'source'" class="picker-item" v-for="src in sources"
                          :key="src.id" @click="selectSource(src)">
                        <text>{{ src.name }}</text>
                        <text v-if="src.id === form.sourceId" style="color: #4CAF50;">✓</text>
                    </view>
                </scroll-view>
            </view>
        </view>
    </view>
</template>

<script>
import { api } from '@/api/index.js'

export default {
    data() {
        return {
            isEdit: false,
            editId: null,
            form: { type: 2, amount: 0, categoryId: null, sourceId: null, transDate: '', note: '' },
            amountStr: '',
            categories: [],
            sources: [],
            pickType: null
        }
    },
    computed: {
        selectedCategoryName() {
            const c = this.categories.find(c => c.id === this.form.categoryId)
            return c ? c.name : ''
        },
        selectedSourceName() {
            const s = this.sources.find(s => s.id === this.form.sourceId)
            return s ? s.name : ''
        }
    },
    onLoad(options) {
        this.form.transDate = new Date().toISOString().split('T')[0]
        this.loadData()

        if (options.id) {
            this.isEdit = true
            this.editId = options.id
            this.loadRecord(options.id)
        }
    },
    methods: {
        async loadData() {
            const [catRes, srcRes] = await Promise.all([
                api.getCategories(this.form.type),
                api.getSources()
            ])
            this.categories = catRes.data || []
            this.sources = srcRes.data || []
        },
        async loadRecord(id) {
            try {
                const res = await api.getTransactions({ pageNum: 1, pageSize: 1 })
                // Simplified: fetch all and filter
            } catch (e) {}
        },
        setType(type) {
            this.form.type = type
            this.form.categoryId = null
            api.getCategories(type).then(res => {
                this.categories = res.data || []
            })
        },
        showPicker(type) { this.pickType = type },
        selectCategory(cat) { this.form.categoryId = cat.id; this.pickType = null },
        selectSource(src) { this.form.sourceId = src.id; this.pickType = null },
        onDateChange(e) { this.form.transDate = e.detail.value },
        formatAmount() {
            const v = parseFloat(this.amountStr)
            if (!isNaN(v)) this.amountStr = v.toFixed(2)
        },
        async save() {
            const amount = parseFloat(this.amountStr)
            if (isNaN(amount) || amount <= 0) {
                uni.showToast({ title: '请输入有效金额', icon: 'none' })
                return
            }
            const data = { ...this.form, amount }

            try {
                if (this.isEdit) {
                    await api.updateTransaction(this.editId, data)
                } else {
                    await api.addTransaction(data)
                }
                uni.showToast({ title: this.isEdit ? '修改成功' : '记账成功', icon: 'success' })
                setTimeout(() => uni.navigateBack(), 500)
            } catch (e) {
                uni.showToast({ title: '保存失败', icon: 'none' })
            }
        }
    }
}
</script>

<style scoped>
.page { padding: 16px; min-height: 100vh; }

.type-switch {
    display: flex; gap: 12px; margin-bottom: 20px;
}
.type-btn {
    flex: 1; text-align: center; padding: 10px; border-radius: 8px;
    background: #f0f0f0; color: #666; font-size: 15px; transition: all 0.2s;
}
.active-expense { background: #F44336; color: #fff; }
.active-income { background: #4CAF50; color: #fff; }

.amount-section {
    display: flex; align-items: center; justify-content: center;
    padding: 20px 0 30px;
}
.currency { font-size: 28px; color: #333; font-weight: bold; margin-right: 4px; }
.amount-input {
    font-size: 42px; font-weight: bold; color: #333;
    width: 200px; text-align: left;
}

.form-section { background: #fff; border-radius: 12px; overflow: hidden; }
.form-row {
    display: flex; justify-content: space-between; align-items: center;
    padding: 14px 16px; border-bottom: 1px solid #f0f0f0;
}
.form-label { font-size: 14px; color: #333; }
.form-value { font-size: 14px; color: #999; }
.form-arrow { font-size: 18px; color: #ccc; }
.pickable { border-bottom: 1px dashed #ddd; }
.note-input { font-size: 14px; color: #333; text-align: right; flex: 1; }

.save-section { padding: 30px 0; }
.btn-save {
    height: 48px; line-height: 48px; border-radius: 24px;
    color: #fff; font-size: 16px; font-weight: bold; text-align: center;
}
.expense-bg { background: #F44336; }
.income-bg { background: #4CAF50; }

.picker-popup { position: fixed; inset: 0; z-index: 999; }
.picker-mask { position: absolute; inset: 0; background: rgba(0,0,0,0.4); }
.picker-content {
    position: absolute; bottom: 0; left: 0; right: 0;
    background: #fff; border-radius: 16px 16px 0 0; max-height: 60vh;
}
.picker-header {
    display: flex; justify-content: space-between; padding: 14px 16px;
    border-bottom: 1px solid #eee; font-size: 14px;
}
.picker-title { font-weight: bold; }
.picker-list { max-height: 50vh; }
.picker-item {
    display: flex; justify-content: space-between;
    padding: 14px 16px; border-bottom: 1px solid #f5f5f5; font-size: 14px;
}
</style>
