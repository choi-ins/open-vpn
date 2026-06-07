<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { blocklistApi } from '../api'

const domains = ref<string[]>([])
const input = ref('')
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const loading = ref(false)

async function refresh() {
  try {
    const r = await blocklistApi.list()
    domains.value = r.domains
  } catch (e) {
    error.value = `목록 조회 실패: ${(e as Error).message}`
  }
}

async function add() {
  error.value = null; success.value = null
  if (!input.value.trim()) return
  loading.value = true
  try {
    const r = await blocklistApi.add(input.value.trim())
    success.value = `추가됨: ${r.domain}`
    input.value = ''
    await refresh()
  } catch (e: any) {
    const data = e.response?.data
    if (data?.error) error.value = `${data.error} (${data.domain ?? ''})`
    else error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

async function remove(d: string) {
  error.value = null; success.value = null
  try {
    await blocklistApi.remove(d)
    success.value = `삭제됨: ${d}`
    await refresh()
  } catch (e: any) {
    const data = e.response?.data
    if (data?.error) error.value = `${data.error} (${data.domain ?? ''})`
    else error.value = (e as Error).message
  }
}

onMounted(refresh)
</script>

<template>
  <div>
    <h1 class="text-3xl font-bold mb-6">블록리스트 관리</h1>

    <div v-if="error" class="bg-red-100 border border-red-400 text-red-700 p-3 rounded mb-3">⚠️ {{ error }}</div>
    <div v-if="success" class="bg-green-100 border border-green-400 text-green-700 p-3 rounded mb-3">✓ {{ success }}</div>

    <form class="bg-white rounded-lg shadow p-4 mb-6 flex gap-3" @submit.prevent="add">
      <input
        v-model="input"
        class="flex-1 border rounded px-3 py-2 font-mono"
        placeholder="example.com 또는 *.ads.com"
        :disabled="loading"
      />
      <button
        type="submit"
        class="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50"
        :disabled="loading || !input.trim()"
      >도메인 추가</button>
    </form>

    <div class="bg-white rounded-lg shadow">
      <h2 class="text-lg font-semibold p-4 border-b">차단 도메인 ({{ domains.length }})</h2>
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left p-3">도메인</th>
            <th class="text-left p-3">종류</th>
            <th class="text-right p-3">동작</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in domains" :key="d" class="border-t">
            <td class="p-3 font-mono">{{ d }}</td>
            <td class="p-3">
              <span v-if="d.startsWith('*.')" class="px-2 py-1 rounded bg-amber-100 text-amber-800 text-xs">와일드카드</span>
              <span v-else class="px-2 py-1 rounded bg-blue-100 text-blue-800 text-xs">일반</span>
            </td>
            <td class="p-3 text-right">
              <button class="text-red-600 hover:underline text-sm" @click="remove(d)">삭제</button>
            </td>
          </tr>
          <tr v-if="domains.length === 0">
            <td colspan="3" class="p-4 text-gray-400 text-center">차단 도메인이 없습니다.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
