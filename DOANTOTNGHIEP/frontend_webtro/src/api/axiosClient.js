import axios from 'axios';
import env from '@/config/env';
import tokenService from '@/services/tokenService';

/**
 * Instance axios dùng chung. Hai interceptor:
 *  1. Request: gắn Authorization: Bearer <access token> nếu có.
 *  2. Response: khi gặp 401, tự gọi /auth/refresh MỘT LẦN (cookie refresh tự gửi), lấy access
 *     token mới rồi phát lại request gốc. Nhiều request 401 cùng lúc chỉ kích hoạt một lần
 *     refresh nhờ hàng đợi `pendingQueue` (canonical mục 4.4 - chống race refresh).
 */
const axiosClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 30000,
  // Bắt buộc để cookie refresh token (httpOnly, Path=/api/auth) được gửi kèm khi refresh.
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

// ---- Request: gắn access token ----
axiosClient.interceptors.request.use(
  (config) => {
    const token = tokenService.get();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// ---- Response: tự refresh khi 401 ----
let isRefreshing = false;
let pendingQueue = [];

// Cho phép App đăng ký callback dọn phiên khi refresh thất bại (đăng xuất cứng).
let onAuthFailure = null;
export const setAuthFailureHandler = (handler) => {
  onAuthFailure = handler;
};

const flushQueue = (error, token = null) => {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error);
    } else {
      resolve(token);
    }
  });
  pendingQueue = [];
};

axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const status = error.response?.status;

    // Chính endpoint refresh trả 401 -> phiên hết hạn thật, không thử lại.
    const isRefreshCall = originalRequest?.url?.includes('/auth/refresh');
    const isLoginCall = originalRequest?.url?.includes('/auth/login');

    if (status !== 401 || originalRequest?._retry || isRefreshCall || isLoginCall) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      // Đã có một refresh đang chạy: xếp hàng, chờ token mới rồi phát lại.
      return new Promise((resolve, reject) => {
        pendingQueue.push({ resolve, reject });
      })
        .then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return axiosClient(originalRequest);
        })
        .catch((err) => Promise.reject(err));
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      // Gọi thẳng axios gốc (không qua interceptor) để tránh vòng lặp.
      const resp = await axios.post(
        `${env.apiBaseUrl}/auth/refresh`,
        {},
        { withCredentials: true },
      );
      const newToken = resp.data?.data?.accessToken;
      if (!newToken) {
        throw new Error('Refresh không trả về access token');
      }
      tokenService.set(newToken);
      flushQueue(null, newToken);
      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return axiosClient(originalRequest);
    } catch (refreshError) {
      flushQueue(refreshError, null);
      tokenService.clear();
      if (onAuthFailure) {
        onAuthFailure();
      }
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

export default axiosClient;
