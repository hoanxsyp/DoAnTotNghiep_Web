import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API xác thực (khớp docs/03_THIET_KE_API.md mục 12.1). Chỉ gọi HTTP + bóc envelope, không chứa
 * logic nghiệp vụ (canonical luật F2).
 */
const authApi = {
  register: (payload) => unwrap(axiosClient.post('/auth/register', payload)),
  login: (payload) => unwrap(axiosClient.post('/auth/login', payload)),
  logout: () => unwrap(axiosClient.post('/auth/logout')),
  refresh: () => unwrap(axiosClient.post('/auth/refresh')),
  me: () => unwrap(axiosClient.get('/users/me')),
  forgotPassword: (payload) => unwrap(axiosClient.post('/auth/forgot-password', payload)),
  resetPassword: (payload) => unwrap(axiosClient.post('/auth/reset-password', payload)),
  changePassword: (payload) => unwrap(axiosClient.post('/auth/change-password', payload)),
  verifyEmail: (payload) => unwrap(axiosClient.post('/auth/verify-email', payload)),
  resendVerification: (payload) => unwrap(axiosClient.post('/auth/resend-verification', payload)),
};

export default authApi;
