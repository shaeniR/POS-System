import api from './api';

export const PAYMENT_OUTCOMES = ['SUCCESS', 'FAILURE', 'TIMEOUT'];

/** A unique key per payment attempt; the backend rejects a second request with the same key as a duplicate. */
export function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/**
 * Pays for a RESERVED order through the backend's mock gateway.
 * Failed (402), timed-out (504) and refunded (409) payments still return a payment record,
 * so those are returned as results rather than thrown.
 */
export async function payForOrder(orderId, simulatedOutcome, idempotencyKey) {
  try {
    const { data } = await api.post(
      `/api/orders/${orderId}/payments`,
      { simulatedOutcome },
      { headers: { 'Idempotency-Key': idempotencyKey } }
    );
    return data;
  } catch (error) {
    if (error.response?.data?.paymentId) {
      return error.response.data;
    }
    throw error;
  }
}

export async function getPayments(orderId) {
  const { data } = await api.get(`/api/orders/${orderId}/payments`);
  return data;
}
