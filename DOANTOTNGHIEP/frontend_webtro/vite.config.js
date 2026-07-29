import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  // Khi chay `npm run dev` ngoai Docker, backend nam o localhost:8080.
  // Khi build trong Docker, VITE_API_BASE_URL = "/api" va Nginx lam reverse
  // proxy -> khong can proxy cua Vite nua.
  const devApiTarget = env.VITE_DEV_API_TARGET || 'http://localhost:8080';

  return {
    plugins: [react()],

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    server: {
      port: Number(env.VITE_DEV_PORT) || 5173,
      host: true,
      strictPort: false,
      proxy: {
        '/api': {
          target: devApiTarget,
          changeOrigin: true,
          secure: false,
        },
        '/v3/api-docs': { target: devApiTarget, changeOrigin: true },
        '/swagger-ui': { target: devApiTarget, changeOrigin: true },
      },
    },

    preview: {
      port: Number(env.VITE_PREVIEW_PORT) || 4173,
      host: true,
    },

    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      // Sourcemap tat o production de khong lo source code.
      sourcemap: mode !== 'production',
      chunkSizeWarningLimit: 1200,
      rollupOptions: {
        output: {
          // Tach vendor thanh cac chunk on dinh -> nguoi dung chi tai lai
          // phan thay doi, khong tai lai ca thu vien moi lan deploy.
          manualChunks(id) {
            if (!id.includes('node_modules')) return undefined;
            if (id.includes('@mui') || id.includes('@emotion')) return 'vendor-mui';
            if (id.includes('chart.js') || id.includes('react-chartjs-2')) return 'vendor-chart';
            if (id.includes('react-router')) return 'vendor-router';
            if (
              id.includes('@reduxjs') ||
              id.includes('react-redux') ||
              id.includes('immer') ||
              id.includes('reselect')
            ) {
              return 'vendor-redux';
            }
            if (id.includes('react-hook-form') || id.includes('yup') || id.includes('@hookform')) {
              return 'vendor-form';
            }
            if (id.includes('/react/') || id.includes('/react-dom/') || id.includes('scheduler')) {
              return 'vendor-react';
            }
            return 'vendor';
          },
        },
      },
    },

    esbuild: {
      // Bo console/debugger khoi ban production.
      drop: mode === 'production' ? ['console', 'debugger'] : [],
    },
  };
});
