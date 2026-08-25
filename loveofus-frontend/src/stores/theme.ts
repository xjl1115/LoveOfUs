import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  // State
  const isDarkMode = ref<boolean>(localStorage.getItem('darkMode') === 'true')

  // Actions
  function toggleDarkMode() {
    isDarkMode.value = !isDarkMode.value
    localStorage.setItem('darkMode', String(isDarkMode.value))
    applyTheme()
  }

  function setDarkMode(value: boolean) {
    isDarkMode.value = value
    localStorage.setItem('darkMode', String(value))
    applyTheme()
  }

  function applyTheme() {
    const html = document.documentElement
    if (isDarkMode.value) {
      html.classList.add('dark-mode')
      html.classList.remove('light-mode')
    } else {
      html.classList.add('light-mode')
      html.classList.remove('dark-mode')
    }
  }

  // 初始化主题
  function initTheme() {
    applyTheme()
  }

  return {
    isDarkMode,
    toggleDarkMode,
    setDarkMode,
    applyTheme,
    initTheme
  }
})
