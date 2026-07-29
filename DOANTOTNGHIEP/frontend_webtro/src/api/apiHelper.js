import axiosClient from './axiosClient';

/**
 * Bóc lớp envelope thống nhất của backend (canonical mục 7.1): trả về `data` bên trong khi
 * thành công, ném lỗi đã chuẩn hóa khi thất bại.
 *
 * Nhờ lớp này, tầng api/ (F2) chỉ việc `return unwrap(axiosClient.get(...))` và trả thẳng dữ liệu
 * nghiệp vụ cho pages/ — không rò rỉ cấu trúc envelope ra ngoài.
 */
export const unwrap = async (promise) => {
  try {
    const response = await promise;
    return response.data?.data;
  } catch (error) {
    throw normalizeError(error);
  }
};

/**
 * Chuẩn hóa lỗi từ axios thành đối tượng ổn định để UI xử lý:
 * { errorCode, message, fieldErrors, status }
 */
export const normalizeError = (error) => {
  const response = error.response;
  const body = response?.data;

  return {
    status: response?.status ?? 0,
    errorCode: body?.errorCode ?? 'NETWORK_ERROR',
    message: body?.message ?? 'Không kết nối được máy chủ, vui lòng thử lại',
    fieldErrors: Array.isArray(body?.errors) ? body.errors : [],
    retryAfter: response?.headers?.['retry-after'],
  };
};

export { axiosClient };
