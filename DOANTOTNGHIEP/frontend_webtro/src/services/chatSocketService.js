import env from '@/config/env';
import tokenService from '@/services/tokenService';
import { requestTokenRefresh } from '@/api/axiosClient';

/**
 * Một kết nối WebSocket chat DÙNG CHUNG cho cả ứng dụng (mở khi đăng nhập, đóng khi đăng xuất).
 *
 * Server đẩy 2 loại sự kiện:
 * - CONVERSATION_UPDATED: tới mọi kết nối của user, ở bất kỳ trang nào (hội thoại mới, tin cuối,
 *   số chưa đọc) -> onConversationUpdate.
 * - MESSAGE_CREATED: chỉ cho hội thoại đã SUBSCRIBE (hội thoại đang mở) -> onMessage.
 *
 * Mất kết nối -> tự kết nối lại sau 2s, gửi lại SUBSCRIBE các hội thoại đang mở và báo onReconnect
 * để các màn tải lại dữ liệu bị lỡ trong lúc mất kết nối.
 */

const WS_PATH = '/ws/chat';
const RECONNECT_DELAY_MS = 2000;

const toWsProtocol = (protocol) => (protocol === 'https:' ? 'wss:' : 'ws:');

const buildWsUrl = () => {
  if (env.wsBaseUrl) {
    if (env.wsBaseUrl.startsWith('ws')) {
      return env.wsBaseUrl;
    }
    const { protocol, host } = window.location;
    return `${toWsProtocol(protocol)}//${host}${env.wsBaseUrl}`;
  }

  if (env.apiBaseUrl?.startsWith('http')) {
    const apiUrl = new URL(env.apiBaseUrl);
    apiUrl.protocol = toWsProtocol(apiUrl.protocol);
    apiUrl.pathname = WS_PATH;
    apiUrl.search = '';
    apiUrl.hash = '';
    return apiUrl.toString();
  }

  const { protocol, host } = window.location;
  return `${toWsProtocol(protocol)}//${host}${WS_PATH}`;
};

const normalizeMessage = (message, currentUserId) => {
  const senderId = Number(message?.senderId);
  return {
    ...message,
    senderId,
    conversationId: Number(message?.conversationId),
    sentByMe: String(senderId) === String(currentUserId),
  };
};

let socket = null;
let wanted = false; // true giữa connect() và disconnect()
let currentUserId = null;
let reconnectTimer = null;
let hasConnectedBefore = false;

const subscriptions = new Map(); // conversationId -> số màn đang subscribe
const messageListeners = new Set();
const conversationListeners = new Set();
const reconnectListeners = new Set();

const sendRaw = (payload) => {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(payload));
  }
};

const scheduleReconnect = () => {
  if (wanted && !reconnectTimer) {
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null;
      open();
    }, RECONNECT_DELAY_MS);
  }
};

const open = async () => {
  if (!wanted) return;
  // Access token chỉ sống 15 phút: hết hạn thì làm mới trước, tránh handshake bị 401 lặp lại.
  if (!tokenService.isAccessValid() && tokenService.hasRefresh()) {
    try {
      await requestTokenRefresh();
    } catch {
      scheduleReconnect();
      return;
    }
  }
  const token = tokenService.get();
  if (!wanted || !token) return;

  const url = new URL(buildWsUrl());
  url.searchParams.set('token', token);
  const ws = new WebSocket(url.toString());
  socket = ws;

  ws.onopen = () => {
    subscriptions.forEach((_, conversationId) => sendRaw({ type: 'SUBSCRIBE', conversationId }));
    if (hasConnectedBefore) reconnectListeners.forEach((fn) => fn());
    hasConnectedBefore = true;
  };

  ws.onmessage = (event) => {
    let payload;
    try {
      payload = JSON.parse(event.data);
    } catch {
      return;
    }
    if (payload.type === 'MESSAGE_CREATED') {
      const message = normalizeMessage(payload.message, currentUserId);
      messageListeners.forEach((fn) => fn(message));
    } else if (payload.type === 'CONVERSATION_UPDATED') {
      conversationListeners.forEach((fn) => fn(payload.conversation, Number(payload.totalUnread || 0)));
    }
  };

  ws.onclose = () => {
    if (socket === ws) socket = null;
    scheduleReconnect();
  };
};

/** Mở kết nối cho user đang đăng nhập (gọi lại khi đã mở thì bỏ qua). */
export const connect = (userId) => {
  currentUserId = userId;
  if (wanted) return;
  wanted = true;
  hasConnectedBefore = false;
  open();
};

/** Đóng kết nối (đăng xuất). Giữ nguyên các listener của component đang mount. */
export const disconnect = () => {
  wanted = false;
  currentUserId = null;
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  if (socket && socket.readyState <= WebSocket.OPEN) {
    socket.close();
  }
  socket = null;
};

/** Nhận MESSAGE_CREATED của một hội thoại. Trả hàm hủy. */
export const subscribeConversation = (conversationId, onMessage) => {
  const id = Number(conversationId);
  const listener = (message) => {
    if (message.conversationId === id) onMessage(message);
  };
  messageListeners.add(listener);
  const count = subscriptions.get(id) || 0;
  subscriptions.set(id, count + 1);
  if (count === 0) sendRaw({ type: 'SUBSCRIBE', conversationId: id });

  return () => {
    messageListeners.delete(listener);
    const left = (subscriptions.get(id) || 1) - 1;
    if (left <= 0) {
      subscriptions.delete(id);
      sendRaw({ type: 'UNSUBSCRIBE', conversationId: id });
    } else {
      subscriptions.set(id, left);
    }
  };
};

/** Nhận CONVERSATION_UPDATED: fn(conversation, totalUnread). Trả hàm hủy. */
export const onConversationUpdate = (fn) => {
  conversationListeners.add(fn);
  return () => conversationListeners.delete(fn);
};

/** Được gọi mỗi lần kết nối LẠI thành công (không gọi ở lần kết nối đầu). Trả hàm hủy. */
export const onReconnect = (fn) => {
  reconnectListeners.add(fn);
  return () => reconnectListeners.delete(fn);
};

export default { connect, disconnect, subscribeConversation, onConversationUpdate, onReconnect };
