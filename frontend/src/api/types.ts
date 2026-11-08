// Mirrors the backend DTOs (Java records). Instants arrive as ISO-8601
// strings; nullable fields are sent as null, not omitted.

// auth-service
export interface TokenResponse {
  accessToken: string
  tokenType: string
  /** Seconds until the token expires. */
  expiresIn: number
}

export interface User {
  id: string
  email: string
  createdAt: string
}

// url-service
export interface ShortLink {
  shortCode: string
  shortUrl: string
  originalUrl: string
  createdAt: string
  updatedAt: string | null
  expiresAt: string | null
  active: boolean
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CreateLinkRequest {
  originalUrl: string
  customAlias?: string
  expiresAt?: string
}

/** PUT semantics: a null expiresAt removes any existing expiry. */
export interface UpdateLinkRequest {
  originalUrl: string
  expiresAt: string | null
}

// analytics-service
export interface CountBucket {
  value: string
  count: number
}

export interface DailyCount {
  /** yyyy-MM-dd, a UTC day. */
  date: string
  clicks: number
}

/** totalClicks and uniqueVisitors are all-time; clicksPerDay covers `days` UTC days. */
export interface LinkStats {
  shortCode: string
  totalClicks: number
  uniqueVisitors: number
  days: number
  clicksPerDay: DailyCount[]
  topReferrers: CountBucket[]
  browsers: CountBucket[]
  operatingSystems: CountBucket[]
  devices: CountBucket[]
}

export interface LinkClicks {
  shortCode: string
  clicks: number
}

/** totalClicks covers all of the caller's links; links is the top N by clicks. */
export interface TopLinks {
  totalClicks: number
  links: LinkClicks[]
}
