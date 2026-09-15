// Presentation only: the statuses themselves and what they allow come from the backend.
const LABELS = {
  PENDING: 'Pending',
  RESERVED: 'Reserved',
  PAID: 'Paid',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  SUCCESS: 'Success',
  TIMEOUT: 'Timed out',
  PROCESSING: 'Processing',
  REFUNDED: 'Refunded',
};

export default function OrderStatusBadge({ status, size }) {
  if (!status) return null;
  const key = String(status).toLowerCase();
  return (
    <span className={`badge badge-${key}${size === 'lg' ? ' badge-lg' : ''}`}>
      <span className="badge-dot" aria-hidden="true" />
      {LABELS[status] ?? status}
    </span>
  );
}
