import { useEffect } from 'react';
import {
  Container, Grid, Paper, Box, Typography, Link, Breadcrumbs, List, ListItemButton, ListItemText,
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import RichTextViewer from '@/components/common/RichTextViewer';

/**
 * Trang Điều khoản sử dụng (docs/04 mục 5.1.12). Nội dung tĩnh mẫu — có thể thay bằng config
 * `page.terms` qua API khi backend cung cấp. Render qua RichTextViewer (không dangerouslySetInnerHTML).
 */
const SECTIONS = [
  {
    id: 'chap-nhan',
    heading: '1. Chấp nhận điều khoản',
    body:
      'Khi truy cập và sử dụng Webtro, bạn đồng ý tuân thủ các điều khoản dưới đây. Nếu không ' +
      'đồng ý, vui lòng ngừng sử dụng dịch vụ.',
  },
  {
    id: 'tai-khoan',
    heading: '2. Tài khoản người dùng',
    body:
      'Bạn chịu trách nhiệm bảo mật thông tin đăng nhập của mình. Thông tin cung cấp khi đăng ký ' +
      'phải chính xác. Một số điện thoại chỉ nên thuộc về một tài khoản đang hoạt động.',
  },
  {
    id: 'noi-dung-tin',
    heading: '3. Nội dung tin đăng',
    body:
      'Chủ trọ chịu trách nhiệm về tính chính xác của tin đăng. Nghiêm cấm đăng tin sai sự thật, ' +
      'lừa đảo, trùng lặp hoặc chứa nội dung vi phạm pháp luật. Tin đăng có thể bị kiểm duyệt, tạm ' +
      'ẩn hoặc gỡ bỏ nếu vi phạm.',
  },
  {
    id: 'thanh-toan',
    heading: '4. Thanh toán và gói dịch vụ',
    body:
      'Các gói đẩy tin, làm nổi bật tin được cung cấp theo bảng giá công khai. Giao dịch được ghi ' +
      'nhận và có thể tra soát. Việc hoàn tiền tuân theo chính sách hiện hành.',
  },
  {
    id: 'trach-nhiem',
    heading: '5. Giới hạn trách nhiệm',
    body:
      'Webtro là nền tảng trung gian kết nối. Chúng tôi khuyến nghị người thuê kiểm tra kỹ thông ' +
      'tin và không chuyển tiền trước khi xem phòng trực tiếp.',
  },
];

const TermsPage = () => {
  useEffect(() => {
    document.title = 'Điều khoản sử dụng — Webtro';
  }, []);

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 3, md: 5 } }}>
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/" underline="hover" color="inherit">
          Trang chủ
        </Link>
        <Typography color="text.primary">Điều khoản sử dụng</Typography>
      </Breadcrumbs>

      <Grid container spacing={3}>
        <Grid item xs={12} md={3} sx={{ display: { xs: 'none', md: 'block' } }}>
          <Box sx={{ position: 'sticky', top: 88 }}>
            <Typography variant="overline" color="text.secondary">
              Mục lục
            </Typography>
            <List dense>
              {SECTIONS.map((s) => (
                <ListItemButton key={s.id} component="a" href={`#${s.id}`}>
                  <ListItemText primary={s.heading} />
                </ListItemButton>
              ))}
            </List>
          </Box>
        </Grid>

        <Grid item xs={12} md={9}>
          <Paper variant="outlined" sx={{ p: { xs: 2.5, md: 4 } }}>
            <Typography variant="h4" component="h1" sx={{ fontWeight: 800 }}>
              Điều khoản sử dụng
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 3 }}>
              Cập nhật: 01/07/2026
            </Typography>

            {SECTIONS.map((s) => (
              <Box key={s.id} id={s.id} sx={{ mb: 3, scrollMarginTop: 88 }}>
                <Typography variant="h6" component="h2" sx={{ fontWeight: 700, mb: 1 }}>
                  {s.heading}
                </Typography>
                <RichTextViewer content={s.body} variant="body1" />
              </Box>
            ))}
          </Paper>
        </Grid>
      </Grid>
    </Container>
  );
};

export default TermsPage;
