import { Box, CircularProgress } from '@mui/material';

/** Spinner toàn trang, dùng làm fallback cho lazy-loaded route (canonical mục 11 doc UI). */
const PageLoader = () => (
  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
    <CircularProgress />
  </Box>
);

export default PageLoader;
