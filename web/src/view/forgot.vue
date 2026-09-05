<template>
  <section class="identity-panel">
    <h2>找回密码</h2>
    <p>输入注册邮箱，我们将通过邮件提供重置链接。</p>
    <p v-if="sent" class="identity-success" role="status">如果该邮箱可用于重置密码，你将收到重置链接。请检查收件箱及垃圾邮件文件夹。</p>
    <form v-else class="identity-form" @submit.prevent="requestReset">
      <label>邮箱<input v-model="email" type="email" autocomplete="email" required /></label>
      <p v-if="error" class="identity-error" role="alert">{{ error }}</p>
      <button class="identity-button" type="submit" :disabled="pending">{{ pending ? '正在发送…' : '发送重置邮件' }}</button>
    </form>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { useIdentity, identityErrorMessage } from '../modules/identity/identityContext.js'
import '../modules/identity/identityForms.css'

const { identityHttp } = useIdentity()
const email = ref('')
const pending = ref(false)
const sent = ref(false)
const error = ref('')
const requestReset = async () => {
  if (pending.value) return
  pending.value = true
  error.value = ''
  try { await identityHttp.requestPasswordReset(email.value); sent.value = true }
  catch (failure) { error.value = identityErrorMessage(failure) }
  finally { pending.value = false }
}
</script>
