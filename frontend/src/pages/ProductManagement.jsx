import { useEffect, useMemo, useState } from 'react';
import PageHeader from '../components/PageHeader';
import ProductForm from '../components/ProductForm';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import EmptyState from '../components/EmptyState';
import Notice from '../components/Notice';
import ConfirmDialog from '../components/ConfirmDialog';
import StockIndicator from '../components/StockIndicator';
import { useAction } from '../hooks/useAction';
import { useFetch } from '../hooks/useFetch';
import { createProduct, deleteProduct, getProducts, updateProduct } from '../services/productService';
import { formatMoney } from '../utils/format';

export default function ProductManagement() {
  const { data: products, loading, error, reload } = useFetch(getProducts);
  const [editing, setEditing] = useState(null);
  const [formKey, setFormKey] = useState(0);
  const [toDelete, setToDelete] = useState(null);
  const [search, setSearch] = useState('');
  const [success, setSuccess] = useState('');

  const save = useAction();
  const remove = useAction();

  useEffect(() => {
    if (!success) return undefined;
    const id = setTimeout(() => setSuccess(''), 4000);
    return () => clearTimeout(id);
  }, [success]);

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    const list = [...(products ?? [])].sort((a, b) => a.id - b.id);
    return term ? list.filter((p) => p.name.toLowerCase().includes(term)) : list;
  }, [products, search]);

  const resetForm = () => {
    setEditing(null);
    save.setError(null);
    setFormKey((k) => k + 1);
  };

  const handleSubmit = async (values) => {
    const result = await save.run(() => (editing ? updateProduct(editing.id, values) : createProduct(values)));
    if (!result.ok) return;
    setSuccess(editing ? `“${result.data.name}” updated.` : `“${result.data.name}” added.`);
    resetForm();
    reload({ silent: true });
  };

  const startEdit = (product) => {
    setEditing(product);
    save.setError(null);
    setFormKey((k) => k + 1);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const confirmDelete = async () => {
    const product = toDelete;
    const result = await remove.run(() => deleteProduct(product.id));
    setToDelete(null);
    if (result.ok) {
      setSuccess(`“${product.name}” deleted.`);
      if (editing?.id === product.id) resetForm();
    }
    reload({ silent: true });
  };

  return (
    <>
      <PageHeader title="Product Management" subtitle="Add, edit and remove products in the catalogue." />

      {success && <Notice type="success" title={success} onDismiss={() => setSuccess('')} />}
      {remove.error && (
        <ErrorMessage error={remove.error} onDismiss={() => remove.setError(null)}>
          {remove.error.response?.status === 409 && (
            <p className="alert-detail">Products that appear in existing orders cannot be deleted.</p>
          )}
        </ErrorMessage>
      )}

      <div className="layout-admin">
        <section className="card form-card">
          <div className="card-header">
            <h2>{editing ? 'Edit product' : 'New product'}</h2>
            {editing && <span className="muted small">ID {editing.id}</span>}
          </div>
          {save.error && !save.error.response?.data?.validationErrors && <ErrorMessage error={save.error} />}
          <ProductForm
            key={formKey}
            product={editing}
            submitting={save.busy}
            serverError={save.error}
            onSubmit={handleSubmit}
            onCancel={editing ? resetForm : undefined}
          />
        </section>

        <section className="card card-flush">
          <div className="card-header padded">
            <h2>Catalogue</h2>
            <div className="search search-sm">
              <span aria-hidden="true">⌕</span>
              <input
                type="search"
                placeholder="Search…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                aria-label="Search products"
              />
            </div>
          </div>

          {loading && <Loading label="Loading products…" />}
          {!loading && error && (
            <div className="padded">
              <ErrorMessage error={error} onRetry={() => reload()} />
            </div>
          )}

          {!loading && !error && products?.length === 0 && (
            <EmptyState title="No products yet" message="Use the form to add your first product." />
          )}

          {!loading && !error && products?.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Name</th>
                    <th className="num">Price</th>
                    <th className="num">Stock</th>
                    <th>Status</th>
                    <th aria-label="Actions" />
                  </tr>
                </thead>
                <tbody>
                  {visible.map((product) => (
                    <tr key={product.id} className={editing?.id === product.id ? 'row-selected' : ''}>
                      <td className="mono muted">{product.id}</td>
                      <td className="strong">{product.name}</td>
                      <td className="num">{formatMoney(product.price)}</td>
                      <td className="num">{product.stockCount}</td>
                      <td>
                        <StockIndicator stock={product.stockCount} />
                      </td>
                      <td className="num nowrap">
                        <div className="button-row end">
                          <button type="button" className="btn btn-secondary btn-sm" onClick={() => startEdit(product)}>
                            Edit
                          </button>
                          <button
                            type="button"
                            className="btn btn-danger-outline btn-sm"
                            onClick={() => setToDelete(product)}
                            disabled={remove.busy}
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {visible.length === 0 && (
                    <tr>
                      <td colSpan="6" className="muted center padded">
                        No products match “{search}”.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>

      <ConfirmDialog
        open={toDelete !== null}
        title={`Delete “${toDelete?.name}”?`}
        message="The product will be removed from the catalogue. This cannot be undone."
        confirmLabel="Delete product"
        cancelLabel="Cancel"
        danger
        busy={remove.busy}
        onConfirm={confirmDelete}
        onCancel={() => setToDelete(null)}
      />
    </>
  );
}
