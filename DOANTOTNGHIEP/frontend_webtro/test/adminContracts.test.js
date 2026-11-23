import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buildAdminListingFilters,
  buildAdminPaymentFilters,
  buildResetPasswordPayload,
  listingDetailPath,
} from '../src/utils/adminContracts.js';

test('tạo payload đặt lại mật khẩu đầy đủ', () => {
  assert.deepEqual(buildResetPasswordPayload('reset-token', {
    newPassword: 'NewPassword1',
    confirmPassword: 'NewPassword1',
  }), {
    token: 'reset-token',
    newPassword: 'NewPassword1',
    confirmPassword: 'NewPassword1',
  });
});

test('chuẩn hóa bộ lọc tin đăng quản trị', () => {
  assert.deepEqual(buildAdminListingFilters({
    keyword: '  phòng đẹp  ',
    status: 'ACTIVE',
    categoryCode: 'BOARDING_HOUSE',
  }), {
    keyword: 'phòng đẹp',
    status: 'ACTIVE',
    categoryCode: 'BOARDING_HOUSE',
  });
  assert.deepEqual(buildAdminListingFilters({ keyword: ' ', status: '', categoryCode: '' }, {
    status: 'PENDING',
  }), {
    keyword: undefined,
    status: 'PENDING',
    categoryCode: undefined,
  });
});

test('chuẩn hóa bộ lọc thanh toán quản trị', () => {
  assert.deepEqual(buildAdminPaymentFilters({
    keyword: '  người mua  ',
    status: 'SUCCESS',
    paymentMethod: 'SANDBOX',
  }), {
    keyword: 'người mua',
    status: 'SUCCESS',
    paymentMethod: 'SANDBOX',
  });
  assert.deepEqual(buildAdminPaymentFilters({ keyword: '', status: '', paymentMethod: '' }, {
    paymentMethod: 'VNPAY',
  }), {
    keyword: undefined,
    status: undefined,
    paymentMethod: 'VNPAY',
  });
});

test('tạo đường dẫn chi tiết tin có và không có slug', () => {
  assert.equal(listingDetailPath({ id: 15, slug: 'phong-dep' }), '/tin/phong-dep-15');
  assert.equal(listingDetailPath({ id: 15 }), '/tin/15');
});
