import { toast } from 'react-toastify';

/**
 * Bọc react-toastify với quy ước thông điệp tiếng Việt (canonical mục 8 doc UI). `apiError` nhận
 * đối tượng lỗi đã chuẩn hóa từ apiHelper và hiển thị thông điệp thân thiện.
 */
export const notify = {
  success: (msg) => toast.success(msg),
  error: (msg) => toast.error(msg),
  warning: (msg) => toast.warning(msg),
  info: (msg) => toast.info(msg),

  /** Hiển thị lỗi từ API (đã normalize): ưu tiên message, có kèm gợi ý theo errorCode nếu cần. */
  apiError: (error, fallback = 'Đã có lỗi xảy ra, vui lòng thử lại') => {
    const message = error?.message || fallback;
    toast.error(message);
  },
};

export default notify;
