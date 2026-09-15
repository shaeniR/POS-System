import { Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import Products from './pages/Products';
import Cart from './pages/Cart';
import Checkout from './pages/Checkout';
import Payment from './pages/Payment';
import Orders from './pages/Orders';
import OrderDetails from './pages/OrderDetails';
import Inventory from './pages/Inventory';
import ProductManagement from './pages/ProductManagement';
import ConcurrencyTest from './pages/ConcurrencyTest';
import NotFound from './pages/NotFound';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Products />} />
        <Route path="/cart" element={<Cart />} />
        <Route path="/checkout" element={<Checkout />} />
        <Route path="/payment/:orderId" element={<Payment />} />
        <Route path="/orders" element={<Orders />} />
        <Route path="/orders/:id" element={<OrderDetails />} />
        <Route path="/inventory" element={<Inventory />} />
        <Route path="/admin/products" element={<ProductManagement />} />
        <Route path="/concurrency-test" element={<ConcurrencyTest />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
