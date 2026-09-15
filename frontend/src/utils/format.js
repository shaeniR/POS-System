// Change these two values to match your shop's currency and locale.
const CURRENCY = 'USD';
const LOCALE = undefined; // the browser's locale

const moneyFormatter = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY });

export function formatMoney(value) {
  const amount = Number(value);
  return Number.isFinite(amount) ? moneyFormatter.format(amount) : '—';
}

/** The backend sends local date-times without a time zone, e.g. "2026-09-15T14:03:21.52". */
export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString(LOCALE, { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleTimeString(LOCALE);
}

/** 272 → "04:32" */
export function formatCountdown(totalSeconds) {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const minutes = String(Math.floor(safe / 60)).padStart(2, '0');
  const seconds = String(safe % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
}

export function pluralize(count, word) {
  return `${count} ${word}${count === 1 ? '' : 's'}`;
}
