import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API lưu tin (khớp docs/03 mục 4.5.1-4.5.3). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const favoriteApi = {
  add: (listingId) => unwrap(axiosClient.post('/favorites', { listingId })),
  remove: (listingId) => unwrap(axiosClient.delete(`/favorites/${listingId}`)),
  getMyFavorites: (params) => unwrap(axiosClient.get('/favorites', { params })),
};

export default favoriteApi;
