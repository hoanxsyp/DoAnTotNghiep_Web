import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API báo cáo vi phạm — phía người dùng (khớp docs/03 mục 4.8). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const reportApi = {
  create: (payload) => unwrap(axiosClient.post('/reports', payload)),
  getMyReports: (params) => unwrap(axiosClient.get('/reports/my', { params })),
};

export default reportApi;
