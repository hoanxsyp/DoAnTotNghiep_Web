import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API tin đăng + tìm kiếm (khớp docs/03 mục 12.3, 12.4). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const listingApi = {
  // ----- Công khai -----
  search: (params) => unwrap(axiosClient.get('/search/listings', { params })),
  getDetail: (id) => unwrap(axiosClient.get(`/listings/${id}`)),
  getBySlug: (slugId) => unwrap(axiosClient.get(`/listings/${slugId}`)),
  getRelated: (id) => unwrap(axiosClient.get(`/listings/${id}/related`)),
  getSuggested: (params) => unwrap(axiosClient.get('/listings/suggested', { params })),

  // ----- Chủ trọ -----
  create: (payload) => unwrap(axiosClient.post('/listings', payload)),
  update: (id, payload) => unwrap(axiosClient.put(`/listings/${id}`, payload)),
  remove: (id) => unwrap(axiosClient.delete(`/listings/${id}`)),
  submit: (id) => unwrap(axiosClient.post(`/listings/${id}/submit`)),
  hide: (id) => unwrap(axiosClient.post(`/listings/${id}/hide`)),
  unhide: (id) => unwrap(axiosClient.post(`/listings/${id}/unhide`)),
  close: (id, payload) => unwrap(axiosClient.post(`/listings/${id}/close`, payload)),
  renew: (id) => unwrap(axiosClient.post(`/listings/${id}/renew`)),
  getMyListings: (params) => unwrap(axiosClient.get('/listings/my', { params })),
  getStats: (id) => unwrap(axiosClient.get(`/listings/${id}/stats`)),
  // Backend không có /listings/{id}/contacts; dùng /landlord/contacts (lọc theo listingId).
  getContacts: (id, params) =>
    unwrap(axiosClient.get('/landlord/contacts', { params: { ...params, listingId: id } })),

  // ----- Ảnh -----
  uploadImages: (id, formData) =>
    unwrap(axiosClient.post(`/listings/${id}/images`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })),
  deleteImage: (id, imageId) => unwrap(axiosClient.delete(`/listings/${id}/images/${imageId}`)),

  // ----- AI dự đoán giá -----
  predictPrice: (payload) => unwrap(axiosClient.post('/ai/price-prediction', payload)),
};

export default listingApi;
