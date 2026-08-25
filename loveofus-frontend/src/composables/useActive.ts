import { ref, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 用户活跃度追踪
 * 监听页面上的用户行为，记录最后活跃时间
 * 用于决定是否需要刷新 Token
 */
export function useActive() {
  const userStore = useUserStore()
  const lastActiveTime = ref(Date.now())

  const ACTIVITY_EVENTS = ['mousedown', 'keydown', 'touchstart', 'scroll', 'click', 'focus']

  function onUserActive() {
    const now = Date.now()
    lastActiveTime.value = now
    userStore.updateLastActiveTime(now)
  }

  function startTracking() {
    ACTIVITY_EVENTS.forEach((event) => {
      window.addEventListener(event, onUserActive, { passive: true })
    })
  }

  function stopTracking() {
    ACTIVITY_EVENTS.forEach((event) => {
      window.removeEventListener(event, onUserActive)
    })
  }

  onMounted(() => startTracking())
  onUnmounted(() => stopTracking())

  return {
    lastActiveTime
  }
}
