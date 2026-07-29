import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API bình luận tin đăng (khớp docs/03 mục 4.7.1-4.7.5). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const commentApi = {
  getComments: (listingId, params) =>
    unwrap(axiosClient.get(`/listings/${listingId}/comments`, { params })),
  create: (listingId, payload) =>
    unwrap(axiosClient.post(`/listings/${listingId}/comments`, payload)),
  update: (id, payload) => unwrap(axiosClient.put(`/comments/${id}`, payload)),
  remove: (id) => unwrap(axiosClient.delete(`/comments/${id}`)),
  reply: (id, payload) => unwrap(axiosClient.post(`/comments/${id}/reply`, payload)),
};

export default commentApi;
