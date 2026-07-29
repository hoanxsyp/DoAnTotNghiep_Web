import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API người dùng & theo dõi chủ trọ (khớp docs/03 mục 4.2). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const userApi = {
  getMe: () => unwrap(axiosClient.get('/users/me')),
  updateMe: (payload) => unwrap(axiosClient.put('/users/me', payload)),
  getPublicProfile: (id) => unwrap(axiosClient.get(`/users/${id}`)),
  getPublicListings: (id, params) => unwrap(axiosClient.get(`/users/${id}/listings`, { params })),
  follow: (id) => unwrap(axiosClient.post(`/users/${id}/follow`)),
  unfollow: (id) => unwrap(axiosClient.delete(`/users/${id}/follow`)),
  getFollowing: (params) => unwrap(axiosClient.get('/users/me/following', { params })),
  updateAvatar: (formData) =>
    unwrap(axiosClient.post('/users/me/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })),
  deleteAvatar: () => unwrap(axiosClient.delete('/users/me/avatar')),
  updateContact: (payload) => unwrap(axiosClient.patch('/users/me/contact', payload)),

  // ----- Hồ sơ chủ trọ (docs/03 mục 4.2.10–4.2.12) -----
  getLandlordProfile: () => unwrap(axiosClient.get('/users/me/landlord-profile')),
  updateLandlordProfile: (payload) => unwrap(axiosClient.put('/users/me/landlord-profile', payload)),
  requestLandlordVerification: (payload) =>
    unwrap(axiosClient.post('/users/me/landlord-verification', payload)),

  // ----- Tổng quan chủ trọ -----
  getLandlordDashboard: (params) => unwrap(axiosClient.get('/landlord/dashboard', { params })),
};

export default userApi;
