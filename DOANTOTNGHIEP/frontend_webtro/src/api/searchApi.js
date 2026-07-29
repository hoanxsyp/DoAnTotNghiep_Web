import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API tìm kiếm tin đăng (khớp docs/03 mục 4.4.2). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const searchApi = {
  searchListings: (params) => unwrap(axiosClient.get('/search/listings', { params })),
  getRelated: (id) => unwrap(axiosClient.get(`/listings/${id}/related`)),
};

export default searchApi;
