import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import Notice from '../components/Notice';
import OrderStatusBadge from '../components/OrderStatusBadge';
import ReservationPanel from '../components/ReservationPanel';
import { useAction } from '../hooks/useAction';
import { useFetch } from '../hooks/useFetch';
import { getOrder } from '../services/orderService';
import { newIdempotencyKey, payForOrder, PAYMENT_OUTCOMES } from '../services/paymentService';
import { formatMoney } from '../utils/format';

const OUTCOME_INFO = {
  SUCCESS: { title: 'Success', text: 'The gateway approves the payment. The order becomes PAID.' },
  FAILURE: { title: 'Failure', text: 'The gateway declines. The order fails and stock is released.' },
  TIMEOUT: { title: 'Timeout', text: 'The gateway does not answer in time. The reservation expires.' },
};

export default function Payment() {
  const { orderId } = useParams();
  const { data: order, loading, error, reload } = useFetch(() => getOrder(orderId), [orderId]);
  const [outcome, setOutcome] = useState('SUCCESS');
  const [payment, setPayment] = useState(null);
  const { busy, error: payError, run } = useAction();

  // One key for this payment screen: if the request is somehow sent twice, the backend rejects the repeat
  const idempotencyKey = useRef(newIdempotencyKey());

  const handlePay = async () => {
    const result = await run(() => payForOrder(order.id, outcome, idempotencyKey.current));
    if (result.ignored) return;
    if (result.ok) setPayment(result.data);
    // Whatever happened, show the order status the backend now has
    await reload({ silent: true });
  };

  if (loading) return <Loading label="Loading order…" />;
  if (error) return <ErrorMessage error={error} onRetry={() => reload()} />;

  const canPay = !payment && (order.allowedTransitions ?? []).includes('PAID');

  return (
    <>
      <PageHeader
        title="Payment"
        subtitle="Mock payment gateway — choose how the gateway should respond."
        actions={
          <Link to={`/orders/${order.id}`} className="btn btn-secondary">
            Order details
          </Link>
        }
      />

      <div className="layout-split">
        <section className="card">
          {payment ? (
            <PaymentResult payment={payment} />
          ) : canPay ? (
            <>
              <h2>Simulated gateway outcome</h2>
              <fieldset className="outcome-options" disabled={busy}>
                <legend className="sr-only">Payment outcome</legend>
                {PAYMENT_OUTCOMES.map((value) => (
                  <label key={value} className={`outcome outcome-${value.toLowerCase()}${outcome === value ? ' selected' : ''}`}>
                    <input
                      type="radio"
                      name="outcome"
                      value={value}
                      checked={outcome === value}
                      onChange={() => setOutcome(value)}
                    />
                    <span className="outcome-title">{OUTCOME_INFO[value]?.title ?? value}</span>
                    <span className="outcome-text">{OUTCOME_INFO[value]?.text}</span>
                  </label>
                ))}
              </fieldset>

              {payError && <ErrorMessage error={payError} />}

              <button type="button" className="btn btn-primary btn-lg btn-block" onClick={handlePay} disabled={busy}>
                {busy ? (
                  <>
                    <span className="spinner spinner-light" aria-hidden="true" /> Processing payment…
                  </>
                ) : (
                  `Pay ${formatMoney(order.totalAmount)}`
                )}
              </button>
              {busy && <p className="muted small center">Contacting the payment gateway. Please don’t close this page.</p>}
            </>
          ) : (
            <>
              {payError && <ErrorMessage error={payError} />}
              <ReservationPanel order={order} onExpire={() => reload({ silent: true })} />
              {order.status !== 'EXPIRED' && (
                <Notice type="info" title="This order cannot be paid">
                  Its current status is <OrderStatusBadge status={order.status} />, so payment is not available.
                </Notice>
              )}
              <div className="button-row">
                <Link to={`/orders/${order.id}`} className="btn btn-primary">
                  View order
                </Link>
                <Link to="/" className="btn btn-secondary">
                  Back to products
                </Link>
              </div>
            </>
          )}
        </section>

        <aside className="card summary-card">
          <h2>Order</h2>
          <dl className="summary">
            <div>
              <dt>Order number</dt>
              <dd className="mono">{order.orderNumber}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <OrderStatusBadge status={order.status} />
              </dd>
            </div>
            <div className="summary-total">
              <dt>Amount</dt>
              <dd>{formatMoney(order.totalAmount)}</dd>
            </div>
          </dl>
          {order.status === 'RESERVED' && (
            <ReservationPanel order={order} onExpire={() => reload({ silent: true })} />
          )}
        </aside>
      </div>
    </>
  );
}

const RESULT_VIEW = {
  SUCCESS: { type: 'success', icon: '✓', title: 'Payment Successful', lines: ['Order PAID'] },
  FAILED: { type: 'error', icon: '✕', title: 'Payment Failed', lines: ['Order FAILED', 'Stock Released'] },
  TIMEOUT: { type: 'warning', icon: '⏱', title: 'Payment Timed Out', lines: ['Order EXPIRED', 'Stock Released'] },
  REFUNDED: { type: 'warning', icon: '↺', title: 'Payment Refunded', lines: ['The order was already closed'] },
};

function PaymentResult({ payment }) {
  const view = RESULT_VIEW[payment.paymentStatus] ?? {
    type: 'info',
    icon: 'i',
    title: `Payment ${payment.paymentStatus}`,
    lines: [],
  };

  return (
    <div className={`payment-result result-${view.type}`}>
      <div className="result-icon" aria-hidden="true">
        {view.icon}
      </div>
      <h2>{view.title}</h2>
      <ul className="result-lines">
        {view.lines.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>

      <dl className="summary">
        <div>
          <dt>Order status</dt>
          <dd>
            <OrderStatusBadge status={payment.orderStatus} />
          </dd>
        </div>
        <div>
          <dt>Amount</dt>
          <dd>{formatMoney(payment.amount)}</dd>
        </div>
        {payment.transactionId && (
          <div>
            <dt>Transaction</dt>
            <dd className="mono">{payment.transactionId}</dd>
          </div>
        )}
        {payment.message && (
          <div>
            <dt>Gateway message</dt>
            <dd>{payment.message}</dd>
          </div>
        )}
      </dl>

      <div className="button-row center">
        <Link to={`/orders/${payment.orderId}`} className="btn btn-primary">
          View order
        </Link>
        <Link to="/" className="btn btn-secondary">
          Continue shopping
        </Link>
      </div>
    </div>
  );
}
