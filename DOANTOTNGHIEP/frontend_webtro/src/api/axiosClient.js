import axios from 'axios';
import env from '@/config/env';
import tokenService from '@/services/tokenService';

/**
 * Instance axios dùng chung. Hai interceptor:
 *  1. Request: gắn Authorization: Bearer <access token> nếu có.
 *  2. Response: khi gặp 401, tự gọi /auth/refresh MỘT LẦN (gửi refresh token từ localStorage trong
 *     body), lấy CẶP token mới rồi phát lại request gốc. Nhiều request 401 cùng lúc chỉ kích hoạt
 *     một lần refresh nhờ hàng đợi `pendingQueue` (canonical mục 4.4 - chống race refresh).
 *
 * Không dùng cookie: backend nhận token qua header/body nên không cần `withCredentials`.
 */
const axiosClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

let isRefreshing = false;
let pendingQueue = [];

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

const requestTokenRefresh = async () => {
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      pendingQueue.push({ resolve, reject });
    });
  }

  isRefreshing = true;
  try {
    const resp = await axios.post(`${env.apiBaseUrl}/auth/refresh`, {
      refreshToken: tokenService.getRefresh(),
    });
    const data = resp.data?.data || {};
    const newToken = data.accessToken;
    if (!newToken) {
      throw new Error('Refresh không trả về access token');
    }
    tokenService.setTokens({
      accessToken: newToken,
      refreshToken: data.refreshToken,
      refreshExpiresIn: data.refreshExpiresIn,
    });
    flushQueue(null, newToken);
    return newToken;
  } catch (refreshError) {
    flushQueue(refreshError, null);
    throw refreshError;
  } finally {
    isRefreshing = false;
  }
};

// ---- Request: gắn access token ----
axiosClient.interceptors.request.use(
  async (config) => {
    const isRefreshCall = config.url?.includes('/auth/refresh');
    let token = tokenService.get();
    if (!isRefreshCall && tokenService.shouldRenewRefresh(15 * 60)) {
      try {
        token = await requestTokenRefresh();
      } catch {
        // Access token vẫn còn hạn: request hiện tại tiếp tục, phiên chỉ bị dọn khi cả access/refresh đều không dùng được.
        token = tokenService.get();
      }
    }
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// ---- Response: tự refresh khi 401 ----
// Cho phép App đăng ký callback dọn phiên khi refresh thất bại (đăng xuất cứng).
let onAuthFailure = null;
export const setAuthFailureHandler = (handler) => {
  onAuthFailure = handler;
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

    // Không có refresh token thì không có gì để làm mới.
    if (!tokenService.hasRefresh()) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      const newToken = await requestTokenRefresh();
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
