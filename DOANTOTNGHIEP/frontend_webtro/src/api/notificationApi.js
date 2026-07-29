import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/** API thông báo (khớp docs/03 mục Notification). */
const notificationApi = {
  getList: (params) => unwrap(axiosClient.get('/notifications', { params })),
  getRecent: () => unwrap(axiosClient.get('/notifications', { params: { page: 0, size: 8 } })),
  getUnreadCount: () => unwrap(axiosClient.get('/notifications/unread-count')),
  markAsRead: (id) => unwrap(axiosClient.put(`/notifications/${id}/read`)),
  markAllAsRead: () => unwrap(axiosClient.put('/notifications/read-all')),
  getPreferences: () => unwrap(axiosClient.get('/notifications/preferences')),
  updatePreferences: (payload) => unwrap(axiosClient.put('/notifications/preferences', payload)),
};

export default notificationApi;
