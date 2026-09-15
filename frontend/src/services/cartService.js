import api from './api';

/**
 * Returns the cart with the given id, or a new cart if no id is given or it no longer exists.
 * The backend decides which; always use the cartId from the response.
 * Note: the backend currently fails (500) when the id belongs to an existing cart, so load existing carts with getCart.
 */
export async function createOrGetCart(cartId) {
  const { data } = await api.post('/api/cart', null, { params: cartId ? { cartId } : undefined });
  return data;
}

export async function getCart(cartId) {
  const { data } = await api.get(`/api/cart/${cartId}`);
  return data;
}

/** Adds a product; if it is already in the cart the backend increases its quantity. */
export async function addCartItem(cartId, productId, quantity) {
  const { data } = await api.post(`/api/cart/${cartId}/items`, { productId, quantity });
  return data;
}

export async function removeCartItem(cartId, itemId) {
  const { data } = await api.delete(`/api/cart/${cartId}/items/${itemId}`);
  return data;
}

export async function clearCart(cartId) {
  await api.delete(`/api/cart/${cartId}`);
}
