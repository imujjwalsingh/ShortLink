export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'shortlink.theme' // also read by the inline script in index.html

/** The theme on screen: an explicit choice if made, else the OS setting. */
export function currentTheme(): Theme {
  const explicit = document.documentElement.dataset.theme
  if (explicit === 'light' || explicit === 'dark') return explicit
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Storage blocked: the choice lasts until reload.
  }
}
