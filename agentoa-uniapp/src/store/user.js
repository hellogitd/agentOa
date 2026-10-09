import { reactive } from 'vue'
import { getUser, setUser, saveSession, clearAuth } from '@/utils/auth'
import * as authApi from '@/api/auth'
import { connectRealtime, disconnectRealtime } from '@/utils/realtime'

const state = reactive({
  user: getUser(),
  loaded: false
})

export function useUserStore() {
  async function loadProfile(force) {
    if (state.loaded && !force) return state.user
    const user = await authApi.profile()
    state.user = user
    setUser(user)
    state.loaded = true
    return user
  }

  function applyLogin(payload) {
    saveSession(payload)
    state.user = payload.user || null
    state.loaded = true
    connectRealtime()
  }

  function reset() {
    disconnectRealtime()
    clearAuth()
    state.user = null
    state.loaded = false
  }

  function hasPermission(perm) {
    const perms = (state.user && state.user.permissions) || []
    return perms.includes('*:*:*') || perms.includes(perm)
  }

  return { state, loadProfile, applyLogin, reset, hasPermission }
}
