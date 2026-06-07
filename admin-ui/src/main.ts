import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Dashboard from './pages/Dashboard.vue'
import Blocklist from './pages/Blocklist.vue'
import DiskEvents from './pages/DiskEvents.vue'
import Policy from './pages/Policy.vue'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: Dashboard },
    { path: '/blocklist', name: 'blocklist', component: Blocklist },
    { path: '/events', name: 'events', component: DiskEvents },
    { path: '/policy', name: 'policy', component: Policy },
  ],
})

createApp(App).use(router).mount('#app')
