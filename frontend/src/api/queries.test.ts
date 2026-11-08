import { describe, expect, it } from 'vitest'
import { TOP_LINKS_LIMIT, clickCounter } from './queries'

describe('clickCounter', () => {
  it('returns the count for links in the top list', () => {
    const clicksFor = clickCounter({ totalClicks: 7, links: [{ shortCode: 'abc', clicks: 7 }] })
    expect(clicksFor('abc')).toBe(7)
  })

  it('knows a missing link has zero clicks when the list is not full', () => {
    const clicksFor = clickCounter({ totalClicks: 7, links: [{ shortCode: 'abc', clicks: 7 }] })
    expect(clicksFor('never-clicked')).toBe(0)
  })

  it("doesn't guess for a link outside a full top-N list", () => {
    const links = Array.from({ length: TOP_LINKS_LIMIT }, (_, i) => ({ shortCode: `c${i}`, clicks: 1000 - i }))
    const clicksFor = clickCounter({ totalClicks: 99_999, links })
    expect(clicksFor('c0')).toBe(1000)
    expect(clicksFor('somewhere-below')).toBeUndefined()
  })

  it('is unknown for everything while analytics is unavailable', () => {
    expect(clickCounter(undefined)('abc')).toBeUndefined()
  })
})
