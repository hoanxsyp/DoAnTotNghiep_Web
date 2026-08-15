import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/**
 * API quản trị (khớp docs/03 mục 4.12-4.20). Chỉ gọi HTTP + bóc envelope (luật F2).
 */
const adminApi = {
  // ----- Dashboard & thống kê (STATISTIC_VIEW) -----
  getDashboard: (params) => unwrap(axiosClient.get('/admin/dashboard', { params })),
  getStatistics: (params) => unwrap(axiosClient.get('/admin/statistics', { params })),
  getRevenue: (params) => unwrap(axiosClient.get('/admin/statistics/revenue', { params })),

  // ----- Người dùng (USER_MANAGE / USER_ROLE_ASSIGN) -----
  getUsers: (params) => unwrap(axiosClient.get('/admin/users', { params })),
  getUser: (id) => unwrap(axiosClient.get(`/admin/users/${id}`)),
  lockUser: (id, payload) => unwrap(axiosClient.put(`/admin/users/${id}/lock`, payload)),
  unlockUser: (id) => unwrap(axiosClient.put(`/admin/users/${id}/unlock`)),
  updateUserRole: (id, payload) => unwrap(axiosClient.put(`/admin/users/${id}/role`, payload)),

  // ----- Chủ trọ (LANDLORD_VERIFY) -----
  getLandlords: (params) => unwrap(axiosClient.get('/admin/landlords', { params })),
  verifyLandlord: (id, payload) => unwrap(axiosClient.put(`/admin/landlords/${id}/verify`, payload)),
  unverifyLandlord: (id, payload) => unwrap(axiosClient.put(`/admin/landlords/${id}/unverify`, payload)),
  rejectLandlordVerification: (id, payload) =>
    unwrap(axiosClient.put(`/admin/landlords/${id}/reject-verification`, payload)),
  restrictLandlordPosting: (id, payload) =>
    unwrap(axiosClient.put(`/admin/landlords/${id}/restrict-posting`, payload)),

  // ----- Tin đăng & kiểm duyệt (LISTING_VIEW_ANY / LISTING_MODERATE / LISTING_LOCK) -----
  getListings: (params) => unwrap(axiosClient.get('/admin/listings', { params })),
  getListing: (id) => unwrap(axiosClient.get(`/admin/listings/${id}`)),
  approveListing: (id, payload) => unwrap(axiosClient.put(`/admin/listings/${id}/approve`, payload)),
  rejectListing: (id, payload) => unwrap(axiosClient.put(`/admin/listings/${id}/reject`, payload)),
  hideListing: (id, payload) => unwrap(axiosClient.put(`/admin/listings/${id}/hide`, payload)),
  unhideListing: (id, payload) => unwrap(axiosClient.put(`/admin/listings/${id}/unhide`, payload)),
  flagNeedReview: (id, payload) => unwrap(axiosClient.put(`/admin/listings/${id}/flag-need-review`, payload)),
  clearNeedReview: (id, payload) => unwrap(axiosClient.put(`/admin/listings/${id}/clear-need-review`, payload)),
  lockListing: (id, payload) => unwrap(axiosClient.put(`/admin/listings/${id}/lock`, payload)),
  unlockListing: (id) => unwrap(axiosClient.put(`/admin/listings/${id}/unlock`)),
  requestListingEdit: (id, payload) => unwrap(axiosClient.put(`/admin/listings/${id}/request-edit`, payload)),
  bulkListings: (payload) => unwrap(axiosClient.put('/admin/listings/bulk', payload)),
  getModerationQueue: (params) => unwrap(axiosClient.get('/admin/moderation-queue', { params })),
  getModerationActions: (params) => unwrap(axiosClient.get('/admin/moderation-actions', { params })),

  // ----- Báo cáo (REPORT_RESOLVE) -----
  getReports: (params) => unwrap(axiosClient.get('/admin/reports', { params })),
  getReport: (id) => unwrap(axiosClient.get(`/admin/reports/${id}`)),
  getReportsByTarget: (targetType, targetId) =>
    unwrap(axiosClient.get(`/admin/reports/target/${targetType}/${targetId}`)),
  updateReportStatus: (id, payload) => unwrap(axiosClient.put(`/admin/reports/${id}/status`, payload)),
  resolveReport: (id, payload) => unwrap(axiosClient.put(`/admin/reports/${id}/resolve`, payload)),
  resolveReportGroup: (payload) => unwrap(axiosClient.put('/admin/reports/resolve-group', payload)),

  // ----- Cảnh báo vi phạm (WARNING_SEND) -----
  getWarnings: (params) => unwrap(axiosClient.get('/admin/warnings', { params })),
  sendWarning: (payload) => unwrap(axiosClient.post('/admin/warnings', payload)),

  // ----- Bình luận & đánh giá (COMMENT_MODERATE / REVIEW_MODERATE) -----
  getComments: (params) => unwrap(axiosClient.get('/admin/comments', { params })),
  hideComment: (id, payload) => unwrap(axiosClient.put(`/admin/comments/${id}/hide`, payload)),
  unhideComment: (id) => unwrap(axiosClient.put(`/admin/comments/${id}/unhide`)),
  markCommentSpam: (id, payload) => unwrap(axiosClient.put(`/admin/comments/${id}/spam`, payload)),
  bulkComments: (payload) => unwrap(axiosClient.put('/admin/comments/bulk', payload)),
  getReviews: (params) => unwrap(axiosClient.get('/admin/reviews', { params })),
  hideReview: (id, payload) => unwrap(axiosClient.put(`/admin/reviews/${id}/hide`, payload)),
  unhideReview: (id) => unwrap(axiosClient.put(`/admin/reviews/${id}/unhide`)),

  // ----- Thanh toán & gói dịch vụ (PAYMENT_MANAGE / PACKAGE_MANAGE) -----
  getPayments: (params) => unwrap(axiosClient.get('/admin/payments', { params })),
  getPayment: (id) => unwrap(axiosClient.get(`/admin/payments/${id}`)),
  refundPayment: (id, payload) => unwrap(axiosClient.put(`/admin/payments/${id}/refund`, payload)),
  reconcilePayment: (id) => unwrap(axiosClient.post(`/admin/payments/${id}/reconcile`)),
  getPackages: (params) => unwrap(axiosClient.get('/admin/promotion-packages', { params })),
  createPackage: (payload) => unwrap(axiosClient.post('/admin/promotion-packages', payload)),
  updatePackage: (id, payload) =>
    unwrap(axiosClient.put(`/admin/promotion-packages/${id}`, payload)),
  getCoupons: (params) => unwrap(axiosClient.get('/admin/coupons', { params })),
  createCoupon: (payload) => unwrap(axiosClient.post('/admin/coupons', payload)),
  updateCoupon: (id, payload) => unwrap(axiosClient.put(`/admin/coupons/${id}`, payload)),

  // ----- Cấu hình hệ thống (SYSTEM_CONFIG_MANAGE) -----
  getSystemConfigs: (params) => unwrap(axiosClient.get('/admin/system-configs', { params })),
  updateSystemConfigs: (payload) => unwrap(axiosClient.put('/admin/system-configs', payload)),
  updateConfig: (key, payload) =>
    unwrap(axiosClient.put('/admin/system-configs', { configs: [{ key, ...payload }] })),

  // ----- Cấu hình & log AI (AI_CONFIG_MANAGE / AI_LOG_VIEW) -----
  getAiConfig: () => unwrap(axiosClient.get('/admin/ai/config')),
  updateAiConfig: (payload) => unwrap(axiosClient.put('/admin/ai/config', payload)),
  getAiLogs: (params) => unwrap(axiosClient.get('/admin/ai/logs', { params })),
  getAiAlerts: (params) => unwrap(axiosClient.get('/admin/ai/alerts', { params })),
  getPriceDeviations: (params) => unwrap(axiosClient.get('/admin/ai/price-deviations', { params })),
  reanalyzeSentiment: (payload) => unwrap(axiosClient.post('/admin/ai/sentiment/reanalyze', payload)),

  // ----- Nhật ký kiểm toán (AUDIT_LOG_VIEW) -----
  getAuditLogs: (params) => unwrap(axiosClient.get('/admin/audit-logs', { params })),

  // ----- Danh mục (CATALOG_MANAGE) -----
  getCategories: (params) => unwrap(axiosClient.get('/admin/categories', { params })),
  createCategory: (payload) => unwrap(axiosClient.post('/admin/categories', payload)),
  updateCategory: (id, payload) => unwrap(axiosClient.put(`/admin/categories/${id}`, payload)),
  deleteCategory: (id) => unwrap(axiosClient.delete(`/admin/categories/${id}`)),
  toggleCategory: (id) => unwrap(axiosClient.put(`/admin/categories/${id}/toggle`)),

  // ----- Tiện ích (CATALOG_MANAGE) -----
  getAmenities: (params) => unwrap(axiosClient.get('/admin/amenities', { params })),
  createAmenity: (payload) => unwrap(axiosClient.post('/admin/amenities', payload)),
  updateAmenity: (id, payload) => unwrap(axiosClient.put(`/admin/amenities/${id}`, payload)),
  deleteAmenity: (id) => unwrap(axiosClient.delete(`/admin/amenities/${id}`)),
  toggleAmenity: (id) => unwrap(axiosClient.put(`/admin/amenities/${id}/toggle`)),

  // ----- Từ khóa cấm (SYSTEM_CONFIG_MANAGE) -----
  getBannedKeywords: (params) => unwrap(axiosClient.get('/admin/banned-keywords', { params })),
  createBannedKeyword: (payload) => unwrap(axiosClient.post('/admin/banned-keywords', payload)),
  updateBannedKeyword: (id, payload) => unwrap(axiosClient.put(`/admin/banned-keywords/${id}`, payload)),
  deleteBannedKeyword: (id) => unwrap(axiosClient.delete(`/admin/banned-keywords/${id}`)),
  toggleBannedKeyword: (id) => unwrap(axiosClient.put(`/admin/banned-keywords/${id}/toggle`)),

  // ----- Khu vực (CATALOG_MANAGE) -----
  getProvinces: (params) => unwrap(axiosClient.get('/admin/provinces', { params })),
  getDistricts: (provinceId) => unwrap(axiosClient.get(`/provinces/${provinceId}/districts`)),
  getWards: (districtId) => unwrap(axiosClient.get(`/districts/${districtId}/wards`)),
  createProvince: (payload) => unwrap(axiosClient.post('/admin/provinces', payload)),
  updateProvince: (id, payload) => unwrap(axiosClient.put(`/admin/provinces/${id}`, payload)),
  toggleProvince: (id) => unwrap(axiosClient.put(`/admin/provinces/${id}/toggle`)),
  createDistrict: (payload) => unwrap(axiosClient.post('/admin/districts', payload)),
  updateDistrict: (id, payload) => unwrap(axiosClient.put(`/admin/districts/${id}`, payload)),
  createWard: (payload) => unwrap(axiosClient.post('/admin/wards', payload)),
  updateWard: (id, payload) => unwrap(axiosClient.put(`/admin/wards/${id}`, payload)),
  // Backend nhận JSON { provinces: [{ code, name, districts: [{ ..., wards: [...] }] }] } (không CSV).
  importAreas: (payload) => unwrap(axiosClient.post('/admin/areas/import', payload)),
};

export default adminApi;
