<template>
  <main class="identity-panel identity-route">
    <h1>验证邮箱</h1>
    <p v-if="pending" role="status">正在验证邮件链接…</p>
    <p v-else-if="verified" class="identity-success" role="status">邮箱验证成功，现在可以使用邮箱和密码登录。</p>
    <p v-else class="identity-error" role="alert">{{ error }}</p>
    <RouterLink to="/CardList">返回发现页并登录</RouterLink>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useIdentity } from './identityContext.js'
import { useIdentityLink } from './useIdentityLink.js'
import './identityForms.css'

const token = useIdentityLink()
const { identityHttp } = useIdentity()
const pending = ref(Boolean(token))
const verified = ref(false)
const error = ref('验证链接缺失，请从邮件重新打开。')
onMounted(async () => {
  if (!token) return
  try { await identityHttp.verifyEmail(token); verified.value = true }
  catch { error.value = '验证链接已失效、已使用或暂时无法验证，请重新申请或稍后重试。' }
  finally { pending.value = false }
})
</script>
