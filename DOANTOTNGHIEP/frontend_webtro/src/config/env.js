/**
 * Đọc biến môi trường Vite. Vite nhúng các biến VITE_* vào bundle lúc BUILD (không phải runtime),
 * nên mọi cấu hình phía client phải đi qua đây.
 *
 * Mặc định VITE_API_BASE_URL = "/api" -> gọi qua reverse proxy của Nginx, không hardcode host
 * backend vào bundle (xem docker-compose.yml + nginx.conf).
 */
export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '/api',
  appName: import.meta.env.VITE_APP_NAME || 'Webtro',
  isDev: import.meta.env.DEV,
  isProd: import.meta.env.PROD,
};

export default env;
