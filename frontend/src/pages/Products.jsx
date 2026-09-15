import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import StockIndicator from '../components/StockIndicator';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import EmptyState from '../components/EmptyState';
import Notice from '../components/Notice';
import { useCart } from '../context/CartContext';
import { useFetch, useInterval } from '../hooks/useFetch';
import { getProducts } from '../services/productService';
import { isStockConflict } from '../utils/errors';
import { formatMoney, pluralize } from '../utils/format';

const REFRESH_MS = 10000;

export default function Products() {
  const { data: products, loading, error, reload } = useFetch(getProducts);
  const { cart, items, itemCount, addItem } = useCart();
  const [search, setSearch] = useState('');
  const [addingId, setAddingId] = useState(null);
  const [addError, setAddError] = useState(null);
  const [added, setAdded] = useState(null); // { id, name } of the product just added

  // Stock changes when other customers check out, so keep the numbers fresh
  useInterval(() => reload({ silent: true }), REFRESH_MS);

  useEffect(() => {
    if (!added) return undefined;
    const id = setTimeout(() => setAdded(null), 3000);
    return () => clearTimeout(id);
  }, [added]);

  const quantityInCart = useMemo(
    () => Object.fromEntries(items.map((item) => [item.productId, item.quantity])),
    [items]
  );

  const visibleProducts = useMemo(() => {
    const term = search.trim().toLowerCase();
    const list = products ?? [];
    return term ? list.filter((p) => p.name.toLowerCase().includes(term)) : list;
  }, [products, search]);

  const handleAdd = async (product) => {
    if (addingId) return;
    setAddingId(product.id);
    setAddError(null);
    setAdded(null);
    try {
      await addItem(product.id, 1);
      setAdded({ id: product.id, name: product.name });
    } catch (e) {
      setAddError(e);
      // Show the stock the backend has now
      if (isStockConflict(e)) reload({ silent: true });
    } finally {
      setAddingId(null);
    }
  };

  const buttonLabel = (product) => {
    if (addingId === product.id) return 'Adding…';
    if (added?.id === product.id) return '✓ Added';
    if (product.stockCount <= 0) return 'Unavailable';
    return 'Add to cart';
  };

  return (
    <>
      <PageHeader
        title="Products"
        subtitle="Choose items to sell. Stock is refreshed automatically."
        actions={
          <Link to="/cart" className="btn btn-primary">
            View cart · {pluralize(itemCount, 'item')}
          </Link>
        }
      />

      <div className="toolbar">
        <div className="search">
          <span aria-hidden="true">⌕</span>
          <input
            type="search"
            placeholder="Search products…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search products"
          />
        </div>
        {products && (
          <span className="muted">
            Showing {visibleProducts.length} of {pluralize(products.length, 'product')}
          </span>
        )}
      </div>

      {/* Fixed to the screen corner so the result is visible wherever the user has scrolled */}
      <div className="toast-stack" aria-live="polite">
        {added && <Notice type="success" title={`${added.name} added to cart`} onDismiss={() => setAdded(null)} />}
        {addError && <ErrorMessage error={addError} onDismiss={() => setAddError(null)} />}
      </div>

      {loading && <Loading label="Loading products…" />}
      {!loading && error && <ErrorMessage error={error} onRetry={() => reload()} />}

      {!loading && !error && products?.length === 0 && (
        <EmptyState
          title="No products yet"
          message="Add products in Product Management to start selling."
          action={
            <Link to="/admin/products" className="btn btn-primary">
              Add products
            </Link>
          }
        />
      )}

      {!loading && !error && products?.length > 0 && visibleProducts.length === 0 && (
        <EmptyState icon="🔍" title="No matching products" message={`Nothing matches “${search}”.`} />
      )}

      {!loading && !error && visibleProducts.length > 0 && (
        <section className="card card-flush">
          <div className="table-wrap">
            <table className="table table-hover">
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="num">Price</th>
                  <th className="num">Available stock</th>
                  <th>Status</th>
                  <th className="num">In cart</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {visibleProducts.map((product) => {
                  const outOfStock = product.stockCount <= 0;
                  const inCart = quantityInCart[product.id] ?? 0;
                  const justAdded = added?.id === product.id;
                  return (
                    <tr key={product.id} className={outOfStock ? 'row-muted' : ''}>
                      <td className="strong">{product.name}</td>
                      <td className="num">{formatMoney(product.price)}</td>
                      <td className="num strong">{product.stockCount}</td>
                      <td>
                        <StockIndicator stock={product.stockCount} />
                      </td>
                      <td className="num">
                        {inCart > 0 ? <span className="in-cart">{inCart}</span> : <span className="muted">—</span>}
                      </td>
                      <td className="num">
                        <button
                          type="button"
                          className={`btn btn-sm add-btn ${justAdded ? 'btn-success' : 'btn-primary'}`}
                          onClick={() => handleAdd(product)}
                          disabled={!cart || addingId !== null || outOfStock}
                        >
                          {buttonLabel(product)}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  );
}
