import { defineStore } from 'pinia'

export const useSearchStore = defineStore('search', {
    state: () => ({
        keyword: '' // 当前搜索关键字
    }),
    actions: {
        setKeyword(val) {
            this.keyword = val
        }
    }
})