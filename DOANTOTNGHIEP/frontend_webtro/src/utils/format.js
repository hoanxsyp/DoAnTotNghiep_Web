import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/vi';

dayjs.extend(relativeTime);
dayjs.locale('vi');

/**
 * Định dạng giá VND theo cách người Việt quen đọc: dưới 1 triệu hiện đầy đủ, từ 1 triệu trở lên
 * rút gọn "x,y triệu".
 */
export const formatPrice = (value) => {
  if (value == null) return 'Thỏa thuận';
  const num = Number(value);
  if (Number.isNaN(num)) return 'Thỏa thuận';
  if (num >= 1_000_000) {
    const millions = num / 1_000_000;
    const text = Number.isInteger(millions) ? String(millions) : millions.toFixed(1).replace('.', ',');
    return `${text} triệu`;
  }
  return `${num.toLocaleString('vi-VN')} đ`;
};

/** Giá đầy đủ có dấu phân cách và đơn vị đồng. */
export const formatCurrency = (value) => {
  if (value == null) return '—';
  return `${Number(value).toLocaleString('vi-VN')} đ`;
};

/** Định dạng ngày giờ. */
export const formatDateTime = (value, pattern = 'DD/MM/YYYY HH:mm') =>
  value ? dayjs(value).format(pattern) : '—';

export const formatDate = (value) => (value ? dayjs(value).format('DD/MM/YYYY') : '—');

/** Thời gian tương đối ("3 giờ trước"). */
export const fromNow = (value) => (value ? dayjs(value).fromNow() : '');

/** Rút gọn số lớn: 1200 -> "1,2k". */
export const compactNumber = (value) => {
  const num = Number(value) || 0;
  if (num >= 1000) return `${(num / 1000).toFixed(1).replace('.', ',')}k`;
  return String(num);
};

/** Diện tích. */
export const formatArea = (value) => (value != null ? `${value}m²` : '—');
