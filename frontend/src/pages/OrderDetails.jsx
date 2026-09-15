import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import Notice from '../components/Notice';
import OrderStatusBadge from '../components/OrderStatusBadge';
import OrderItemsTable from '../components/OrderItemsTable';
import ReservationPanel from '../components/ReservationPanel';
import OrderActions from '../components/OrderActions';
import { useFetch } from '../hooks/useFetch';
import { getOrder } from '../services/orderService';
import { getPayments } from '../services/paymentService';
import { formatDateTime, formatMoney, formatTime } from '../utils/format';

export default function OrderDetails() {
  const { id } = useParams();
  const { data: order, setData: setOrder, loading, error, reload } = useFetch(() => getOrder(id), [id]);
  const payments = useFetch(() => getPayments(id), [id]);
  const [cancelled, setCancelled] = useState(false);

  const refreshAll = () => {
    reload({ silent: true });
    payments.reload({ silent: true });
  };

  if (loading) return <Loading label="Loading order…" />;
  if (error) {
    return (
      <>
        <ErrorMessage error={error} onRetry={() => reload()} />
        <Link to="/orders" className="btn btn-secondary">
          ← Back to orders
        </Link>
      </>
    );
  }

  const allowed = order.allowedTransitions ?? [];

  return (
    <>
      <Link to="/orders" className="back-link">
        ← All orders
      </Link>
      <PageHeader
        title={
          <span className="title-with-badge">
            <span className="mono">{order.orderNumber}</span>
            <OrderStatusBadge status={order.status} size="lg" />
          </span>
        }
        subtitle={`Created ${formatDateTime(order.createdAt)}`}
        actions={
          <button type="button" className="btn btn-secondary" onClick={refreshAll}>
            Refresh
          </button>
        }
      />

      {cancelled && (
        <Notice type="success" title="Order cancelled" onDismiss={() => setCancelled(false)}>
          The items have been returned to inventory.
        </Notice>
      )}

      <ReservationPanel order={order} onExpire={refreshAll} />

      <div className="layout-split">
        <div className="stack">
          <section className="card">
            <h2>Items</h2>
            <OrderItemsTable items={order.items} totalAmount={order.totalAmount} />
          </section>

          <section className="card">
            <h2>Payments</h2>
            {payments.loading && <Loading inline label="Loading payments…" />}
            {payments.error && <ErrorMessage error={payments.error} onRetry={() => payments.reload()} />}
            {payments.data && payments.data.length === 0 && (
              <p className="muted">No payment attempts for this order.</p>
            )}
            {payments.data && payments.data.length > 0 && (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Result</th>
                      <th className="num">Amount</th>
                      <th>Message</th>
                      <th>Started</th>
                      <th>Completed</th>
                    </tr>
                  </thead>
                  <tbody>
                    {payments.data.map((p) => (
                      <tr key={p.paymentId}>
                        <td className="mono">{p.paymentId}</td>
                        <td>
                          <OrderStatusBadge status={p.paymentStatus} />
                        </td>
                        <td className="num">{formatMoney(p.amount)}</td>
                        <td className="wrap">{p.message || '—'}</td>
                        <td className="nowrap">{formatTime(p.createdAt)}</td>
                        <td className="nowrap">{formatTime(p.completedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>

        <aside className="card summary-card">
          <h2>Summary</h2>
          <dl className="summary">
            <div>
              <dt>Status</dt>
              <dd>
                <OrderStatusBadge status={order.status} />
              </dd>
            </div>
            <div>
              <dt>Created</dt>
              <dd>{formatDateTime(order.createdAt)}</dd>
            </div>
            <div>
              <dt>{order.status === 'RESERVED' ? 'Reserved until' : 'Reservation ended'}</dt>
              <dd>{formatDateTime(order.expiresAt)}</dd>
            </div>
            <div className="summary-total">
              <dt>Total</dt>
              <dd>{formatMoney(order.totalAmount)}</dd>
            </div>
          </dl>

          <h3 className="section-label">Allowed next steps</h3>
          {allowed.length === 0 ? (
            <p className="muted small">None — this order is in a final state.</p>
          ) : (
            <div className="badge-list">
              {allowed.map((status) => (
                <OrderStatusBadge key={status} status={status} />
              ))}
            </div>
          )}

          <OrderActions
            order={order}
            onChanged={(updated) => {
              setOrder(updated);
              setCancelled(updated.status === 'CANCELLED');
              payments.reload({ silent: true });
            }}
            onFailed={refreshAll}
          />
        </aside>
      </div>
    </>
  );
}
