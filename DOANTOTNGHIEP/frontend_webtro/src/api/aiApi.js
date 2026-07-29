import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API AI — phía người dùng (khớp docs/03 mục 4.11). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const aiApi = {
  getRecommendations: (payload) => unwrap(axiosClient.post('/ai/recommendations', payload)),
  predictPrice: (payload) => unwrap(axiosClient.post('/ai/price-prediction', payload)),
  analyzeSentiment: (payload) => unwrap(axiosClient.post('/ai/sentiment/analyze', payload)),
};

export default aiApi;
