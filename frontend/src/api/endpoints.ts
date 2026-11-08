import { request } from './client'
import type {
  CreateLinkRequest,
  LinkStats,
  Page,
  ShortLink,
  TokenResponse,
  TopLinks,
  UpdateLinkRequest,
  User,
} from './types'

const code = (shortCode: string) => encodeURIComponent(shortCode)

export const api = {
  register: (email: string, password: string) =>
    request<User>('/api/auth/register', { method: 'POST', body: { email, password }, auth: false }),

  login: (email: string, password: string) =>
    request<TokenResponse>('/api/auth/login', { method: 'POST', body: { email, password }, auth: false }),

  me: () => request<User>('/api/auth/me'),

  listLinks: (page: number, size: number) => request<Page<ShortLink>>(`/api/urls?page=${page}&size=${size}`),

  getLink: (shortCode: string) => request<ShortLink>(`/api/urls/${code(shortCode)}`),

  createLink: (body: CreateLinkRequest) => request<ShortLink>('/api/urls', { method: 'POST', body }),

  updateLink: (shortCode: string, body: UpdateLinkRequest) =>
    request<ShortLink>(`/api/urls/${code(shortCode)}`, { method: 'PUT', body }),

  deactivateLink: (shortCode: string) => request<void>(`/api/urls/${code(shortCode)}`, { method: 'DELETE' }),

  linkStats: (shortCode: string, days: number) =>
    request<LinkStats>(`/api/analytics/links/${code(shortCode)}?days=${days}`),

  topLinks: (limit: number) => request<TopLinks>(`/api/analytics/links?limit=${limit}`),
}
