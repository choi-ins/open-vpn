import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import Sidebar from '../components/Sidebar.vue'

describe('Sidebar', () => {
  it('renders 4 nav links (대시보드/블록리스트/이벤트 로그/정책 설정)', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/blocklist', component: { template: '<div />' } },
        { path: '/events', component: { template: '<div />' } },
        { path: '/policy', component: { template: '<div />' } },
      ],
    })
    await router.push('/')
    await router.isReady()
    const w = mount(Sidebar, { global: { plugins: [router] } })
    const text = w.text()
    expect(text).toContain('대시보드')
    expect(text).toContain('블록리스트')
    expect(text).toContain('이벤트 로그')
    expect(text).toContain('정책 설정')
    expect(w.findAll('a').length).toBe(4)
  })
})
