import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import CartItem from '../components/CartItem';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import EmptyState from '../components/EmptyState';
import ConfirmDialog from '../components/ConfirmDialog';
import { useCart } from '../context/CartContext';
import { formatMoney, pluralize } from '../utils/format';

export default function Cart() {
  const navigate = useNavigate();
  const { cart, items, itemCount, totalAmount, loading, increaseItem, decreaseItem, removeItem, clearCart } = useCart();
  const [busyItemId, setBusyItemId] = useState(null);
  const [clearing, setClearing] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const [error, setError] = useState(null);

  const busy = busyItemId !== null || clearing;

  const updateItem = async (item, change) => {
    if (busy) return;
    setBusyItemId(item.id);
    setError(null);
    try {
      await change(item);
    } catch (e) {
      setError(e);
    } finally {
      setBusyItemId(null);
    }
  };

  const handleClear = async () => {
    setClearing(true);
    setError(null);
    try {
      await clearCart();
    } catch (e) {
      setError(e);
    } finally {
      setClearing(false);
      setConfirmClear(false);
    }
  };

  if (loading && !cart) return <Loading label="Loading cart…" />;

  return (
    <>
      <PageHeader
        title="Cart"
        subtitle={items.length ? `${pluralize(itemCount, 'item')} ready for checkout` : undefined}
        actions={
          <Link to="/" className="btn btn-secondary">
            ← Continue shopping
          </Link>
        }
      />

      {error && <ErrorMessage error={error} onDismiss={() => setError(null)} />}

      {cart && items.length === 0 && (
        <EmptyState
          icon="🛒"
          title="Your cart is empty"
          message="Add products to start a sale."
          action={
            <Link to="/" className="btn btn-primary">
              Browse products
            </Link>
          }
        />
      )}

      {items.length > 0 && (
        <div className="layout-split">
          <section className="card">
            <div className="card-header">
              <h2>Items</h2>
              <button type="button" className="btn-link danger" onClick={() => setConfirmClear(true)} disabled={busy}>
                Clear cart
              </button>
            </div>
            <div className="cart-list">
              {items.map((item) => (
                <CartItem
                  key={item.id}
                  item={item}
                  busy={busyItemId === item.id || clearing}
                  onIncrease={(i) => updateItem(i, increaseItem)}
                  onDecrease={(i) => updateItem(i, decreaseItem)}
                  onRemove={(i) => updateItem(i, (it) => removeItem(it.id))}
                />
              ))}
            </div>
          </section>

          <aside className="card summary-card">
            <h2>Order summary</h2>
            <dl className="summary">
              <div>
                <dt>Items</dt>
                <dd>{itemCount}</dd>
              </div>
              <div className="summary-total">
                <dt>Total</dt>
                <dd>{formatMoney(totalAmount)}</dd>
              </div>
            </dl>
            <p className="muted small">
              Stock is not held while items are in the cart. It is reserved for 5 minutes when you check out.
            </p>
            <button type="button" className="btn btn-primary btn-block btn-lg" onClick={() => navigate('/checkout')} disabled={busy}>
              Proceed to checkout
            </button>
          </aside>
        </div>
      )}

      <ConfirmDialog
        open={confirmClear}
        title="Clear the cart?"
        message="All items will be removed from the cart."
        confirmLabel="Clear cart"
        danger
        busy={clearing}
        onConfirm={handleClear}
        onCancel={() => setConfirmClear(false)}
      />
    </>
  );
}
