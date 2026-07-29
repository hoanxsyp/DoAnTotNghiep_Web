import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API danh mục / khu vực / tiện ích (khớp docs/03 mục 4.3 công khai và 4.17 quản trị).
 * Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const catalogApi = {
  // ----- Công khai -----
  getCategories: (params) => unwrap(axiosClient.get('/categories', { params })),
  getProvinces: (params) => unwrap(axiosClient.get('/provinces', { params })),
  getDistricts: (provinceId) => unwrap(axiosClient.get(`/provinces/${provinceId}/districts`)),
  getWards: (districtId) => unwrap(axiosClient.get(`/districts/${districtId}/wards`)),
  getAmenities: (params) => unwrap(axiosClient.get('/amenities', { params })),
  // Nội dung tĩnh công khai (trang Giới thiệu / Điều khoản) từ SystemConfig page.about/page.terms.
  getAboutContent: () => unwrap(axiosClient.get('/content/about')),
  getTermsContent: () => unwrap(axiosClient.get('/content/terms')),
  // Backend không expose cấu hình công khai (ngưỡng nghiệp vụ chỉ ở /admin/system-configs cần quyền).
  // Form đăng tin dùng ràng buộc mặc định phía client; trả null để nơi gọi tự fallback.
  getPublicConfigs: () => Promise.resolve(null),

  // ----- Quản trị: danh mục -----
  createCategory: (payload) => unwrap(axiosClient.post('/admin/categories', payload)),
  updateCategory: (id, payload) => unwrap(axiosClient.put(`/admin/categories/${id}`, payload)),
  deleteCategory: (id) => unwrap(axiosClient.delete(`/admin/categories/${id}`)),

  // ----- Quản trị: tiện ích -----
  createAmenity: (payload) => unwrap(axiosClient.post('/admin/amenities', payload)),
  updateAmenity: (id, payload) => unwrap(axiosClient.put(`/admin/amenities/${id}`, payload)),
  deleteAmenity: (id) => unwrap(axiosClient.delete(`/admin/amenities/${id}`)),
};

export default catalogApi;
