import { Link as RouterLink } from 'react-router-dom';
import { Box, Container, Grid, Typography, Link, Divider, Stack } from '@mui/material';
import HomeIcon from '@mui/icons-material/Home';

/** Footer công khai: giới thiệu ngắn, liên kết nhanh, danh mục phổ biến. */
const PublicFooter = () => {
  const year = 2026;
  return (
    <Box component="footer" sx={{ bgcolor: 'background.paper', borderTop: 1, borderColor: 'divider', mt: 6, py: 5 }}>
      <Container maxWidth="lg">
        <Grid container spacing={4}>
          <Grid item xs={12} md={4}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ color: 'primary.main', mb: 1 }}>
              <HomeIcon />
              <Typography variant="h6" fontWeight={800}>Webtro</Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary">
              Nền tảng tìm phòng trọ, căn hộ, nhà nguyên căn và bạn ở ghép nhanh chóng, minh bạch.
              Kết nối trực tiếp người thuê với chủ trọ.
            </Typography>
          </Grid>
          <Grid item xs={6} md={2}>
            <Typography variant="subtitle2" fontWeight={700} gutterBottom>Khám phá</Typography>
            <Stack spacing={0.5}>
              <Link component={RouterLink} to="/tim-kiem" color="text.secondary" underline="hover">Tìm phòng</Link>
              <Link component={RouterLink} to="/tim-kiem?categoryCode=BOARDING_HOUSE" color="text.secondary" underline="hover">Phòng trọ</Link>
              <Link component={RouterLink} to="/tim-kiem?categoryCode=ROOMMATE" color="text.secondary" underline="hover">Ở ghép</Link>
              <Link component={RouterLink} to="/tim-kiem?categoryCode=APARTMENT" color="text.secondary" underline="hover">Căn hộ</Link>
            </Stack>
          </Grid>
          <Grid item xs={6} md={2}>
            <Typography variant="subtitle2" fontWeight={700} gutterBottom>Tài khoản</Typography>
            <Stack spacing={0.5}>
              <Link component={RouterLink} to="/dang-nhap" color="text.secondary" underline="hover">Đăng nhập</Link>
              <Link component={RouterLink} to="/dang-ky" color="text.secondary" underline="hover">Đăng ký</Link>
              <Link component={RouterLink} to="/quan-ly/tin-dang/tao" color="text.secondary" underline="hover">Đăng tin</Link>
            </Stack>
          </Grid>
          <Grid item xs={12} md={4}>
            <Typography variant="subtitle2" fontWeight={700} gutterBottom>Thông tin</Typography>
            <Stack spacing={0.5}>
              <Link component={RouterLink} to="/gioi-thieu" color="text.secondary" underline="hover">Giới thiệu</Link>
              <Link component={RouterLink} to="/dieu-khoan" color="text.secondary" underline="hover">Điều khoản sử dụng</Link>
            </Stack>
          </Grid>
        </Grid>
        <Divider sx={{ my: 3 }} />
        <Typography variant="caption" color="text.secondary">
          © {year} Webtro — Đồ án tốt nghiệp. Website quảng cáo và tìm kiếm phòng trọ.
        </Typography>
      </Container>
    </Box>
  );
};

export default PublicFooter;
