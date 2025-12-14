import { createStore } from 'vuex'

const MEMBER = "member";

export default createStore({
    state: {
        member: { ...(SessionStorage.get(MEMBER) || {}) }
    },
    mutations: {
        setMember(state, _member) {
            state.member = { ..._member };
            SessionStorage.set(MEMBER, _member);
        },
        clearMember(state) {
            state.member = {};
            SessionStorage.remove(MEMBER);
        }
    }
})
