import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API Phòng của chủ trọ (docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 4). Mỗi tin đăng thuộc một
 * phòng; đánh giá gắn vào phòng nên đóng tin rồi đăng lại vẫn giữ đánh giá.
 */
const rentalUnitApi = {
  getMine: (params) => unwrap(axiosClient.get('/landlord/rental-units', { params })),
  getDetail: (id) => unwrap(axiosClient.get(`/landlord/rental-units/${id}`)),
  create: (payload) => unwrap(axiosClient.post('/landlord/rental-units', payload)),
  update: (id, payload) => unwrap(axiosClient.put(`/landlord/rental-units/${id}`, payload)),
  archive: (id) => unwrap(axiosClient.post(`/landlord/rental-units/${id}/archive`)),
  unarchive: (id) => unwrap(axiosClient.post(`/landlord/rental-units/${id}/unarchive`)),
  /** Tạo tin nháp mới từ tin gần nhất của phòng (nội dung, tiện ích, ảnh). */
  relist: (id) => unwrap(axiosClient.post(`/landlord/rental-units/${id}/relist`)),
  /** Dò trùng địa chỉ trước khi tạo phòng: { duplicate, duplicateUnit, sameAddressUnits, otherOwnerMatch }. */
  checkDuplicate: (params) => unwrap(axiosClient.get('/landlord/rental-units/duplicate-check', { params })),
};

export default rentalUnitApi;
