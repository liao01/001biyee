import { createStore } from 'vuex'

const User = "user";

export default createStore({
    state: {
        user: { ...(SessionStorage.get(User) || {}) }
    },
    mutations: {
        setMember(state, _user) {
            state.user = { ..._user };
            SessionStorage.set(User, _user);
        },
        clearMember(state) {
            state.user = {};
            SessionStorage.remove(User);
        }
    }
})
