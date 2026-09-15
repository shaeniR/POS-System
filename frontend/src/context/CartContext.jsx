import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import * as cartService from '../services/cartService';

const CART_ID_KEY = 'pos.cartId';

const CartContext = createContext(null);

function readStoredCartId() {
  try {
    return localStorage.getItem(CART_ID_KEY);
  } catch {
    return null;
  }
}

function storeCartId(cartId) {
  try {
    localStorage.setItem(CART_ID_KEY, cartId);
  } catch {
    // Storage unavailable (e.g. private mode): the cart just won't survive a page reload
  }
}

/**
 * Loads the saved cart, or creates a new one if there is none or the backend no longer has it (404).
 * An existing cart is read with GET: the backend's POST /api/cart?cartId=... returns 500 for carts that already exist.
 */
async function loadOrCreateCart(cartId) {
  if (cartId) {
    try {
      return await cartService.getCart(cartId);
    } catch (e) {
      if (e.response?.status !== 404) throw e;
    }
  }
  return cartService.createOrGetCart();
}

/**
 * Holds the backend cart for this browser. The cart id always comes from the backend
 * and is kept in localStorage so the cart survives page reloads.
 */
export function CartProvider({ children }) {
  const [cart, setCart] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const initStarted = useRef(false);

  // Every cart response carries the id to use from now on (the backend may have created a new cart)
  const applyCart = useCallback((data) => {
    storeCartId(data.cartId);
    setCart(data);
    return data;
  }, []);

  const initCart = useCallback(
    async (cartId = readStoredCartId()) => {
      setLoading(true);
      setError(null);
      try {
        return applyCart(await loadOrCreateCart(cartId));
      } catch (e) {
        setError(e);
        return null;
      } finally {
        setLoading(false);
      }
    },
    [applyCart]
  );

  useEffect(() => {
    // React StrictMode runs effects twice in development; create the cart only once
    if (initStarted.current) return;
    initStarted.current = true;
    initCart();
  }, [initCart]);

  const requireCartId = useCallback(() => {
    if (!cart?.cartId) throw new Error('The cart is not ready yet.');
    return cart.cartId;
  }, [cart]);

  const refreshCart = useCallback(async () => {
    const cartId = cart?.cartId ?? readStoredCartId();
    if (!cartId) return initCart(null);
    try {
      return applyCart(await cartService.getCart(cartId));
    } catch (e) {
      if (e.response?.status === 404) return initCart(null);
      throw e;
    }
  }, [cart, applyCart, initCart]);

  const addItem = useCallback(
    async (productId, quantity = 1) => applyCart(await cartService.addCartItem(requireCartId(), productId, quantity)),
    [applyCart, requireCartId]
  );

  const removeItem = useCallback(
    async (itemId) => applyCart(await cartService.removeCartItem(requireCartId(), itemId)),
    [applyCart, requireCartId]
  );

  const increaseItem = useCallback((item) => addItem(item.productId, 1), [addItem]);

  /**
   * The backend has no "set quantity" endpoint, so a decrease removes the line and adds it back with one less.
   * If adding it back fails, the cart is reloaded so the screen shows what the backend actually holds.
   */
  const decreaseItem = useCallback(
    async (item) => {
      const cartId = requireCartId();
      if (item.quantity <= 1) {
        return applyCart(await cartService.removeCartItem(cartId, item.id));
      }
      await cartService.removeCartItem(cartId, item.id);
      try {
        return applyCart(await cartService.addCartItem(cartId, item.productId, item.quantity - 1));
      } catch (e) {
        await refreshCart().catch(() => {});
        throw e;
      }
    },
    [applyCart, refreshCart, requireCartId]
  );

  const clearCart = useCallback(async () => {
    const cartId = requireCartId();
    await cartService.clearCart(cartId);
    return applyCart(await cartService.getCart(cartId));
  }, [applyCart, requireCartId]);

  const value = useMemo(
    () => ({
      cart,
      cartId: cart?.cartId ?? null,
      items: cart?.items ?? [],
      itemCount: (cart?.items ?? []).reduce((sum, item) => sum + item.quantity, 0),
      totalAmount: cart?.totalAmount ?? 0,
      loading,
      error,
      initCart,
      refreshCart,
      addItem,
      removeItem,
      increaseItem,
      decreaseItem,
      clearCart,
    }),
    [cart, loading, error, initCart, refreshCart, addItem, removeItem, increaseItem, decreaseItem, clearCart]
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used inside <CartProvider>');
  return context;
}
