import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ConfirmDialog from './ConfirmDialog';
import ErrorMessage from './ErrorMessage';
import { cancelOrder } from '../services/orderService';
import { useAction } from '../hooks/useAction';

/**
 * Pay / Cancel buttons driven entirely by the order's `allowedTransitions` from the backend.
 * `onChanged(updatedOrder)` is called after a successful cancellation; `onFailed(error)` after a rejected one.
 */
export default function OrderActions({ order, onChanged, onFailed, showPay = true }) {
  const navigate = useNavigate();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const { busy, error, setError, run } = useAction();

  const allowed = order.allowedTransitions ?? [];
  const canPay = showPay && allowed.includes('PAID');
  const canCancel = allowed.includes('CANCELLED');

  if (!canPay && !canCancel) return null;

  const confirmCancel = async () => {
    const result = await run(() => cancelOrder(order.id));
    setConfirmOpen(false);
    if (result.ok) onChanged?.(result.data);
    else if (!result.ignored) onFailed?.(result.error);
  };

  return (
    <div className="order-actions">
      <div className="button-row">
        {canPay && (
          <button type="button" className="btn btn-primary" onClick={() => navigate(`/payment/${order.id}`)} disabled={busy}>
            Pay now
          </button>
        )}
        {canCancel && (
          <button type="button" className="btn btn-danger-outline" onClick={() => setConfirmOpen(true)} disabled={busy}>
            Cancel order
          </button>
        )}
      </div>

      {error && <ErrorMessage error={error} onDismiss={() => setError(null)} />}

      <ConfirmDialog
        open={confirmOpen}
        title={`Cancel order ${order.orderNumber}?`}
        message={
          order.status === 'PAID'
            ? 'The payment will be refunded and the items returned to inventory. This cannot be undone.'
            : 'The reserved items will be returned to inventory. This cannot be undone.'
        }
        confirmLabel="Cancel order"
        cancelLabel="Keep order"
        danger
        busy={busy}
        onConfirm={confirmCancel}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
