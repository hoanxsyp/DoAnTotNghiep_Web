import { Box, Typography, Stack } from '@mui/material';

/**
 * Tiêu đề trang quản trị: `<h1>` + mô tả ngắn + vùng hành động bên phải (nút tạo, xuất...).
 * Nhận nội dung qua props (luật F3).
 */
const AdminPageHeader = ({ title, subtitle, actions }) => (
  <Box
    sx={{
      display: 'flex',
      flexDirection: { xs: 'column', sm: 'row' },
      alignItems: { xs: 'flex-start', sm: 'center' },
      justifyContent: 'space-between',
      gap: 1.5,
      mb: 3,
    }}
  >
    <Box>
      <Typography variant="h5" component="h1" fontWeight={700}>
        {title}
      </Typography>
      {subtitle && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          {subtitle}
        </Typography>
      )}
    </Box>
    {actions && (
      <Stack direction="row" spacing={1} flexWrap="wrap">
        {actions}
      </Stack>
    )}
  </Box>
);

export default AdminPageHeader;
