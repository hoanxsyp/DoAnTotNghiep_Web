import { Box, Typography, Stack } from '@mui/material';

/**
 * Tiêu đề trang trong khu vực dashboard (Tenant/Landlord) — docs/04 §7.2, §7.3.
 * Gồm tiêu đề, mô tả phụ và vùng hành động (nút) căn phải, xuống dòng ở mobile.
 *
 * Props: title, subtitle, action (node)
 */
export default function PageHeader({ title, subtitle, action }) {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      alignItems={{ xs: 'flex-start', sm: 'center' }}
      justifyContent="space-between"
      sx={{ mb: 3 }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      {action && <Box sx={{ flexShrink: 0 }}>{action}</Box>}
    </Stack>
  );
}
