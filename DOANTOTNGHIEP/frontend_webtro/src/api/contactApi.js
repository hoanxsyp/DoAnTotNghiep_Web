import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API liên hệ & trò chuyện (khớp docs/03 mục 4.6). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const contactApi = {
  contactListing: (id, payload) => unwrap(axiosClient.post(`/listings/${id}/contact`, payload)),
  getLandlordContacts: (params) => unwrap(axiosClient.get('/landlord/contacts', { params })),
  getConversations: (params) => unwrap(axiosClient.get('/conversations', { params })),
  createConversation: (payload) => unwrap(axiosClient.post('/conversations', payload)),
  getMessages: (convId, params) =>
    unwrap(axiosClient.get(`/conversations/${convId}/messages`, { params })),
  sendMessage: (convId, payload) =>
    unwrap(axiosClient.post(`/conversations/${convId}/messages`, payload)),
  getConversation: (convId) => unwrap(axiosClient.get(`/conversations/${convId}`)),
  markRead: (convId) => unwrap(axiosClient.post(`/conversations/${convId}/read`)),
  getContactInfo: (listingId) => unwrap(axiosClient.get(`/listings/${listingId}/contact-info`)),
};

export default contactApi;
