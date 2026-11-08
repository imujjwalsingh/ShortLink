import type { ShortLink } from '../api/types'

const fullNumber = new Intl.NumberFormat('en-US')
const compactNumber = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })

/** 1,284 in full; compact from 10,000 up (12.9K, 4.2M). */
export function formatCount(value: number): string {
  return Math.abs(value) >= 10_000 ? compactNumber.format(value) : fullNumber.format(value)
}

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })
const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export function formatDate(iso: string): string {
  return dateFormat.format(new Date(iso))
}

export function formatDateTime(iso: string): string {
  return dateTimeFormat.format(new Date(iso))
}

const utcDay = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
const utcDayWithYear = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' })

/**
 * Formats an analytics bucket ("2026-11-08", a UTC day). Formatting in UTC
 * matters: in a time zone behind UTC, local formatting would label every
 * bucket with the previous day.
 */
export function formatUtcDay(day: string, withYear = false): string {
  const date = new Date(`${day}T00:00:00Z`)
  return (withYear ? utcDayWithYear : utcDay).format(date)
}

const relativeFormat = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
]

/**
 * "3 hours ago", "yesterday", "in 2 days". Anything within a minute either
 * way is "just now": server and browser clocks drift, and a link created a
 * second ago shouldn't read as "in under a minute".
 */
export function formatRelative(iso: string, now = Date.now()): string {
  const seconds = Math.round((Date.parse(iso) - now) / 1000)
  for (const [unit, size] of RELATIVE_UNITS) {
    if (Math.abs(seconds) >= size) return relativeFormat.format(Math.round(seconds / size), unit)
  }
  return 'just now'
}

/**
 * Adds https:// when the user typed a bare host ("example.com/page"); the
 * backend only accepts absolute http(s) URLs.
 */
export function normalizeUrl(input: string): string {
  const trimmed = input.trim()
  if (!trimmed) return trimmed
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`
}

/** "http://localhost:8080/abc" -> "localhost:8080/abc", for display only. */
export function withoutScheme(url: string): string {
  return url.replace(/^[a-z][a-z0-9+.-]*:\/\//i, '')
}

export type LinkStatus = 'active' | 'expired' | 'inactive'

/** Mirrors the redirect's own rules: inactive is a 404, past expiry a 410. */
export function linkStatus(link: Pick<ShortLink, 'active' | 'expiresAt'>, now = Date.now()): LinkStatus {
  if (!link.active) return 'inactive'
  if (link.expiresAt && Date.parse(link.expiresAt) <= now) return 'expired'
  return 'active'
}

const pad = (n: number) => String(n).padStart(2, '0')

/** ISO instant -> value for <input type="datetime-local">, in the user's time zone. */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** A datetime-local value (local time, no offset) -> ISO instant; null if empty or invalid. */
export function fromLocalInput(value: string): string | null {
  if (!value) return null
  const d = new Date(value) // no offset in the string, so parsed as local time
  return Number.isNaN(d.getTime()) ? null : d.toISOString()
}

/** analytics-service records a missing or unparseable Referer as "direct". */
export function referrerLabel(value: string): string {
  return value === 'direct' ? 'Direct or none' : value
}

/**
 * Browser / OS / device buckets. Capitalizes all-lowercase values
 * ("desktop", "unknown") but leaves names with their own casing alone
 * ("iOS", "macOS").
 */
export function bucketLabel(value: string): string {
  if (!value || value !== value.toLowerCase()) return value
  return value.charAt(0).toUpperCase() + value.slice(1)
}
