import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // Where the Spring Boot backend runs locally (server.port in application.properties)
  const backendUrl = env.VITE_BACKEND_URL || 'http://localhost:8081';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      // Forward API calls to the backend so the browser sees a single origin
      proxy: {
        '/api': { target: backendUrl, changeOrigin: true },
      },
    },
  };
});
