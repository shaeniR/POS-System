const LOW_STOCK_THRESHOLD = 5;

/** Visual stock level based on the stock count returned by the backend. */
export default function StockIndicator({ stock }) {
  if (stock <= 0) {
    return <span className="stock stock-out">Out of stock</span>;
  }
  if (stock <= LOW_STOCK_THRESHOLD) {
    return <span className="stock stock-low">Low stock · {stock} left</span>;
  }
  return <span className="stock stock-ok">In stock · {stock}</span>;
}
