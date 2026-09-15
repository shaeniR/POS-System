import api from './api';

export async function getProducts() {
  const { data } = await api.get('/api/products');
  return data;
}

/** Available and reserved stock for every product. */
export async function getStockOverview() {
  const { data } = await api.get('/api/products/stock');
  return data;
}

export async function getProduct(id) {
  const { data } = await api.get(`/api/products/${id}`);
  return data;
}

export async function getProductStock(id) {
  const { data } = await api.get(`/api/products/${id}/stock`);
  return data;
}

export async function createProduct(product) {
  const { data } = await api.post('/api/products', product);
  return data;
}

export async function updateProduct(id, product) {
  const { data } = await api.put(`/api/products/${id}`, product);
  return data;
}

export async function deleteProduct(id) {
  await api.delete(`/api/products/${id}`);
}
