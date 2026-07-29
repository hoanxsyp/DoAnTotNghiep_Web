import { useEffect } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import { Container, Box, Typography, Button, Stack } from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';

/**
 * Trang 403 — Không đủ quyền (docs/04 mục 5.1.11).
 * Cố ý KHÔNG liệt kê quyền đang thiếu — tránh lộ cấu trúc phân quyền cho người không có quyền.
 */
const ForbiddenPage = () => {
  const navigate = useNavigate();

  useEffect(() => {
    document.title = 'Không đủ quyền truy cập — Webtro';
  }, []);

  return (
    <Container maxWidth="sm">
      <Box sx={{ py: { xs: 8, md: 12 }, textAlign: 'center' }}>
        <LockOutlinedIcon sx={{ fontSize: 96, color: 'text.disabled' }} />
        <Typography variant="h4" component="h1" sx={{ fontWeight: 700, mt: 2 }}>
          Bạn không có quyền truy cập
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1.5 }}>
          Trang này chỉ dành cho người dùng có quyền phù hợp. Nếu bạn cho rằng đây là nhầm lẫn,
          hãy liên hệ quản trị viên.
        </Typography>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
          justifyContent="center"
          sx={{ mt: 4 }}
        >
          <Button variant="outlined" onClick={() => navigate(-1)}>
            ← Quay lại
          </Button>
          <Button variant="contained" component={RouterLink} to="/">
            Về trang chủ
          </Button>
        </Stack>
      </Box>
    </Container>
  );
};

export default ForbiddenPage;
