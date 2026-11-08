import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, type TooltipContentProps } from 'recharts'
import type { DailyCount } from '../../api/types'
import { formatCount, formatUtcDay } from '../../lib/format'

const tick = { fill: 'var(--muted)', fontSize: 12 }

/**
 * Clicks per day: a single series, so no legend (the card title names it).
 * 2px line over a 10% wash, recessive hairline grid, crosshair tooltip.
 * Colors are CSS variables, so the chart follows the theme without re-rendering.
 */
export function ClicksChart({ data }: { data: DailyCount[] }) {
  const total = data.reduce((sum, day) => sum + day.clicks, 0)

  return (
    <div className="relative h-64">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke="var(--grid)" />
          <XAxis
            dataKey="date"
            tickFormatter={(day: string) => formatUtcDay(day)}
            tickLine={false}
            axisLine={{ stroke: 'var(--axis)' }}
            tick={tick}
            tickMargin={8}
            minTickGap={28}
          />
          <YAxis
            allowDecimals={false}
            // An all-zero range would otherwise collapse to a single tick.
            domain={[0, (dataMax: number) => Math.max(dataMax, 4)]}
            tickFormatter={(value: number) => formatCount(value)}
            tickLine={false}
            axisLine={false}
            tick={tick}
            width={44}
          />
          <Tooltip cursor={{ stroke: 'var(--axis)', strokeWidth: 1 }} content={ChartTooltip} isAnimationActive={false} />
          <Area
            // Straight segments: a smoothed curve would rise before the day
            // the clicks happened, implying clicks that never occurred.
            type="linear"
            dataKey="clicks"
            name="Clicks"
            stroke="var(--series-1)"
            strokeWidth={2}
            fill="var(--series-1)"
            fillOpacity={0.1}
            dot={false}
            activeDot={{ r: 5, fill: 'var(--series-1)', stroke: 'var(--surface)', strokeWidth: 2 }}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
      {total === 0 && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <p className="rounded-md bg-surface px-3 py-1.5 text-sm text-ink-2">No clicks in this period yet</p>
        </div>
      )}
    </div>
  )
}

function ChartTooltip({ active, payload, label }: TooltipContentProps) {
  if (!active || !payload?.length) return null
  const clicks = Number(payload[0].value ?? 0)
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-2 text-sm shadow-lg">
      <p className="text-ink-2">{formatUtcDay(String(label), true)}</p>
      <p className="mt-0.5 flex items-center gap-2 font-medium text-ink">
        <span className="size-2 rounded-full bg-series-1" aria-hidden />
        {formatCount(clicks)} {clicks === 1 ? 'click' : 'clicks'}
      </p>
    </div>
  )
}

/** The same data as a table: the accessible twin of the chart. */
export function ClicksTable({ data }: { data: DailyCount[] }) {
  return (
    <div className="max-h-64 overflow-auto rounded-lg border border-line">
      <table className="w-full text-sm">
        <caption className="sr-only">Clicks per day</caption>
        <thead className="sticky top-0 bg-wash text-left text-ink-2">
          <tr>
            <th scope="col" className="px-3 py-2 font-medium">
              Day (UTC)
            </th>
            <th scope="col" className="px-3 py-2 text-right font-medium">
              Clicks
            </th>
          </tr>
        </thead>
        <tbody>
          {[...data].reverse().map((day) => (
            <tr key={day.date} className="border-t border-line">
              <td className="px-3 py-1.5">{formatUtcDay(day.date, true)}</td>
              <td className="px-3 py-1.5 text-right tabular-nums">{formatCount(day.clicks)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
