import axios from 'axios';

/**
 * Base URL of the Spring Boot backend — the single place to change it.
 *
 * - Development: leave VITE_API_BASE_URL unset. Requests go to /api on the Vite dev server, which forwards them
 *   to the backend (see vite.config.js), so the browser needs no CORS permission.
 * - Deployment: set VITE_API_BASE_URL in .env to the backend's public URL, e.g. https://pos-api.example.com
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  // Allow for a simulated gateway TIMEOUT and for free-tier hosting waking up from sleep (can take about a minute)
  timeout: 90000,
});

export default api;
