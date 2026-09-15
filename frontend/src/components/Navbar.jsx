import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useCart } from '../context/CartContext';

const LINKS = [
  { to: '/', label: 'Products', end: true },
  { to: '/cart', label: 'Cart', showCount: true },
  { to: '/orders', label: 'Orders' },
  { to: '/inventory', label: 'Inventory' },
  { to: '/admin/products', label: 'Product Management' },
  { to: '/concurrency-test', label: 'Concurrency Test' },
];

export default function Navbar() {
  const { itemCount, loading } = useCart();
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <header className="navbar">
      <div className="navbar-inner">
        <NavLink to="/" className="brand" onClick={() => setMenuOpen(false)}>
          <span className="brand-mark" aria-hidden="true">
            ▦
          </span>
          <span>
            POS System
            <small>Order &amp; Inventory</small>
          </span>
        </NavLink>

        <button
          type="button"
          className="nav-toggle"
          aria-expanded={menuOpen}
          aria-controls="main-nav"
          onClick={() => setMenuOpen(!menuOpen)}
        >
          {menuOpen ? '✕' : '☰'}
          <span className="sr-only">Menu</span>
        </button>

        <nav id="main-nav" className={`nav-links${menuOpen ? ' open' : ''}`}>
          {LINKS.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
              onClick={() => setMenuOpen(false)}
            >
              {link.label}
              {link.showCount && (
                <span className="nav-count" aria-label={`${itemCount} items in cart`}>
                  {loading ? '…' : itemCount}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
      </div>
    </header>
  );
}
