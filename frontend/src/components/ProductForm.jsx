import { useState } from 'react';
import { parseApiError } from '../utils/errors';

const EMPTY = { name: '', price: '', stockCount: '' };

/** Quick checks for instant feedback. The backend validates again and its errors are shown per field too. */
function validate(values) {
  const errors = {};
  if (!values.name.trim()) errors.name = 'Product name is required';

  const price = Number(values.price);
  if (values.price === '') errors.price = 'Price is required';
  else if (!Number.isFinite(price) || price <= 0) errors.price = 'Price must be greater than zero';

  const stock = Number(values.stockCount);
  if (values.stockCount === '') errors.stockCount = 'Stock count is required';
  else if (!Number.isInteger(stock)) errors.stockCount = 'Stock count must be a whole number';
  else if (stock < 0) errors.stockCount = 'Stock count cannot be negative';

  return errors;
}

/**
 * Create / edit form. Remount it with a different `key` to load another product or reset it.
 * `serverError` is the Axios error from the last save, if any.
 */
export default function ProductForm({ product, submitting = false, serverError, onSubmit, onCancel }) {
  const [values, setValues] = useState(
    product ? { name: product.name, price: String(product.price), stockCount: String(product.stockCount) } : EMPTY
  );
  const [touched, setTouched] = useState({});
  const [submitted, setSubmitted] = useState(false);

  const clientErrors = validate(values);
  const serverFieldErrors = parseApiError(serverError)?.fieldErrors ?? {};

  const errorFor = (field) =>
    ((submitted || touched[field]) && clientErrors[field]) || serverFieldErrors[field] || '';

  const change = (field) => (e) => setValues({ ...values, [field]: e.target.value });
  const blur = (field) => () => setTouched({ ...touched, [field]: true });

  const submit = (e) => {
    e.preventDefault();
    setSubmitted(true);
    if (Object.keys(clientErrors).length > 0) return;
    onSubmit({
      name: values.name.trim(),
      price: Number(values.price),
      stockCount: Number(values.stockCount),
    });
  };

  return (
    <form className="form" onSubmit={submit} noValidate>
      <div className="field">
        <label htmlFor="product-name">Product name</label>
        <input
          id="product-name"
          value={values.name}
          onChange={change('name')}
          onBlur={blur('name')}
          placeholder="e.g. Coca-Cola 330ml"
          className={errorFor('name') ? 'invalid' : ''}
          disabled={submitting}
          autoComplete="off"
        />
        {errorFor('name') && <span className="field-error">{errorFor('name')}</span>}
      </div>

      <div className="field-row">
        <div className="field">
          <label htmlFor="product-price">Price</label>
          <input
            id="product-price"
            type="number"
            inputMode="decimal"
            step="0.01"
            min="0.01"
            value={values.price}
            onChange={change('price')}
            onBlur={blur('price')}
            placeholder="0.00"
            className={errorFor('price') ? 'invalid' : ''}
            disabled={submitting}
          />
          {errorFor('price') && <span className="field-error">{errorFor('price')}</span>}
        </div>

        <div className="field">
          <label htmlFor="product-stock">Stock count</label>
          <input
            id="product-stock"
            type="number"
            inputMode="numeric"
            step="1"
            min="0"
            value={values.stockCount}
            onChange={change('stockCount')}
            onBlur={blur('stockCount')}
            placeholder="0"
            className={errorFor('stockCount') ? 'invalid' : ''}
            disabled={submitting}
          />
          {errorFor('stockCount') && <span className="field-error">{errorFor('stockCount')}</span>}
        </div>
      </div>

      <div className="button-row">
        <button type="submit" className="btn btn-primary" disabled={submitting}>
          {submitting ? 'Saving…' : product ? 'Save changes' : 'Add product'}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}
