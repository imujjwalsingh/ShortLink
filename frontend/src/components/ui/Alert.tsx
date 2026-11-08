import { CircleAlert, CircleCheck, Info } from 'lucide-react'
import type { ReactNode } from 'react'

type Tone = 'error' | 'success' | 'info'

const tones: Record<Tone, { classes: string; Icon: typeof Info }> = {
  error: { classes: 'bg-danger-wash text-danger', Icon: CircleAlert },
  success: { classes: 'bg-success-wash text-success', Icon: CircleCheck },
  info: { classes: 'bg-wash text-ink-2', Icon: Info },
}

export function Alert({ tone = 'error', className = '', children }: { tone?: Tone; className?: string; children: ReactNode }) {
  const { classes, Icon } = tones[tone]
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`flex gap-2.5 rounded-lg px-3.5 py-3 text-sm ${classes} ${className}`}>
      <Icon className="mt-px size-4 shrink-0" aria-hidden />
      <div className="min-w-0">{children}</div>
    </div>
  )
}
