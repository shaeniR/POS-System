import { formatMoney } from '../utils/format';

export default function CartItem({ item, busy = false, onIncrease, onDecrease, onRemove }) {
  return (
    <div className={`cart-item${busy ? ' is-busy' : ''}`}>
      <div className="cart-item-info">
        <div className="cart-item-name">{item.productName}</div>
        <div className="muted">{formatMoney(item.unitPrice)} each</div>
      </div>

      <div className="qty-stepper" aria-label={`Quantity of ${item.productName}`}>
        <button
          type="button"
          onClick={() => onDecrease(item)}
          disabled={busy}
          aria-label={item.quantity <= 1 ? 'Remove item' : 'Decrease quantity'}
        >
          −
        </button>
        <span className="qty-value">{item.quantity}</span>
        <button type="button" onClick={() => onIncrease(item)} disabled={busy} aria-label="Increase quantity">
          +
        </button>
      </div>

      <div className="cart-item-subtotal">{formatMoney(item.subtotal)}</div>

      <button type="button" className="btn-link danger" onClick={() => onRemove(item)} disabled={busy}>
        Remove
      </button>
    </div>
  );
}
