/**
 * Định danh phiên chatbot của trình duyệt — backend BẮT BUỘC khi chưa đăng nhập.
 *
 * Lưu ở sessionStorage: đóng tab/trình duyệt rồi mở lại là phiên chat mới (khung chat trống thì
 * bot cũng không nhớ hội thoại cũ). Đăng xuất thì xóa để người dùng sau có phiên riêng; đăng nhập
 * thì giữ nguyên để backend gắn phiên đang chat lúc là khách vào tài khoản.
 */
const SESSION_KEY = 'webtro.chatbot.sessionId';

/** UUID v4 — crypto.randomUUID chỉ có trên HTTPS/localhost, nên có phương án dự phòng. */
const newUuid = () => {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID();
  const b = window.crypto?.getRandomValues ? window.crypto.getRandomValues(new Uint8Array(16))
    : Uint8Array.from({ length: 16 }, () => Math.floor(Math.random() * 256));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
};

// Bản cũ lưu ở localStorage (dùng mãi một phiên cho cả trình duyệt) — dọn đi.
try {
  window.localStorage.removeItem(SESSION_KEY);
} catch {
  // storage bị chặn
}

/** Storage bị chặn thì dùng tạm trong bộ nhớ. */
let memorySessionId = null;

export const getChatSessionId = () => {
  try {
    let id = window.sessionStorage.getItem(SESSION_KEY);
    if (!id) {
      id = newUuid();
      window.sessionStorage.setItem(SESSION_KEY, id);
    }
    return id;
  } catch {
    memorySessionId = memorySessionId || newUuid();
    return memorySessionId;
  }
};

export const clearChatSession = () => {
  memorySessionId = null;
  try {
    window.sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // storage bị chặn
  }
};
