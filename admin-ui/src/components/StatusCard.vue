<script setup lang="ts">
defineProps<{
  title: string
  status: string
  detail: string
  color: 'green' | 'red' | 'gray' | 'amber'
}>()

const colorMap = {
  green: 'bg-green-100 text-green-800 border-green-300',
  red: 'bg-red-100 text-red-800 border-red-300',
  gray: 'bg-gray-100 text-gray-700 border-gray-300',
  amber: 'bg-amber-100 text-amber-800 border-amber-300',
}
</script>

<template>
  <div class="bg-white rounded-lg shadow p-5 border-l-4" :class="colorMap[color]">
    <h3 class="font-semibold text-gray-700">{{ title }}</h3>
    <div class="text-3xl font-bold mt-2">{{ status }}</div>
    <p class="text-sm text-gray-500 mt-1">{{ detail }}</p>
  </div>
</template>
