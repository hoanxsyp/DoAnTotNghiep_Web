/**
 * Quản lý access token phía client.
 *
 * Quyết định (canonical mục 8): access token giữ TRONG MEMORY (biến module), KHÔNG localStorage.
 * Refresh token nằm trong cookie httpOnly do backend đặt — JavaScript không đọc được, nên không
 * có hàm nào ở đây đụng tới refresh token. Mất access token khi F5 là ĐÚNG: bootstrapAuth() sẽ
 * gọi /api/auth/refresh (cookie tự gửi) để lấy access token mới.
 *
 * Vì access token không ở localStorage, một lỗ XSS chỉ lấy được token sống <= 15 phút và không
 * gia hạn được — thiệt hại có trần.
 */

let accessToken = null;

export const tokenService = {
  get() {
    return accessToken;
  },
  set(token) {
    accessToken = token;
  },
  clear() {
    accessToken = null;
  },
  has() {
    return Boolean(accessToken);
  },
};

export default tokenService;
