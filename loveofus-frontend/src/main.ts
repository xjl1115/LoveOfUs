import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'
import lazyDirective from '@/directives/lazy'
import { initMobileAdapter } from '@/utils/mobileAdapter'

// Vant 基础样式（组件 JS 由 unplugin-vue-components 自动按需加载）
import 'vant/lib/index.css'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import '@/styles/global.scss'

// amfe-flexible
import 'amfe-flexible'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 注册全局懒加载指令
app.directive('lazy', lazyDirective)

// 初始化移动端适配
initMobileAdapter()

// 初始化主题
const themeStore = useThemeStore()
themeStore.initTheme()

app.mount('#app')
