import { Card, CardActionArea, Box, Typography, Avatar, Skeleton } from '@mui/material';

/**
 * Thẻ chỉ số cho Dashboard (docs/04 §10.1): icon + nhãn + số lớn + phụ đề tùy chọn. Nhận dữ liệu
 * qua props (luật F3). Có trạng thái `loading` -> skeleton đúng hình dạng.
 */
const StatCard = ({ label, value, sub, icon, color = 'primary', loading = false, onClick }) => {
  const content = (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, p: 2.5, width: '100%' }}>
      {icon && (
        <Avatar variant="rounded" sx={{ bgcolor: `${color}.light`, color: `${color}.dark`, width: 48, height: 48 }}>
          {icon}
        </Avatar>
      )}
      <Box sx={{ minWidth: 0, flexGrow: 1 }}>
        <Typography variant="body2" color="text.secondary" noWrap>
          {label}
        </Typography>
        {loading ? (
          <Skeleton width={72} height={34} />
        ) : (
          <Typography variant="h5" fontWeight={700} noWrap>
            {value ?? '—'}
          </Typography>
        )}
        {sub && !loading && (
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
            {sub}
          </Typography>
        )}
      </Box>
    </Box>
  );

  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      {onClick ? <CardActionArea onClick={onClick} sx={{ height: '100%' }}>{content}</CardActionArea> : content}
    </Card>
  );
};

export default StatCard;
