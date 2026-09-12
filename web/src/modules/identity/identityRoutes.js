import EmailVerificationResult from './EmailVerificationResult.vue'
import PasswordReset from './PasswordReset.vue'

export const identityRoutes = [
  { path: '/verify-email', component: EmailVerificationResult },
  { path: '/reset-password', component: PasswordReset },
]
