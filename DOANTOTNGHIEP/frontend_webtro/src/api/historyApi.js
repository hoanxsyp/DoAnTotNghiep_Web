import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API lịch sử xem tin (khớp docs/03 mục 4.5.4-4.5.5). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const historyApi = {
  getViews: (params) => unwrap(axiosClient.get('/history/views', { params })),
  clearViews: () => unwrap(axiosClient.delete('/history/views')),
  deleteView: (id) => unwrap(axiosClient.delete(`/history/views/${id}`)),
};

export default historyApi;
