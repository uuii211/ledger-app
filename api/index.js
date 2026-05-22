// 后端 API 地址
const BASE_URL = 'https://xiangjianpeng.cloud/ledger-api/api'

function request(url, options = {}) {
    return new Promise((resolve, reject) => {
        uni.request({
            url: BASE_URL + url,
            method: options.method || 'GET',
            data: options.data,
            timeout: 10000,
            success: (res) => {
                if (res.data.code === 200) {
                    resolve(res.data)
                } else {
                    reject(res.data)
                }
            },
            fail: (err) => {
                uni.showToast({ title: '网络错误', icon: 'none' })
                reject(err)
            }
        })
    })
}

export const api = {
    // 账目
    getTransactions(params) {
        const qs = Object.entries(params).filter(([,v]) => v).map(([k,v]) => `${k}=${v}`).join('&')
        return request('/transactions?' + qs)
    },
    addTransaction(data) {
        return request('/transactions', { method: 'POST', data })
    },
    updateTransaction(id, data) {
        return request('/transactions/' + id, { method: 'PUT', data })
    },
    deleteTransaction(id) {
        return request('/transactions/' + id, { method: 'DELETE' })
    },
    clearTransactions() {
        return request('/transactions/clear', { method: 'DELETE' })
    },
    // 统计
    getSummary(month) { return request('/statistics/summary?month=' + month) },
    getCategoryStats(month, type) {
        let url = '/statistics/category?month=' + month
        if (type) url += '&type=' + type
        return request(url)
    },
    getTrend(year) { return request('/statistics/trend?year=' + year) },
    // 基础数据
    getCategories(type) { return request('/categories?type=' + (type || '')) },
    getSources() { return request('/sources') }
}
