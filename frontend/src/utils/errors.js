import { API_BASE_URL } from '../services/api';

/**
 * Turns an Axios error into a user-friendly { status, title, message, fieldErrors }.
 * The backend's own messages are kept as detail because they are written for people (e.g. "Available: 2, Requested: 3").
 */
export function parseApiError(error) {
  if (!error) return null;

  // A plain JavaScript error thrown by our own code, not an HTTP failure
  if (!error.isAxiosError) {
    return { status: 0, title: 'Something went wrong', message: error.message || 'Please try again.' };
  }

  if (!error.response) {
    if (error.code === 'ECONNABORTED') {
      return { status: 0, title: 'Request timed out', message: 'The server took too long to respond. Please try again.' };
    }
    return {
      status: 0,
      title: 'Cannot reach the server',
      message: API_BASE_URL
        ? `Make sure the backend is running at ${API_BASE_URL}.`
        : 'Make sure the Spring Boot backend is running.',
    };
  }

  const { status, data } = error.response;
  const serverMessage = typeof data?.message === 'string' ? data.message : '';

  // The backend always sends a JSON body. A 5xx without one comes from the Vite dev proxy: the backend is not running.
  if (status >= 500 && (!data || typeof data !== 'object')) {
    return {
      status,
      title: 'Cannot reach the backend',
      message: 'The backend is not responding. Make sure the Spring Boot backend is running, then try again.',
    };
  }

  switch (status) {
    case 400:
      if (data?.validationErrors) {
        return {
          status,
          title: 'Please check the form',
          message: 'Some fields are invalid.',
          fieldErrors: data.validationErrors,
        };
      }
      return { status, title: 'Invalid request', message: serverMessage || 'The request could not be processed.' };

    case 402:
      return { status, title: 'Payment failed', message: 'Payment failed. Your reserved stock has been released.' };

    case 404:
      return { status, title: 'Resource not found', message: serverMessage || 'It may have been removed.' };

    case 409:
      return conflictError(data?.error, serverMessage);

    case 410:
      return {
        status,
        title: 'Reservation expired',
        message: 'This reservation has expired. The reserved stock has been returned to inventory.',
      };

    case 504:
      return {
        status,
        title: 'Payment timed out',
        message: 'Payment timed out. The reservation has expired and stock has been released.',
      };

    default:
      return {
        status,
        title: 'Something went wrong',
        message: 'The server could not complete the request. Please try again.',
        // The backend's own explanation, so an unexpected failure can be diagnosed
        detail: serverMessage ? `Server said (${status}): ${serverMessage}` : `HTTP ${status}`,
      };
  }
}

function conflictError(kind, serverMessage) {
  switch (kind) {
    case 'Insufficient Stock':
      return {
        status: 409,
        isStockConflict: true,
        title: 'Stock is no longer available',
        message:
          'The inventory was updated because another transaction completed first. Another customer may have purchased this product.',
        detail: serverMessage,
      };
    case 'Duplicate Submission':
      return { status: 409, title: 'Already submitted', message: serverMessage || 'This request was already processed.' };
    case 'Invalid Order State':
      return { status: 409, title: 'Action not allowed', message: serverMessage || 'The order can no longer do this.' };
    case 'Concurrent Update':
      return {
        status: 409,
        title: 'Busy — please retry',
        message: 'Another request is updating the same data right now. Nothing was changed, so it is safe to try again.',
      };
    default:
      return { status: 409, title: 'Conflict', message: serverMessage || 'The request conflicts with the current data.' };
  }
}

export function isStockConflict(error) {
  return Boolean(parseApiError(error)?.isStockConflict);
}
