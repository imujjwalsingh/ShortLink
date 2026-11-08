import { useId, type InputHTMLAttributes, type ReactNode } from 'react'
import { inputClass } from './styles'

interface FieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'prefix'> {
  label: string
  /** Help text, replaced by the error when there is one. */
  hint?: ReactNode
  error?: string
  /** Fixed text shown in front of the input, e.g. the short-link host. */
  prefix?: string
  /** Rendered after the input on the same row, e.g. a "Clear" button. */
  trailing?: ReactNode
}

export function Field({ label, hint, error, prefix, trailing, id, className = '', ...input }: FieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const messageId = `${inputId}-message`
  const message = error ?? hint

  const field = (
    <input
      id={inputId}
      aria-invalid={error ? true : undefined}
      aria-describedby={message ? messageId : undefined}
      className={inputClass({ invalid: Boolean(error), joinedLeft: Boolean(prefix) })}
      {...input}
    />
  )

  return (
    <div className={className}>
      <label htmlFor={inputId} className="mb-1.5 block text-sm font-medium text-ink">
        {label}
      </label>
      <div className="flex items-center gap-2">
        {prefix ? (
          <div className="flex min-w-0 flex-1">
            <span className="flex h-10 max-w-[55%] shrink-0 items-center truncate rounded-l-lg border border-r-0 border-line bg-wash px-3 text-sm text-ink-2">
              {prefix}
            </span>
            {field}
          </div>
        ) : (
          field
        )}
        {trailing}
      </div>
      {message && (
        <p id={messageId} className={`mt-1.5 text-sm ${error ? 'text-danger' : 'text-ink-2'}`}>
          {message}
        </p>
      )}
    </div>
  )
}
