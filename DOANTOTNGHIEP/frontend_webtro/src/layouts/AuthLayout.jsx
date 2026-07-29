import { Outlet, Link as RouterLink } from 'react-router-dom';
import { Box, Container, Paper, Typography, Link } from '@mui/material';
import HomeIcon from '@mui/icons-material/Home';

/**
 * Layout cho các trang xác thực (đăng nhập, đăng ký, quên mật khẩu...). Nền gradient, khung form
 * ở giữa, gọn cho cả mobile.
 */
const AuthLayout = () => {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #0F766E 0%, #0B5A54 100%)',
        py: 4,
        px: 2,
      }}
    >
      <Link
        component={RouterLink}
        to="/"
        underline="none"
        sx={{ display: 'flex', alignItems: 'center', gap: 1, color: '#fff', mb: 3, fontWeight: 700, fontSize: 24 }}
      >
        <HomeIcon /> Webtro
      </Link>
      <Container maxWidth="xs" disableGutters>
        <Paper elevation={8} sx={{ p: { xs: 3, sm: 4 }, borderRadius: 3 }}>
          <Outlet />
        </Paper>
      </Container>
      <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.8)', mt: 3 }}>
        © Webtro — Website tìm phòng trọ
      </Typography>
    </Box>
  );
};

export default AuthLayout;
