import { Link, useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import EmptyState from '../components/EmptyState';
import OrderItemsTable from '../components/OrderItemsTable';
import OrderStatusBadge from '../components/OrderStatusBadge';
import ReservationPanel from '../components/ReservationPanel';
import OrderActions from '../components/OrderActions';
import { useCart } from '../context/CartContext';
import { useAction } from '../hooks/useAction';
import { useFetch } from '../hooks/useFetch';
import { checkoutCart, getOrder } from '../services/orderService';
import { isStockConflict } from '../utils/errors';
import { formatDateTime, formatMoney } from '../utils/format';

export default function Checkout() {
  const [searchParams] = useSearchParams();
  const orderId = searchParams.get('orderId');

  // After checkout the order id is kept in the URL, so a page refresh still shows the confirmation
  return orderId ? <OrderConfirmation orderId={orderId} /> : <CartReview />;
}

function CartReview() {
  const [, setSearchParams] = useSearchParams();
  const { cart, items, totalAmount, loading, refreshCart } = useCart();
  const { busy, error, run } = useAction();

  const handleCheckout = async () => {
    const result = await run(() => checkoutCart(cart.cartId));
    if (result.ok) {
      // The backend emptied the cart; reload it so the navbar count is correct
      await refreshCart().catch(() => {});
      setSearchParams({ orderId: String(result.data.id) }, { replace: true });
    } else if (isStockConflict(result.error)) {
      await refreshCart().catch(() => {});
    }
  };

  if (loading && !cart) return <Loading label="Loading cart…" />;

  return (
    <>
      <PageHeader title="Checkout" subtitle="Review the order. Checking out reserves the stock for 5 minutes." />

      {error && (
        <ErrorMessage error={error}>
          {isStockConflict(error) && (
            <p className="alert-detail">
              <Link to="/cart">Update your cart</Link> or <Link to="/">check current stock</Link>.
            </p>
          )}
        </ErrorMessage>
      )}

      {items.length === 0 ? (
        <EmptyState
          icon="🛒"
          title="Nothing to check out"
          message="Your cart is empty."
          action={
            <Link to="/" className="btn btn-primary">
              Browse products
            </Link>
          }
        />
      ) : (
        <div className="layout-split">
          <section className="card">
            <h2>Cart summary</h2>
            <OrderItemsTable items={items} />
          </section>

          <aside className="card summary-card">
            <h2>Payment due</h2>
            <dl className="summary">
              <div className="summary-total">
                <dt>Total</dt>
                <dd>{formatMoney(totalAmount)}</dd>
              </div>
            </dl>
            <ol className="steps">
              <li>Stock is reserved for your order</li>
              <li>You have 5 minutes to pay</li>
              <li>Unpaid reservations are released automatically</li>
            </ol>
            <button type="button" className="btn btn-primary btn-block btn-lg" onClick={handleCheckout} disabled={busy}>
              {busy ? 'Reserving stock…' : 'Checkout & reserve stock'}
            </button>
            <Link to="/cart" className="btn btn-secondary btn-block">
              Back to cart
            </Link>
          </aside>
        </div>
      )}
    </>
  );
}

function OrderConfirmation({ orderId }) {
  const { data: order, setData: setOrder, loading, error, reload } = useFetch(() => getOrder(orderId), [orderId]);

  if (loading) return <Loading label="Loading order…" />;
  if (error) return <ErrorMessage error={error} onRetry={() => reload()} />;

  const isReserved = order.status === 'RESERVED';

  return (
    <>
      <PageHeader
        title={isReserved ? 'Order reserved' : `Order ${order.status.toLowerCase()}`}
        subtitle={isReserved ? 'Your items are held. Complete payment before the reservation expires.' : undefined}
        actions={
          <Link to={`/orders/${order.id}`} className="btn btn-secondary">
            View order details
          </Link>
        }
      />

      <ReservationPanel order={order} onExpire={() => reload({ silent: true })} />

      <div className="layout-split">
        <section className="card">
          <h2>Items</h2>
          <OrderItemsTable items={order.items} totalAmount={order.totalAmount} />
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
            <div>
              <dt>Created</dt>
              <dd>{formatDateTime(order.createdAt)}</dd>
            </div>
            {isReserved && (
              <div>
                <dt>Reserved until</dt>
                <dd>{formatDateTime(order.expiresAt)}</dd>
              </div>
            )}
            <div className="summary-total">
              <dt>Total</dt>
              <dd>{formatMoney(order.totalAmount)}</dd>
            </div>
          </dl>
          <OrderActions order={order} onChanged={setOrder} onFailed={() => reload({ silent: true })} />
          {!isReserved && (
            <Link to="/" className="btn btn-secondary btn-block">
              Back to products
            </Link>
          )}
        </aside>
      </div>
    </>
  );
}
