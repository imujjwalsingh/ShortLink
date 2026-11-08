import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ApiError } from './client'
import { api } from './endpoints'
import type { CreateLinkRequest, LinkStats, Page, ShortLink, TopLinks, UpdateLinkRequest, User } from './types'

export const PAGE_SIZE = 10

/**
 * How many links the dashboard asks analytics about. A link missing from a
 * list shorter than this has zero clicks; missing from a full list, its
 * count is unknown (see clickCounter).
 */
export const TOP_LINKS_LIMIT = 100

export const queryKeys = {
  me: ['me'] as const,
  linkPages: ['links'] as const,
  linkPage: (page: number) => ['links', page] as const,
  link: (code: string) => ['link', code] as const,
  stats: (code: string, days: number) => ['stats', code, days] as const,
  topLinks: ['topLinks'] as const,
}

export function useMe() {
  return useQuery<User, ApiError>({ queryKey: queryKeys.me, queryFn: api.me, staleTime: 5 * 60_000 })
}

export function useLinks(page: number) {
  return useQuery<Page<ShortLink>, ApiError>({
    queryKey: queryKeys.linkPage(page),
    queryFn: () => api.listLinks(page, PAGE_SIZE),
    // Keep the current page on screen while the next one loads.
    placeholderData: keepPreviousData,
  })
}

export function useLink(code: string) {
  return useQuery<ShortLink, ApiError>({ queryKey: queryKeys.link(code), queryFn: () => api.getLink(code) })
}

export function useLinkStats(code: string, days: number) {
  return useQuery<LinkStats, ApiError>({
    queryKey: queryKeys.stats(code, days),
    queryFn: () => api.linkStats(code, days),
    placeholderData: keepPreviousData,
  })
}

export function useTopLinks() {
  return useQuery<TopLinks, ApiError>({ queryKey: queryKeys.topLinks, queryFn: () => api.topLinks(TOP_LINKS_LIMIT) })
}

export function useCreateLink() {
  const queryClient = useQueryClient()
  return useMutation<ShortLink, ApiError, CreateLinkRequest>({
    mutationFn: api.createLink,
    onSuccess: (link) => {
      queryClient.setQueryData(queryKeys.link(link.shortCode), link)
      return queryClient.invalidateQueries({ queryKey: queryKeys.linkPages })
    },
  })
}

export function useUpdateLink(code: string) {
  const queryClient = useQueryClient()
  return useMutation<ShortLink, ApiError, UpdateLinkRequest>({
    mutationFn: (body) => api.updateLink(code, body),
    onSuccess: (link) => {
      queryClient.setQueryData(queryKeys.link(code), link)
      return queryClient.invalidateQueries({ queryKey: queryKeys.linkPages })
    },
  })
}

export function useDeactivateLink() {
  const queryClient = useQueryClient()
  return useMutation<void, ApiError, string>({
    mutationFn: api.deactivateLink,
    onSuccess: (_, code) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.link(code) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.linkPages }),
      ]),
  })
}

/**
 * Click count for a link on the dashboard, from the top-links response:
 * a number, or undefined when it can't be known (analytics unavailable, or
 * the link is outside a full top-N list).
 */
export function clickCounter(top: TopLinks | undefined): (code: string) => number | undefined {
  if (!top) return () => undefined
  const counts = new Map(top.links.map((link) => [link.shortCode, link.clicks]))
  const listIsComplete = top.links.length < TOP_LINKS_LIMIT
  return (code) => counts.get(code) ?? (listIsComplete ? 0 : undefined)
}
