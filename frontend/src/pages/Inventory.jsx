import { useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import EmptyState from '../components/EmptyState';
import StockIndicator from '../components/StockIndicator';
import { useFetch, useInterval } from '../hooks/useFetch';
import { getStockOverview } from '../services/productService';

const REFRESH_MS = 5000;

export default function Inventory() {
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [updatedAt, setUpdatedAt] = useState(null);

  const { data: stock, loading, error, reload } = useFetch(async () => {
    const data = await getStockOverview();
    setUpdatedAt(new Date());
    return data;
  });

  useInterval(() => reload({ silent: true }), REFRESH_MS, autoRefresh);

  const rows = stock ?? [];
  const totals = rows.reduce(
    (acc, row) => ({
      available: acc.available + row.availableStock,
      reserved: acc.reserved + row.reservedStock,
      outOfStock: acc.outOfStock + (row.availableStock === 0 ? 1 : 0),
    }),
    { available: 0, reserved: 0, outOfStock: 0 }
  );

  return (
    <>
      <PageHeader
        title="Inventory"
        subtitle="Live stock levels as recorded by the backend."
        actions={
          <>
            <label className="toggle">
              <input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} />
              <span>Auto-refresh</span>
            </label>
            <button type="button" className="btn btn-secondary" onClick={() => reload()} disabled={loading}>
              Refresh
            </button>
          </>
        }
      />

      <div className="stat-grid">
        <div className="stat">
          <span className="stat-label">Products</span>
          <strong className="stat-value">{rows.length}</strong>
        </div>
        <div className="stat stat-available">
          <span className="stat-label">Available units</span>
          <strong className="stat-value">{totals.available}</strong>
        </div>
        <div className="stat stat-reserved">
          <span className="stat-label">Reserved units</span>
          <strong className="stat-value">{totals.reserved}</strong>
        </div>
        <div className="stat stat-out">
          <span className="stat-label">Out of stock</span>
          <strong className="stat-value">{totals.outOfStock}</strong>
        </div>
      </div>

      <div className="info-box">
        <strong>How stock is tracked</strong>
        <p>
          <b>Available</b> units can be bought right now. <b>Reserved</b> units are held by checked-out orders that are
          waiting for payment — they were already deducted from available stock at checkout, inside a locked database
          transaction, so two customers can never buy the same last unit. Reserved units return to available stock when a
          reservation expires, a payment fails, or an order is cancelled.
        </p>
      </div>

      {loading && !stock && <Loading label="Loading inventory…" />}
      {error && <ErrorMessage error={error} onRetry={() => reload()} />}

      {stock && rows.length === 0 && (
        <EmptyState
          title="No products in inventory"
          action={
            <Link to="/admin/products" className="btn btn-primary">
              Add products
            </Link>
          }
        />
      )}

      {rows.length > 0 && (
        <section className="card card-flush">
          <div className="card-header padded">
            <h2>Stock by product</h2>
            <span className="live">
              {autoRefresh && <span className="live-dot" aria-hidden="true" />}
              {updatedAt ? `Updated ${updatedAt.toLocaleTimeString()}` : ''}
            </span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="num">Available</th>
                  <th className="num">Reserved</th>
                  <th className="bar-col">Allocation</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const total = row.availableStock + row.reservedStock;
                  const availablePct = total > 0 ? (row.availableStock / total) * 100 : 0;
                  const reservedPct = total > 0 ? (row.reservedStock / total) * 100 : 0;
                  return (
                    <tr key={row.productId}>
                      <td className="strong">{row.productName}</td>
                      <td className="num strong">{row.availableStock}</td>
                      <td className="num">{row.reservedStock > 0 ? <span className="reserved-count">{row.reservedStock}</span> : 0}</td>
                      <td className="bar-col">
                        <div
                          className="stock-bar"
                          title={`${row.availableStock} available, ${row.reservedStock} reserved`}
                        >
                          <span className="stock-bar-available" style={{ width: `${availablePct}%` }} />
                          <span className="stock-bar-reserved" style={{ width: `${reservedPct}%` }} />
                        </div>
                      </td>
                      <td>
                        <StockIndicator stock={row.availableStock} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="legend padded">
            <span>
              <i className="legend-swatch available" /> Available
            </span>
            <span>
              <i className="legend-swatch reserved" /> Reserved (awaiting payment)
            </span>
          </div>
        </section>
      )}
    </>
  );
}
