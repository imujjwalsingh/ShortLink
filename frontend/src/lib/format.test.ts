import { describe, expect, it } from 'vitest'
import {
  bucketLabel,
  formatCount,
  formatRelative,
  formatUtcDay,
  fromLocalInput,
  linkStatus,
  normalizeUrl,
  referrerLabel,
  toLocalInput,
  withoutScheme,
} from './format'

describe('formatCount', () => {
  it('prints numbers in full below 10,000', () => {
    expect(formatCount(0)).toBe('0')
    expect(formatCount(1284)).toBe('1,284')
    expect(formatCount(9999)).toBe('9,999')
  })

  it('compacts from 10,000 up', () => {
    expect(formatCount(12_900)).toBe('12.9K')
    expect(formatCount(4_200_000)).toBe('4.2M')
  })
})

describe('formatUtcDay', () => {
  it('keeps the UTC date in a time zone behind UTC', () => {
    // Guard: the suite must really run behind UTC for this to mean anything.
    expect(new Date('2026-11-08T00:00:00Z').getTimezoneOffset()).toBeGreaterThan(0)
    expect(formatUtcDay('2026-11-08', true)).toMatch(/\b8\b/)
    expect(formatUtcDay('2026-11-08', true)).not.toMatch(/\b7\b/)
  })
})

describe('normalizeUrl', () => {
  it('adds https:// to a bare host', () => {
    expect(normalizeUrl('example.com/page')).toBe('https://example.com/page')
    expect(normalizeUrl('  localhost:3000/x ')).toBe('https://localhost:3000/x')
  })

  it('leaves URLs that already have a scheme alone', () => {
    expect(normalizeUrl('http://example.com')).toBe('http://example.com')
    expect(normalizeUrl('HTTPS://Example.com')).toBe('HTTPS://Example.com')
  })

  it('leaves empty input empty, so required-field validation still fires', () => {
    expect(normalizeUrl('   ')).toBe('')
  })
})

describe('withoutScheme', () => {
  it('strips the scheme for display', () => {
    expect(withoutScheme('http://localhost:8080/abc')).toBe('localhost:8080/abc')
  })
})

describe('linkStatus', () => {
  const now = Date.parse('2026-11-08T12:00:00Z')

  it('is inactive when deactivated, even if also expired', () => {
    expect(linkStatus({ active: false, expiresAt: '2020-01-01T00:00:00Z' }, now)).toBe('inactive')
  })

  it('is expired once expiresAt has passed', () => {
    expect(linkStatus({ active: true, expiresAt: '2026-11-08T11:59:59Z' }, now)).toBe('expired')
    expect(linkStatus({ active: true, expiresAt: '2026-11-08T12:00:00Z' }, now)).toBe('expired')
  })

  it('is active with a future or no expiry', () => {
    expect(linkStatus({ active: true, expiresAt: '2026-11-09T00:00:00Z' }, now)).toBe('active')
    expect(linkStatus({ active: true, expiresAt: null }, now)).toBe('active')
  })
})

describe('datetime-local conversion', () => {
  it('round-trips an instant through the local input format', () => {
    const iso = '2026-12-01T17:30:00.000Z'
    expect(toLocalInput(iso)).toBe('2026-12-01T09:30') // Los Angeles, UTC-8
    expect(fromLocalInput(toLocalInput(iso))).toBe(iso)
  })

  it('treats empty and invalid values as no expiry', () => {
    expect(toLocalInput(null)).toBe('')
    expect(fromLocalInput('')).toBeNull()
    expect(fromLocalInput('not a date')).toBeNull()
  })
})

describe('bucket labels', () => {
  it('names the direct-traffic bucket', () => {
    expect(referrerLabel('direct')).toBe('Direct or none')
    expect(referrerLabel('news.ycombinator.com')).toBe('news.ycombinator.com')
  })

  it('capitalizes device types and unknown', () => {
    expect(bucketLabel('mobile')).toBe('Mobile')
    expect(bucketLabel('unknown')).toBe('Unknown')
  })

  it('keeps names that have their own casing', () => {
    expect(bucketLabel('iOS')).toBe('iOS')
    expect(bucketLabel('macOS')).toBe('macOS')
    expect(bucketLabel('Windows')).toBe('Windows')
  })
})

describe('formatRelative', () => {
  const now = Date.parse('2026-11-08T12:00:00Z')

  it('says "just now" within a minute either way (clock drift)', () => {
    expect(formatRelative('2026-11-08T12:00:20Z', now)).toBe('just now')
    expect(formatRelative('2026-11-08T11:59:30Z', now)).toBe('just now')
  })

  it('uses the largest whole unit', () => {
    expect(formatRelative('2026-11-08T09:00:00Z', now)).toMatch(/3 hours ago/)
  })
})
