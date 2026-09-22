import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxy na backend, żeby front chodził na tym samym originie co API.
// Dzięki temu w devie nie ma CORS-a ani drugiego adresu w konfiguracji.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
