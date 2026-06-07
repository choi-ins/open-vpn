import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatusCard from '../components/StatusCard.vue'

describe('StatusCard', () => {
  it('renders title/status/detail and applies color class', () => {
    const w = mount(StatusCard, {
      props: { title: 'VPN', status: '5/5', detail: '연결됨', color: 'green' },
    })
    expect(w.text()).toContain('VPN')
    expect(w.text()).toContain('5/5')
    expect(w.text()).toContain('연결됨')
    expect(w.html()).toContain('bg-green-100')
  })

  it('uses red palette for color=red', () => {
    const w = mount(StatusCard, {
      props: { title: 'X', status: '0/5', detail: '오류', color: 'red' },
    })
    expect(w.html()).toContain('bg-red-100')
  })
})
