<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { diskApi, type DiskEvent } from '../api'

const events = ref<DiskEvent[]>([])
const total = ref(0)
const limit = ref(50)
const filterEvent = ref<string>('')   // 빈 문자열 = 전체
const filterVolume = ref<string>('')
const error = ref<string | null>(null)
const lastUpdate = ref('Never')

const filtered = computed(() => events.value.filter(e =>
  (!filterEvent.value || e.event === filterEvent.value) &&
  (!filterVolume.value || e.volume === filterVolume.value)
))
const allEventTypes = computed(() => [...new Set(events.value.map(e => e.event))].sort())
const allVolumes = computed(() => [...new Set(events.value.map(e => e.volume))].sort())

let timer: number | null = null

async function refresh() {
  try {
    const r = await diskApi.events(limit.value)
    events.value = r.events
    total.value = r.total
    lastUpdate.value = new Date().toLocaleTimeString('ko-KR')
    error.value = null
  } catch (e) {
    error.value = `이벤트 조회 실패: ${(e as Error).message}`
  }
}

onMounted(() => {
  refresh()
  timer = window.setInterval(refresh, 10_000)
})
onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div>
    <div class="mb-6 flex justify-between items-end">
      <h1 class="text-3xl font-bold">디스크 이벤트 로그</h1>
      <span class="text-sm text-gray-500">갱신: {{ lastUpdate }} · 표시 {{ filtered.length }}/{{ events.length }} · 전체 {{ total }}</span>
    </div>

    <div v-if="error" class="bg-red-100 border border-red-400 text-red-700 p-3 rounded mb-3">⚠️ {{ error }}</div>

    <div class="bg-white rounded-lg shadow p-4 mb-4 flex gap-3 flex-wrap items-end">
      <div>
        <label class="text-xs text-gray-500 block mb-1">limit</label>
        <select v-model.number="limit" class="border rounded px-2 py-1" @change="refresh">
          <option :value="10">10</option>
          <option :value="50">50</option>
          <option :value="100">100</option>
        </select>
      </div>
      <div>
        <label class="text-xs text-gray-500 block mb-1">event type</label>
        <select v-model="filterEvent" class="border rounded px-2 py-1">
          <option value="">전체</option>
          <option v-for="t in allEventTypes" :key="t" :value="t">{{ t }}</option>
        </select>
      </div>
      <div>
        <label class="text-xs text-gray-500 block mb-1">volume</label>
        <select v-model="filterVolume" class="border rounded px-2 py-1">
          <option value="">전체</option>
          <option v-for="v in allVolumes" :key="v" :value="v">{{ v }}</option>
        </select>
      </div>
      <button class="bg-gray-700 text-white px-3 py-1.5 rounded ml-auto" @click="refresh">새로고침</button>
    </div>

    <div class="bg-white rounded-lg shadow overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left p-3">시각</th>
            <th class="text-left p-3">event</th>
            <th class="text-left p-3">volume</th>
            <th class="text-left p-3">path</th>
            <th class="text-left p-3">action</th>
            <th class="text-left p-3">mode</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(e, i) in filtered" :key="i" class="border-t">
            <td class="p-3 font-mono text-xs">{{ e.ts }}</td>
            <td class="p-3 font-mono">{{ e.event }}</td>
            <td class="p-3">{{ e.volume }}</td>
            <td class="p-3 font-mono text-xs text-gray-500">{{ e.path }}</td>
            <td class="p-3">{{ e.action }}</td>
            <td class="p-3">{{ e.mode }}</td>
          </tr>
          <tr v-if="filtered.length === 0">
            <td colspan="6" class="p-4 text-gray-400 text-center">이벤트가 없습니다.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
