import { Moon, Sun } from 'lucide-react'
import { useState } from 'react'
import { applyTheme, currentTheme, type Theme } from '../lib/theme'
import { Button } from './ui/Button'

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme)
  const next: Theme = theme === 'dark' ? 'light' : 'dark'
  return (
    <Button
      variant="ghost"
      size="sm"
      iconOnly
      onClick={() => {
        applyTheme(next)
        setTheme(next)
      }}
      aria-label={`Switch to ${next} theme`}
      title={`Switch to ${next} theme`}
    >
      {theme === 'dark' ? <Sun className="size-4" aria-hidden /> : <Moon className="size-4" aria-hidden />}
    </Button>
  )
}
