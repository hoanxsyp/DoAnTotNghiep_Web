import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/** API trò chuyện. */
const conversationApi = {
  getConversations: (params) => unwrap(axiosClient.get('/conversations', { params })),
  createConversation: (payload) => unwrap(axiosClient.post('/conversations', payload)),
  getMessages: (conversationId, params) =>
    unwrap(axiosClient.get(`/conversations/${conversationId}/messages`, { params })),
  sendMessage: (conversationId, payload) =>
    unwrap(axiosClient.post(`/conversations/${conversationId}/messages`, payload)),
  markRead: (conversationId) => unwrap(axiosClient.post(`/conversations/${conversationId}/read`)),
};

export default conversationApi;
