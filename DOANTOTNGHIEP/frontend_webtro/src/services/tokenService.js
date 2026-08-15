/**
 * Quản lý access token + refresh token phía client.
 *
 * Quyết định: CẢ HAI token đều lưu trong localStorage. Backend không đặt cookie nào nữa — client
 * tự gắn access token vào header `Authorization` và gửi refresh token trong body khi làm mới phiên.
 * Nhờ vậy phiên sống sót qua F5 mà không cần gọi /auth/refresh chỉ để khôi phục access token.
 *
 * Đánh đổi cần biết: token nằm trong localStorage thì JavaScript đọc được, nên một lỗ XSS sẽ lấy
 * được cả refresh token. Bù lại bằng: refresh token xoay vòng mỗi lần dùng + phát hiện tái sử dụng
 * ở backend (dùng lại token cũ là thu hồi cả họ token), và toàn bộ nội dung người dùng nhập đều
 * được strip HTML trước khi lưu.
 *
 * Giá trị được cache trong biến module để `get()` (chạy ở mỗi request interceptor) không phải chạm
 * localStorage liên tục; `syncFromStorage()` nạp lại lúc khởi động app.
 */

const ACCESS_KEY = 'webtro_access_token';
const REFRESH_KEY = 'webtro_refresh_token';
const REFRESH_EXPIRES_AT_KEY = 'webtro_refresh_expires_at';

const hasStorage = () => typeof window !== 'undefined' && Boolean(window.localStorage);

const read = (key) => {
  if (!hasStorage()) return null;
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
};

const write = (key, value) => {
  if (!hasStorage()) return;
  try {
    if (value) {
      window.localStorage.setItem(key, value);
    } else {
      window.localStorage.removeItem(key);
    }
  } catch {
    // Chế độ riêng tư của trình duyệt có thể chặn ghi — bỏ qua, bản cache trong RAM vẫn dùng được.
  }
};

let accessToken = read(ACCESS_KEY);
let refreshToken = read(REFRESH_KEY);
let refreshExpiresAt = Number(read(REFRESH_EXPIRES_AT_KEY)) || null;

const decodeJwtPayload = (token) => {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');
    return JSON.parse(window.atob(padded));
  } catch {
    return null;
  }
};

const secondsUntil = (epochMs) => {
  if (!epochMs) return null;
  return Math.floor((epochMs - Date.now()) / 1000);
};

export const tokenService = {
  get() {
    return accessToken;
  },
  set(token) {
    accessToken = token || null;
    write(ACCESS_KEY, accessToken);
  },
  getRefresh() {
    return refreshToken;
  },
  setRefresh(token) {
    refreshToken = token || null;
    write(REFRESH_KEY, refreshToken);
    if (!refreshToken) {
      refreshExpiresAt = null;
      write(REFRESH_EXPIRES_AT_KEY, null);
    }
  },
  /** Lưu cặp token nhận được từ /auth/login hoặc /auth/refresh. */
  setTokens({ accessToken: access, refreshToken: refresh, refreshExpiresIn } = {}) {
    if (access) this.set(access);
    if (refresh) this.setRefresh(refresh);
    if (Number.isFinite(refreshExpiresIn)) {
      refreshExpiresAt = Date.now() + Number(refreshExpiresIn) * 1000;
      write(REFRESH_EXPIRES_AT_KEY, String(refreshExpiresAt));
    }
  },
  clear() {
    accessToken = null;
    refreshToken = null;
    refreshExpiresAt = null;
    write(ACCESS_KEY, null);
    write(REFRESH_KEY, null);
    write(REFRESH_EXPIRES_AT_KEY, null);
  },
  has() {
    return Boolean(accessToken);
  },
  hasRefresh() {
    return Boolean(refreshToken);
  },
  /** Nạp lại từ localStorage (ví dụ sau khi tab khác đăng nhập/đăng xuất). */
  syncFromStorage() {
    accessToken = read(ACCESS_KEY);
    refreshToken = read(REFRESH_KEY);
    refreshExpiresAt = Number(read(REFRESH_EXPIRES_AT_KEY)) || null;
  },
  accessSecondsRemaining() {
    const payload = decodeJwtPayload(accessToken);
    return payload?.exp ? payload.exp - Math.floor(Date.now() / 1000) : null;
  },
  refreshSecondsRemaining() {
    return secondsUntil(refreshExpiresAt);
  },
  isAccessValid() {
    const remaining = this.accessSecondsRemaining();
    return remaining !== null && remaining > 0;
  },
  shouldRenewRefresh(thresholdSeconds = 900) {
    const remaining = this.refreshSecondsRemaining();
    return remaining !== null && remaining > 0 && remaining <= thresholdSeconds && this.isAccessValid();
  },
};

export default tokenService;
