<template>
  <main class="identity-panel identity-route">
    <h1>设置新密码</h1>
    <p v-if="done" class="identity-success" role="status">密码已更新，请使用新密码重新登录。</p>
    <p v-else-if="!token" class="identity-error" role="alert">重置链接缺失，请从邮件重新打开。</p>
    <form v-else class="identity-form" @submit.prevent="reset">
      <label>新密码<input v-model="password" name="password" type="password" autocomplete="new-password" required /></label>
      <label>确认新密码<input v-model="confirmation" name="confirmation" type="password" autocomplete="new-password" required /></label>
      <p v-if="error" class="identity-error" role="alert">{{ error }}</p>
      <button class="identity-button" type="submit" :disabled="pending">{{ pending ? '正在更新…' : '更新密码' }}</button>
    </form>
    <p><RouterLink to="/CardList">返回发现页并登录</RouterLink></p>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useIdentity, identityErrorMessage } from './identityContext.js'
import { useIdentityLink } from './useIdentityLink.js'
import './identityForms.css'

const token = useIdentityLink()
const { identityHttp } = useIdentity()
const password = ref('')
const confirmation = ref('')
const pending = ref(false)
const done = ref(false)
const error = ref('')
const reset = async () => {
  if (pending.value || !token) return
  error.value = ''
  if (password.value !== confirmation.value) { error.value = '两次密码不一致'; return }
  pending.value = true
  try {
    await identityHttp.resetPassword(token, password.value)
    password.value = ''
    confirmation.value = ''
    done.value = true
  } catch (failure) { error.value = identityErrorMessage(failure) }
  finally { pending.value = false }
}
</script>
