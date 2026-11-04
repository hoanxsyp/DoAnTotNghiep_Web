const valueOrUndefined = (value) => value || undefined;

export const buildResetPasswordPayload = (token, values) => ({
  token,
  newPassword: values.newPassword,
  confirmPassword: values.confirmPassword,
});

export const buildAdminListingFilters = ({ keyword, status, categoryCode }, overrides = {}) => ({
  keyword: valueOrUndefined(keyword.trim()),
  status: valueOrUndefined(status),
  categoryCode: valueOrUndefined(categoryCode),
  ...overrides,
});

export const buildAdminPaymentFilters = ({ keyword, status, paymentMethod }, overrides = {}) => ({
  keyword: valueOrUndefined(keyword.trim()),
  status: valueOrUndefined(status),
  paymentMethod: valueOrUndefined(paymentMethod),
  ...overrides,
});

export const listingDetailPath = ({ id, slug }) => `/tin/${slug ? `${slug}-` : ''}${id}`;
