import { Check, Copy } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Button } from './ui/Button'
import type { ButtonSize, ButtonVariant } from './ui/styles'

interface CopyButtonProps {
  text: string
  label?: string
  variant?: ButtonVariant
  size?: ButtonSize
  iconOnly?: boolean
}

export function CopyButton({ text, label = 'Copy', variant = 'secondary', size = 'sm', iconOnly = false }: CopyButtonProps) {
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!copied) return
    const timer = window.setTimeout(() => setCopied(false), 1500)
    return () => window.clearTimeout(timer)
  }, [copied])

  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
    } catch {
      // Clipboard API unavailable (non-secure origin) or denied.
      window.prompt('Copy this link:', text)
    }
  }

  return (
    <>
      <Button
        variant={variant}
        size={size}
        iconOnly={iconOnly}
        onClick={copy}
        aria-label={iconOnly ? `${label} ${text}` : undefined}
        title={iconOnly ? label : undefined}
      >
        {copied ? <Check className="size-4" aria-hidden /> : <Copy className="size-4" aria-hidden />}
        {!iconOnly && (copied ? 'Copied' : label)}
      </Button>
      <span role="status" className="sr-only">
        {copied ? 'Copied to clipboard' : ''}
      </span>
    </>
  )
}
