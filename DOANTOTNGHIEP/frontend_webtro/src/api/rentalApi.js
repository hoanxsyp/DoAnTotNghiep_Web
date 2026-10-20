import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

const rentalApi = {
  create: (listingId, desiredViewingDate) =>
    unwrap(axiosClient.post('/rental-requests', { listingId, desiredViewingDate })),
  getMine: (params) => unwrap(axiosClient.get('/rental-requests/my', { params })),
  updateViewingDate: (id, desiredViewingDate) =>
    unwrap(axiosClient.put(`/rental-requests/${id}/viewing-date`, { desiredViewingDate })),
  cancel: (id) => unwrap(axiosClient.put(`/rental-requests/${id}/cancel`)),
  getForLandlord: (params) => unwrap(axiosClient.get('/landlord/rental-requests', { params })),
  approve: (id, payload = {}) => unwrap(axiosClient.put(`/landlord/rental-requests/${id}/approve`, payload)),
  reject: (id, payload = {}) => unwrap(axiosClient.put(`/landlord/rental-requests/${id}/reject`, payload)),
  confirmRented: (id) => unwrap(axiosClient.put(`/landlord/rental-requests/${id}/confirm-rented`)),
  confirmCheckedOut: (id) => unwrap(axiosClient.put(`/landlord/rental-requests/${id}/confirm-checked-out`)),
};

export default rentalApi;
