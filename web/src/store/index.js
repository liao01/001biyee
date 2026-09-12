import { createStore } from 'vuex'

// 一次性兼容清理：旧会员缓存含原始令牌，不再恢复或写入。
try { sessionStorage.removeItem('member') } catch { /* 禁用存储不影响内存会话。 */ }

export default createStore({
    state: {
        member: {}
    },
    mutations: {
        setMember(state, _member) {
            state.member = _member?.id ? { id: String(_member.id), name: _member.name || '' } : {};
        },
        clearMember(state) {
            state.member = {};
        }
    }
})
