import { useEffect, useState } from 'react';
import PageHeader from '../components/PageHeader';
import ErrorMessage from '../components/ErrorMessage';
import Notice from '../components/Notice';
import { useAction } from '../hooks/useAction';
import { getProducts, getProductStock } from '../services/productService';
import { addCartItem, createOrGetCart } from '../services/cartService';
import { cancelOrder, checkoutCart } from '../services/orderService';
import { parseApiError } from '../utils/errors';

/**
 * Simulates many customers checking out the same limited-stock product at the same moment through the real API,
 * then verifies that the backend never sold more units than were in stock.
 */
export default function ConcurrencyTest() {
  const [products, setProducts] = useState([]);
  const [productId, setProductId] = useState('');
  const [buyers, setBuyers] = useState(10);
  const [quantity, setQuantity] = useState(1);
  const [report, setReport] = useState(null);
  const [cleanup, setCleanup] = useState('');
  const { busy, error, setError, run } = useAction();

  const loadProducts = async () => {
    const list = await getProducts();
    setProducts(list);
    return list;
  };

  useEffect(() => {
    run(async () => {
      const list = await loadProducts();
      if (list.length) setProductId(String(list[0].id));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const runTest = () =>
    run(async () => {
      setReport(null);
      setCleanup('');
      const id = Number(productId);
      const buyerCount = Number(buyers);
      const perBuyer = Number(quantity);

      const stockBefore = await getProductStock(id);
      if (perBuyer > stockBefore) {
        throw new Error(`Each buyer wants ${perBuyer} but only ${stockBefore} are in stock.`);
      }

      // 1. Every buyer fills their own cart (no stock is held yet)
      const cartIds = await Promise.all(
        Array.from({ length: buyerCount }, async () => {
          const cart = await createOrGetCart();
          await addCartItem(cart.cartId, id, perBuyer);
          return cart.cartId;
        })
      );

      // 2. All buyers press "Checkout" at the same moment
      const attempts = await Promise.allSettled(cartIds.map((cartId) => checkoutCart(cartId)));

      const orders = attempts.filter((a) => a.status === 'fulfilled').map((a) => a.value);
      const rejections = [
        ...new Set(
          attempts
            .filter((a) => a.status === 'rejected')
            .map((a) => {
              const parsed = parseApiError(a.reason);
              return parsed.detail || parsed.message;
            })
        ),
      ];
      const stockAfter = await getProductStock(id);
      const expectedOrders = Math.min(buyerCount, Math.floor(stockBefore / perBuyer));
      const unitsSold = orders.length * perBuyer;

      setReport({
        productName: products.find((p) => p.id === id)?.name,
        stockBefore,
        stockAfter,
        buyerCount,
        perBuyer,
        orders,
        rejected: buyerCount - orders.length,
        rejections,
        expectedOrders,
        passed:
          stockAfter >= 0 &&
          unitsSold <= stockBefore &&
          stockAfter === stockBefore - unitsSold &&
          orders.length === expectedOrders,
      });
      await loadProducts();
    });

  // Cancels the orders created by the test so their stock goes back to inventory
  const cancelTestOrders = () =>
    run(async () => {
      const results = await Promise.allSettled(report.orders.map((o) => cancelOrder(o.id)));
      const cancelled = results.filter((r) => r.status === 'fulfilled').length;
      const stockNow = await getProductStock(Number(productId));
      setCleanup(`Cancelled ${cancelled} of ${report.orders.length} test orders. Stock is now ${stockNow}.`);
      setReport({ ...report, orders: [] });
      await loadProducts();
    });

  return (
    <>
      <PageHeader
        title="Concurrency Test"
        subtitle="Many buyers check out the same product at once. The backend must never oversell."
      />

      <section className="card">
        <div className="field-row field-row-4">
          <div className="field">
            <label htmlFor="ct-product">Product</label>
            <select id="ct-product" value={productId} onChange={(e) => setProductId(e.target.value)} disabled={busy}>
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} (stock {p.stockCount})
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="ct-buyers">Buyers</label>
            <input
              id="ct-buyers"
              type="number"
              min="2"
              max="50"
              value={buyers}
              onChange={(e) => setBuyers(e.target.value)}
              disabled={busy}
            />
          </div>
          <div className="field">
            <label htmlFor="ct-qty">Qty per buyer</label>
            <input
              id="ct-qty"
              type="number"
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              disabled={busy}
            />
          </div>
          <div className="field field-end">
            <button type="button" className="btn btn-primary btn-block" onClick={runTest} disabled={busy || !productId}>
              {busy ? 'Running…' : 'Run test'}
            </button>
          </div>
        </div>
        <p className="muted small">
          Tip: set a product’s stock to 1–3 in Product Management to make the race obvious. Test orders reserve real stock —
          cancel them afterwards.
        </p>
      </section>

      {error && <ErrorMessage error={error} onDismiss={() => setError(null)} />}

      {report && (
        <section className="card">
          <div className="card-header">
            <h2>Result for {report.productName}</h2>
            <span className={`verdict ${report.passed ? 'pass' : 'fail'}`}>
              {report.passed ? '✓ No overselling' : '✕ Check failed'}
            </span>
          </div>

          <div className="stat-grid">
            <div className="stat">
              <span className="stat-label">Stock before</span>
              <strong className="stat-value">{report.stockBefore}</strong>
            </div>
            <div className="stat">
              <span className="stat-label">Buyers × qty</span>
              <strong className="stat-value">
                {report.buyerCount} × {report.perBuyer}
              </strong>
            </div>
            <div className="stat stat-available">
              <span className="stat-label">Orders created</span>
              <strong className="stat-value">
                {report.buyerCount - report.rejected} <small>/ expected {report.expectedOrders}</small>
              </strong>
            </div>
            <div className="stat stat-out">
              <span className="stat-label">Rejected</span>
              <strong className="stat-value">{report.rejected}</strong>
            </div>
            <div className="stat">
              <span className="stat-label">Stock after</span>
              <strong className="stat-value">{report.stockAfter}</strong>
            </div>
          </div>

          {report.rejections.length > 0 && (
            <>
              <h3 className="section-label">Rejection messages from the backend</h3>
              <ul className="message-list">
                {report.rejections.slice(0, 5).map((m) => (
                  <li key={m}>{m}</li>
                ))}
              </ul>
            </>
          )}

          {report.orders.length > 0 && (
            <button type="button" className="btn btn-secondary" onClick={cancelTestOrders} disabled={busy}>
              Cancel the {report.orders.length} test order(s) and restore stock
            </button>
          )}
          {cleanup && <Notice type="success" title={cleanup} />}
        </section>
      )}
    </>
  );
}
