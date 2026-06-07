<script setup lang="ts">
const items = [
  { to: '/', label: '대시보드', icon: '◉' },
  { to: '/blocklist', label: '블록리스트', icon: '⛔' },
  { to: '/events', label: '이벤트 로그', icon: '≡' },
  { to: '/policy', label: '정책 설정', icon: '⚙' },
]
</script>

<template>
  <aside class="w-64 bg-slate-900 text-slate-100 min-h-screen flex flex-col">
    <div class="px-6 py-5 border-b border-slate-700">
      <h1 class="text-xl font-bold">VPN Lab</h1>
      <p class="text-xs text-slate-400 mt-1">사내 보안 콘솔</p>
    </div>
    <nav class="flex-1 p-4 space-y-1">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="block px-3 py-2 rounded hover:bg-slate-800 transition"
        active-class="bg-slate-800 font-semibold"
        :exact-active-class="item.to === '/' ? 'bg-slate-800 font-semibold' : ''"
      >
        <span class="mr-2">{{ item.icon }}</span>{{ item.label }}
      </RouterLink>
    </nav>
    <div class="p-4 text-xs text-slate-500 border-t border-slate-800">
      Spring Boot 3.5 · MongoDB 8
    </div>
  </aside>
</template>
