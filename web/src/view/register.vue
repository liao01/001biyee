<template>
  <section class="identity-panel">
    <h2>创建旅行账号</h2>
    <p>使用邮箱注册，验证后即可开始规划旅行。</p>
    <p v-if="sent" class="identity-success" role="status">请检查邮箱。如果该地址可以注册，我们已发送验证链接；已有账号可直接登录或找回密码。</p>
    <form v-else class="identity-form" @submit.prevent="register">
      <label>邮箱<input v-model="email" type="email" autocomplete="email" required /></label>
      <label>密码<input v-model="password" name="password" type="password" autocomplete="new-password" required /></label>
      <label>确认密码<input v-model="confirmation" name="confirmation" type="password" autocomplete="new-password" required /></label>
      <p v-if="error" class="identity-error" role="alert">{{ error }}</p>
      <button class="identity-button" type="submit" :disabled="pending">{{ pending ? '正在发送…' : '注册并发送验证邮件' }}</button>
    </form>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { useIdentity, identityErrorMessage } from '../modules/identity/identityContext.js'
import '../modules/identity/identityForms.css'

const { identityHttp } = useIdentity()
const email = ref('')
const password = ref('')
const confirmation = ref('')
const pending = ref(false)
const sent = ref(false)
const error = ref('')
const register = async () => {
  if (pending.value) return
  error.value = ''
  if (password.value !== confirmation.value) { error.value = '两次密码不一致'; return }
  pending.value = true
  try {
    await identityHttp.register({ email: email.value, password: password.value })
    password.value = ''
    confirmation.value = ''
    sent.value = true
  } catch (failure) { error.value = identityErrorMessage(failure) }
  finally { pending.value = false }
}
</script>
