// Class builders shared by components that need button or input styling on
// an element that isn't a <Button> or <Field> (e.g. a router <Link>).
//
// Each builder emits exactly one class per CSS property. Appending an
// override like "px-0" to a string that already has "px-3" does not work in
// Tailwind: the stylesheet's order decides which wins, not the order of the
// class names. Variations are options here instead.

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
export type ButtonSize = 'sm' | 'md' | 'lg'

const buttonBase =
  'inline-flex shrink-0 cursor-pointer items-center justify-center gap-2 whitespace-nowrap rounded-lg font-medium ' +
  'transition-colors disabled:pointer-events-none disabled:opacity-50 [&_svg]:shrink-0'

const buttonVariants: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-white hover:bg-accent-hover',
  secondary: 'border border-line bg-surface text-ink hover:bg-wash',
  ghost: 'text-ink-2 hover:bg-wash hover:text-ink',
  danger: 'bg-danger-solid text-white hover:bg-danger-solid-hover',
}

const buttonSizes: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-sm',
  md: 'h-10 px-4 text-sm',
  lg: 'h-11 px-5 text-base',
}

const iconButtonSizes: Record<ButtonSize, string> = {
  sm: 'size-8',
  md: 'size-10',
  lg: 'size-11',
}

export function buttonClass(variant: ButtonVariant = 'primary', size: ButtonSize = 'md'): string {
  return `${buttonBase} ${buttonVariants[variant]} ${buttonSizes[size]}`
}

/** Square button for a lone icon, the same height as the text buttons. */
export function iconButtonClass(variant: ButtonVariant = 'ghost', size: ButtonSize = 'sm'): string {
  return `${buttonBase} ${buttonVariants[variant]} ${iconButtonSizes[size]}`
}

interface InputOptions {
  invalid?: boolean
  size?: 'md' | 'lg'
  /** Square left corners, for an input joined to a prefix. */
  joinedLeft?: boolean
}

export function inputClass({ invalid = false, size = 'md', joinedLeft = false }: InputOptions = {}): string {
  return [
    'w-full min-w-0 border bg-surface px-3 text-ink outline-hidden transition-colors',
    'placeholder:text-muted focus:border-accent focus:ring-3 focus:ring-accent-soft',
    size === 'lg' ? 'h-11 text-base' : 'h-10 text-sm',
    joinedLeft ? 'rounded-r-lg' : 'rounded-lg',
    invalid ? 'border-danger' : 'border-line',
  ].join(' ')
}
