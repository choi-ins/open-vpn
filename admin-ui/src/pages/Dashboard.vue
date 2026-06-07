<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { vpnApi, blocklistApi, diskApi, type VpnClient } from '../api'
import StatusCard from '../components/StatusCard.vue'

const clients = ref<VpnClient[]>([])
const blocklistCount = ref(0)
const diskRunning = ref(false)
const lastUpdate = ref('Never')
const error = ref<string | null>(null)

const connectedCount = computed(() => clients.value.filter(c => c.connected).length)
const totalCount = computed(() => clients.value.length)

let timer: number | null = null

async function refresh() {
  try {
    const [v, b, d] = await Promise.all([
      vpnApi.list().catch(e => { error.value = `VPN API: ${e.message}`; return null }),
      blocklistApi.list().catch(e => { error.value = `Blocklist API: ${e.message}`; return null }),
      diskApi.status().catch(e => { error.value = `DiskControl API: ${e.message}`; return null }),
    ])
    if (v) clients.value = v.clients
    if (b) blocklistCount.value = b.domains.length
    if (d) diskRunning.value = d.running
    lastUpdate.value = new Date().toLocaleTimeString('ko-KR')
    if (v && b && d) error.value = null
  } catch (e) {
    error.value = (e as Error).message
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
    <div class="mb-6 flex justify-between items-center">
      <h1 class="text-3xl font-bold">대시보드</h1>
      <span class="text-sm text-gray-500">마지막 업데이트: {{ lastUpdate }}</span>
    </div>

    <div v-if="error" class="bg-red-100 border border-red-400 text-red-700 p-3 rounded mb-4">
      ⚠️ {{ error }}
    </div>

    <div class="grid grid-cols-3 gap-4 mb-8">
      <StatusCard
        title="VPN"
        :status="`${connectedCount}/${totalCount}`"
        detail="클라이언트 연결"
        :color="connectedCount === totalCount && totalCount > 0 ? 'green' : 'red'"
      />
      <StatusCard
        title="웹 차단"
        :status="String(blocklistCount)"
        detail="차단 도메인"
        color="green"
      />
      <StatusCard
        title="디스크 통제"
        :status="diskRunning ? 'Running' : 'Stopped'"
        detail="에이전트 PoC"
        :color="diskRunning ? 'green' : 'gray'"
      />
    </div>

    <div class="bg-white rounded-lg shadow">
      <h2 class="text-lg font-semibold p-4 border-b">VPN 클라이언트 (5대)</h2>
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left p-3">#</th>
            <th class="text-left p-3">컨테이너</th>
            <th class="text-left p-3">상태</th>
            <th class="text-left p-3">transfer (rx/tx)</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in clients" :key="c.n" class="border-t">
            <td class="p-3 font-mono">{{ c.n }}</td>
            <td class="p-3 font-mono">{{ c.container }}</td>
            <td class="p-3">
              <span :class="c.connected ? 'text-green-700' : 'text-red-700'">
                {{ c.connected ? '🟢 connected' : '🔴 disconnected' }}
              </span>
            </td>
            <td class="p-3 font-mono text-xs text-gray-500">{{ c.transfer || '—' }}</td>
          </tr>
          <tr v-if="clients.length === 0">
            <td class="p-4 text-gray-400 text-center" colspan="4">로드 중…</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
