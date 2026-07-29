import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API đánh giá tin đăng (khớp docs/03 mục 4.7.6-4.7.9). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const reviewApi = {
  getReviews: (listingId, params) =>
    unwrap(axiosClient.get(`/listings/${listingId}/reviews`, { params })),
  create: (listingId, payload) =>
    unwrap(axiosClient.post(`/listings/${listingId}/reviews`, payload)),
  update: (id, payload) => unwrap(axiosClient.put(`/reviews/${id}`, payload)),
  remove: (id) => unwrap(axiosClient.delete(`/reviews/${id}`)),
  getMyReviews: (params) => unwrap(axiosClient.get('/reviews/my', { params })),
};

export default reviewApi;
