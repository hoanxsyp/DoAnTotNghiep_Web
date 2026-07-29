import { Box, Typography, Stack } from '@mui/material';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';

/**
 * Trạng thái rỗng dùng lại (docs/04 mục 6, component #18).
 * Luôn nên có "lối ra" (action) — không chỉ hiện chữ "Không có dữ liệu".
 *
 * Props:
 * - icon: node (mặc định InboxOutlined)
 * - title: tiêu đề ngắn
 * - description: mô tả
 * - action / secondaryAction: node (thường là Button)
 * - size: 'sm' | 'md' | 'lg'
 * - illustration: node minh họa thay cho icon
 */
const SIZE_MAP = {
  sm: { icon: 40, py: 4, gap: 1 },
  md: { icon: 64, py: 6, gap: 1.5 },
  lg: { icon: 96, py: 8, gap: 2 },
};

export default function EmptyState({
  icon,
  title = 'Chưa có dữ liệu',
  description,
  action,
  secondaryAction,
  size = 'md',
  illustration,
}) {
  const cfg = SIZE_MAP[size] || SIZE_MAP.md;

  return (
    <Box
      sx={{
        width: '100%',
        py: cfg.py,
        px: 2,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        textAlign: 'center',
      }}
    >
      {illustration || (
        <Box
          sx={{
            color: 'text.disabled',
            mb: cfg.gap,
            '& svg': { fontSize: cfg.icon },
            display: 'flex',
          }}
        >
          {icon || <InboxOutlinedIcon fontSize="inherit" />}
        </Box>
      )}

      <Typography variant={size === 'lg' ? 'h5' : 'h6'} sx={{ fontWeight: 600 }}>
        {title}
      </Typography>

      {description && (
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ mt: 0.5, maxWidth: 420 }}
        >
          {description}
        </Typography>
      )}

      {(action || secondaryAction) && (
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
          sx={{ mt: cfg.gap + 1 }}
          alignItems="center"
        >
          {action}
          {secondaryAction}
        </Stack>
      )}
    </Box>
  );
}
