import { Chip } from '@mui/material';
import { LISTING_STATUS_META } from '@/constants';

/** Chip màu theo trạng thái tin đăng (canonical mục 5 enum). Dùng trong bảng quản lý và thẻ tin. */
const StatusChip = ({ status, size = 'small' }) => {
  const meta = LISTING_STATUS_META[status] || { label: status, color: 'default' };
  return <Chip label={meta.label} color={meta.color} size={size} variant="filled" />;
};

export default StatusChip;
