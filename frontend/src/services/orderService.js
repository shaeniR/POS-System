import api from './api';

/** Converts the cart into a RESERVED order. The backend deducts (reserves) stock and empties the cart. */
export async function checkoutCart(cartId) {
  const { data } = await api.post(`/api/orders/checkout/${cartId}`);
  return data;
}

export async function getOrders() {
  const { data } = await api.get('/api/orders');
  return data;
}

/** Fetching an order also makes the backend expire it right away if its reservation has run out. */
export async function getOrder(id) {
  const { data } = await api.get(`/api/orders/${id}`);
  return data;
}

export async function getOrderByNumber(orderNumber) {
  const { data } = await api.get(`/api/orders/number/${encodeURIComponent(orderNumber)}`);
  return data;
}

/** Returns the updated order. */
export async function cancelOrder(id) {
  const { data } = await api.post(`/api/orders/${id}/cancel`);
  return data;
}
