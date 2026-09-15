import { formatMoney } from '../utils/format';

export default function OrderItemsTable({ items, totalAmount }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Product</th>
            <th className="num">Unit price</th>
            <th className="num">Qty</th>
            <th className="num">Subtotal</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id ?? item.productId}>
              <td className="strong">{item.productName}</td>
              <td className="num">{formatMoney(item.unitPrice)}</td>
              <td className="num">{item.quantity}</td>
              <td className="num">{formatMoney(item.subtotal)}</td>
            </tr>
          ))}
        </tbody>
        {totalAmount != null && (
          <tfoot>
            <tr>
              <td colSpan="3">Total</td>
              <td className="num">{formatMoney(totalAmount)}</td>
            </tr>
          </tfoot>
        )}
      </table>
    </div>
  );
}
