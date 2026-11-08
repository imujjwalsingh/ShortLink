/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API gateway origin; defaults to http://localhost:8080. Read at build time. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
