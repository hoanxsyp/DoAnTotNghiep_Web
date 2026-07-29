import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API thanh toán & gói đẩy tin (khớp docs/03 mục 4.9). Chỉ gọi HTTP + bóc envelope (luật F2).
 *
 * `POST /payments` và `POST /listings/{id}/promote` yêu cầu header `Idempotency-Key` (docs/03 mục
 * 1.7): truyền qua tham số `config` thứ hai, ví dụ `createPayment(body, { headers: { 'Idempotency-Key': uuid } })`.
 */
const paymentApi = {
  getPackages: (params) => unwrap(axiosClient.get('/promotion-packages', { params })),
  createPayment: (payload, config) => unwrap(axiosClient.post('/payments', payload, config)),
  promote: (listingId, payload, config) =>
    unwrap(axiosClient.post(`/listings/${listingId}/promote`, payload, config)),
  getMyPayments: (params) => unwrap(axiosClient.get('/payments/my', { params })),
  getPayment: (id) => unwrap(axiosClient.get(`/payments/${id}`)),
  cancelPayment: (id) => unwrap(axiosClient.post(`/payments/${id}/cancel`)),
  getMySubscriptions: (params) =>
    unwrap(axiosClient.get('/promotion-subscriptions/my', { params })),
  validateCoupon: (payload) => unwrap(axiosClient.post('/coupons/validate', payload)),
};

export default paymentApi;
