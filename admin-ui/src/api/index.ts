import axios from 'axios'

// dev: Vite proxy → http://127.0.0.1:8080
// prod: 동일 origin으로 호스팅되거나 env로 분기
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '',
  timeout: 10_000,
})

// ----- 타입 정의 (control-plane API 응답과 1:1) -----

export interface VpnClient {
  n: number
  container: string
  connected: boolean
  transfer: string
}
export interface VpnClientsResponse { clients: VpnClient[] }
export interface ScriptResult { ok: boolean; stdout: string; stderr: string; code: number | null }

export interface BlocklistDomainsResponse { domains: string[] }
export interface AddDomainOk { ok: true; domain: string }
export interface AddDomainErr { error: string; domain: string }

export interface DiskStatus { running: boolean; pid: number | null }
export interface DiskPolicy {
  mode: 'log-only' | 'block' | 'readonly' | string
  rules: Record<string, unknown>
  logging: Record<string, unknown>
}
export interface DiskEvent {
  ts: string
  event: string
  volume: string
  path: string
  action: string
  mode: string
}
export interface DiskEventsResponse { events: DiskEvent[]; count: number; total: number }

// ----- VPN -----
export const vpnApi = {
  list: () => api.get<VpnClientsResponse>('/api/v1/clients').then(r => r.data),
  status: (n: number) => api.get<ScriptResult>(`/api/v1/clients/${n}/status`).then(r => r.data),
  connect: (n: number) => api.post<ScriptResult>(`/api/v1/clients/${n}/connect`).then(r => r.data),
  disconnect: (n: number) => api.post<ScriptResult>(`/api/v1/clients/${n}/disconnect`).then(r => r.data),
  killswitch: (n: number) => api.post<ScriptResult>(`/api/v1/clients/${n}/killswitch`).then(r => r.data),
}

// ----- Blocklist -----
export const blocklistApi = {
  list: () => api.get<BlocklistDomainsResponse>('/api/v1/blocklist/domains').then(r => r.data),
  add: (domain: string) =>
    api.post<AddDomainOk>('/api/v1/blocklist/domains', { domain }).then(r => r.data),
  remove: (domain: string) =>
    api.delete<AddDomainOk>(`/api/v1/blocklist/domains/${encodeURIComponent(domain)}`).then(r => r.data),
  reload: () => api.post<{ ok: boolean; message: string }>('/api/v1/blocklist/reload').then(r => r.data),
}

// ----- Disk Control -----
export const diskApi = {
  status: () => api.get<DiskStatus>('/api/v1/disk-control/status').then(r => r.data),
  policy: () => api.get<DiskPolicy>('/api/v1/disk-control/policy').then(r => r.data),
  setMode: (mode: string) =>
    api.put<{ ok: boolean; mode: string }>('/api/v1/disk-control/policy', { mode }).then(r => r.data),
  events: (limit = 10) =>
    api.get<DiskEventsResponse>('/api/v1/disk-control/events', { params: { limit } }).then(r => r.data),
}
