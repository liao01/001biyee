export const createIdentityHttp = (transport) => {
  const post = async (path, body) => {
    const { data } = await transport.post(`/lyw/web/identity/${path}`, body, { withCredentials: true })
    if (!data.success) throw new Error(data.message || '身份请求未成功，请重试')
    return data.content
  }
  return {
    login: ({ email, password }) => post('login', { email, password }),
    register: ({ email, password }) => post('register', { email, password }),
    verifyEmail: (token) => post('verify-email', { token }),
    refresh: () => post('refresh', {}),
    logout: () => post('logout', {}),
    requestPasswordReset: (email) => post('request-password-reset', { email }),
    resetPassword: (token, newPassword) => post('reset-password', { token, newPassword }),
    me: async (accessToken) => {
      const { data } = await transport.get('/lyw/web/identity/me', {
        withCredentials: true, headers: { Authorization: `Bearer ${accessToken}` },
      })
      if (!data.success) throw new Error(data.message || '无法读取当前会员')
      return data.content
    },
  }
}
