import env from '@/config/env';
import tokenService from '@/services/tokenService';

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

export const subscribeToConversationMessages = ({
  conversationId,
  currentUserId,
  onMessage,
  onStatus,
  onError,
}) => {
  let socket = null;
  let closedByClient = false;
  let reconnectTimer = null;

  const connect = () => {
    const token = tokenService.get();
    if (!token || !conversationId) {
      onError?.(new Error('Missing chat token or conversation id'));
      return;
    }

    const url = new URL(buildWsUrl());
    url.searchParams.set('token', token);
    socket = new WebSocket(url.toString());

    socket.onopen = () => {
      onStatus?.('open');
      socket.send(JSON.stringify({ type: 'SUBSCRIBE', conversationId }));
    };

    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (payload.type === 'MESSAGE_CREATED' && Number(payload.conversationId) === Number(conversationId)) {
          onMessage?.(normalizeMessage(payload.message, currentUserId));
        }
      } catch (error) {
        onError?.(error);
      }
    };

    socket.onerror = () => {
      onStatus?.('error');
    };

    socket.onclose = () => {
      onStatus?.('closed');
      if (!closedByClient) {
        reconnectTimer = window.setTimeout(connect, RECONNECT_DELAY_MS);
      }
    };
  };

  connect();

  return () => {
    closedByClient = true;
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer);
    }
    if (socket && socket.readyState <= WebSocket.OPEN) {
      socket.close();
    }
  };
};

export default { subscribeToConversationMessages };
