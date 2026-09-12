<template>
  <section class="identity-panel">
    <h2>欢迎回来</h2>
    <p>使用已验证的邮箱，继续你的旅行。</p>
    <form class="identity-form" @submit.prevent="login">
      <label>邮箱<input v-model="email" type="email" autocomplete="username" required /></label>
      <label>密码<input v-model="password" type="password" autocomplete="current-password" required /></label>
      <p v-if="error" class="identity-error" role="alert">{{ error }}</p>
      <button class="identity-button" type="submit" :disabled="pending">{{ pending ? '正在登录…' : '登录' }}</button>
    </form>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { useIdentity, identityErrorMessage } from '../modules/identity/identityContext.js'
import '../modules/identity/identityForms.css'

const { identitySession } = useIdentity()
const emit = defineEmits(['login-success'])
const email = ref('')
const password = ref('')
const pending = ref(false)
const error = ref('')
const login = async () => {
  if (pending.value) return
  pending.value = true
  error.value = ''
  try {
    await identitySession.login({ email: email.value, password: password.value })
    password.value = ''
    emit('login-success')
  } catch (failure) {
    error.value = identityErrorMessage(failure)
  } finally { pending.value = false }
}
</script>
