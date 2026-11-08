import type { ReactNode } from 'react'

interface CardProps {
  title?: ReactNode
  description?: ReactNode
  /** Controls at the right of the header. */
  actions?: ReactNode
  className?: string
  children: ReactNode
}

export function Card({ title, description, actions, className = '', children }: CardProps) {
  return (
    <section className={`rounded-xl border border-line bg-surface shadow-[0_1px_2px_rgb(0_0_0/0.04)] ${className}`}>
      {(title || actions) && (
        <header className="flex flex-wrap items-start justify-between gap-3 px-5 pt-5">
          <div className="min-w-0">
            {title && <h2 className="text-base font-semibold">{title}</h2>}
            {description && <p className="mt-0.5 text-sm text-ink-2">{description}</p>}
          </div>
          {actions}
        </header>
      )}
      <div className="p-5">{children}</div>
    </section>
  )
}
