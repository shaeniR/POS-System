import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import EmptyState from '../components/EmptyState';
import OrderStatusBadge from '../components/OrderStatusBadge';
import ReservationTimer from '../components/ReservationTimer';
import { useFetch, useInterval } from '../hooks/useFetch';
import { getOrder, getOrders } from '../services/orderService';
import { formatDateTime, formatMoney } from '../utils/format';

const REFRESH_MS = 10000;

async function loadOrders() {
  const orders = await getOrders();
  return [...orders].sort((a, b) => b.id - a.id);
}

export default function Orders() {
  const navigate = useNavigate();
  const { data: orders, loading, error, reload } = useFetch(loadOrders);
  const [filter, setFilter] = useState('ALL');

  useInterval(() => reload({ silent: true }), REFRESH_MS);

  // Filters are built from the statuses the backend actually returned
  const statusCounts = useMemo(() => {
    const counts = {};
    (orders ?? []).forEach((o) => {
      counts[o.status] = (counts[o.status] ?? 0) + 1;
    });
    return counts;
  }, [orders]);

  const visible = filter === 'ALL' ? orders ?? [] : (orders ?? []).filter((o) => o.status === filter);

  // Reading a single order makes the backend expire it immediately if its time is up
  const refreshExpired = (orderId) => {
    getOrder(orderId)
      .catch(() => {})
      .finally(() => reload({ silent: true }));
  };

  return (
    <>
      <PageHeader
        title="Orders"
        subtitle="All orders and their lifecycle status. Updates automatically."
        actions={
          <button type="button" className="btn btn-secondary" onClick={() => reload()} disabled={loading}>
            Refresh
          </button>
        }
      />

      {loading && !orders && <Loading label="Loading orders…" />}
      {error && <ErrorMessage error={error} onRetry={() => reload()} />}

      {orders && orders.length === 0 && (
        <EmptyState
          icon="🧾"
          title="No orders yet"
          message="Orders appear here after checkout."
          action={
            <Link to="/" className="btn btn-primary">
              Start a sale
            </Link>
          }
        />
      )}

      {orders && orders.length > 0 && (
        <section className="card card-flush">
          <div className="chips" role="tablist" aria-label="Filter by status">
            <button
              type="button"
              className={`chip${filter === 'ALL' ? ' active' : ''}`}
              onClick={() => setFilter('ALL')}
            >
              All <span>{orders.length}</span>
            </button>
            {Object.entries(statusCounts).map(([status, count]) => (
              <button
                key={status}
                type="button"
                className={`chip${filter === status ? ' active' : ''}`}
                onClick={() => setFilter(status)}
              >
                {status.charAt(0) + status.slice(1).toLowerCase()} <span>{count}</span>
              </button>
            ))}
          </div>

          {visible.length === 0 ? (
            <p className="muted center padded">No orders with this status.</p>
          ) : (
            <div className="table-wrap">
              <table className="table table-hover">
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Status</th>
                    <th className="num">Items</th>
                    <th className="num">Total</th>
                    <th>Created</th>
                    <th>Reservation</th>
                    <th aria-label="Actions" />
                  </tr>
                </thead>
                <tbody>
                  {visible.map((order) => (
                    <tr key={order.id} onClick={() => navigate(`/orders/${order.id}`)} className="clickable">
                      <td className="mono strong">{order.orderNumber}</td>
                      <td>
                        <OrderStatusBadge status={order.status} />
                      </td>
                      <td className="num">{order.items.reduce((sum, item) => sum + item.quantity, 0)}</td>
                      <td className="num strong">{formatMoney(order.totalAmount)}</td>
                      <td className="nowrap">{formatDateTime(order.createdAt)}</td>
                      <td className="nowrap">
                        {order.status === 'RESERVED' && order.reservationSecondsRemaining != null ? (
                          <ReservationTimer
                            compact
                            secondsRemaining={order.reservationSecondsRemaining}
                            onExpire={() => refreshExpired(order.id)}
                          />
                        ) : (
                          <span className="muted">—</span>
                        )}
                      </td>
                      <td className="num">
                        <Link
                          to={`/orders/${order.id}`}
                          className="btn btn-secondary btn-sm"
                          onClick={(e) => e.stopPropagation()}
                        >
                          View
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </>
  );
}
