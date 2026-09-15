import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import ErrorMessage from './ErrorMessage';
import { useCart } from '../context/CartContext';

export default function Layout() {
  const { error: cartError, loading: cartLoading, initCart } = useCart();

  return (
    <div className="app-shell">
      <Navbar />
      <main className="container">
        {cartError && !cartLoading && (
          <ErrorMessage error={cartError} onRetry={() => initCart()}>
            <p className="alert-detail">The cart could not be loaded, so items cannot be added yet.</p>
          </ErrorMessage>
        )}
        <Outlet />
      </main>
    </div>
  );
}
