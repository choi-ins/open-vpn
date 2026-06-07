<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { diskApi } from '../api'

const currentMode = ref<string>('loading')
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const pendingMode = ref<string | null>(null)   // 확인 다이얼로그용
const busy = ref(false)

async function refresh() {
  try {
    const p = await diskApi.policy()
    currentMode.value = p.mode || 'unknown'
  } catch (e) {
    error.value = `정책 조회 실패: ${(e as Error).message}`
  }
}

function ask(mode: string) {
  if (mode === currentMode.value) return
  pendingMode.value = mode
  error.value = null
  success.value = null
}

async function confirm() {
  if (!pendingMode.value) return
  busy.value = true
  try {
    const r = await diskApi.setMode(pendingMode.value)
    success.value = `모드 변경됨: ${r.mode}`
    currentMode.value = r.mode
    pendingMode.value = null
  } catch (e: any) {
    error.value = e.response?.data?.error ?? (e as Error).message
  } finally {
    busy.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <div class="max-w-2xl">
    <h1 class="text-3xl font-bold mb-6">디스크 통제 정책</h1>

    <div v-if="error" class="bg-red-100 border border-red-400 text-red-700 p-3 rounded mb-4">⚠️ {{ error }}</div>
    <div v-if="success" class="bg-green-100 border border-green-400 text-green-700 p-3 rounded mb-4">✓ {{ success }}</div>

    <div class="bg-white rounded-lg shadow p-6 mb-6">
      <h2 class="text-lg font-semibold mb-3">현재 모드</h2>
      <div class="flex items-center gap-3">
        <span v-if="currentMode === 'block'" class="px-4 py-2 rounded-full bg-red-100 text-red-800 font-bold text-lg">BLOCK</span>
        <span v-else-if="currentMode === 'log-only'" class="px-4 py-2 rounded-full bg-green-100 text-green-800 font-bold text-lg">LOG-ONLY</span>
        <span v-else-if="currentMode === 'readonly'" class="px-4 py-2 rounded-full bg-amber-100 text-amber-800 font-bold text-lg">READONLY</span>
        <span v-else class="px-4 py-2 rounded-full bg-gray-100 text-gray-600 font-bold text-lg">{{ currentMode }}</span>
        <span class="text-gray-600">
          <template v-if="currentMode === 'block'">차단 모드</template>
          <template v-else-if="currentMode === 'log-only'">로그만 수집 (안전)</template>
          <template v-else-if="currentMode === 'readonly'">USB 읽기전용 (쓰기 차단)</template>
        </span>
      </div>
    </div>

    <div class="bg-white rounded-lg shadow p-6">
      <h2 class="text-lg font-semibold mb-3">모드 변경</h2>
      <p class="text-sm text-gray-600 mb-4">
        log-only: 이벤트 기록만 / readonly: USB 읽기전용 재마운트 / block: 차단 정책
      </p>
      <div class="flex gap-3 flex-wrap">
        <button
          class="px-4 py-2 rounded text-white"
          :class="currentMode === 'log-only' ? 'bg-green-500 opacity-50 cursor-not-allowed' : 'bg-green-500 hover:bg-green-600'"
          :disabled="currentMode === 'log-only'"
          @click="ask('log-only')"
        >log-only 로 전환</button>
        <button
          class="px-4 py-2 rounded text-white"
          :class="currentMode === 'readonly' ? 'bg-amber-500 opacity-50 cursor-not-allowed' : 'bg-amber-500 hover:bg-amber-600'"
          :disabled="currentMode === 'readonly'"
          @click="ask('readonly')"
        >readonly 로 전환</button>
        <button
          class="px-4 py-2 rounded text-white"
          :class="currentMode === 'block' ? 'bg-red-500 opacity-50 cursor-not-allowed' : 'bg-red-500 hover:bg-red-600'"
          :disabled="currentMode === 'block'"
          @click="ask('block')"
        >block 로 전환</button>
      </div>
    </div>

    <!-- 확인 다이얼로그 -->
    <div v-if="pendingMode" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg shadow-xl p-6 max-w-md w-full">
        <h3 class="text-lg font-bold mb-3">모드 변경 확인</h3>
        <p class="text-sm text-gray-600 mb-4">
          현재 모드 <code class="bg-gray-100 px-1 rounded">{{ currentMode }}</code>
          → 새 모드 <code class="bg-gray-100 px-1 rounded">{{ pendingMode }}</code>
          로 변경합니다.
        </p>
        <div class="flex gap-2 justify-end">
          <button class="px-4 py-2 rounded border" @click="pendingMode = null" :disabled="busy">취소</button>
          <button class="px-4 py-2 rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50" @click="confirm" :disabled="busy">
            {{ busy ? '변경 중…' : '확인' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
