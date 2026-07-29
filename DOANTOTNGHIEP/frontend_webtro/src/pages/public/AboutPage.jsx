import { useEffect } from 'react';
import {
  Container, Grid, Paper, Box, Typography, Link, Breadcrumbs, List, ListItemButton, ListItemText,
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import RichTextViewer from '@/components/common/RichTextViewer';

/**
 * Trang Giới thiệu (docs/04 mục 5.1.12). Nội dung tĩnh mẫu — có thể thay bằng config
 * `page.about` qua API khi backend cung cấp. Render qua RichTextViewer (không dangerouslySetInnerHTML).
 */
const SECTIONS = [
  {
    id: 'gioi-thieu',
    heading: '1. Về Webtro',
    body:
      'Webtro là nền tảng kết nối người thuê và chủ trọ trên toàn quốc. Chúng tôi giúp bạn tìm ' +
      'phòng trọ, nhà nguyên căn, căn hộ, chung cư mini, ở ghép và mặt bằng nhỏ một cách nhanh ' +
      'chóng, minh bạch và an toàn.',
  },
  {
    id: 'su-menh',
    heading: '2. Sứ mệnh',
    body:
      'Chúng tôi mong muốn mọi tin đăng đều được kiểm duyệt, thông tin rõ ràng và giá cả minh ' +
      'bạch. Hệ thống đánh giá uy tín chủ trọ và phân tích bình luận bằng AI giúp người thuê ' +
      'đưa ra quyết định tốt hơn.',
  },
  {
    id: 'tinh-nang',
    heading: '3. Tính năng nổi bật',
    body:
      '- Tìm kiếm và lọc nâng cao theo khu vực, giá, diện tích, tiện ích.\n' +
      '- Gợi ý tin phù hợp bằng AI.\n' +
      '- Đánh giá và bình luận công khai cho từng tin.\n' +
      '- Điểm uy tín chủ trọ và cảnh báo tin rủi ro.\n' +
      '- Trợ lý ảo hỗ trợ tìm phòng 24/7.',
  },
  {
    id: 'lien-he',
    heading: '4. Liên hệ',
    body:
      'Mọi thắc mắc xin gửi về hộp thư hỗ trợ của chúng tôi. Chúng tôi luôn lắng nghe để cải ' +
      'thiện trải nghiệm cho cả người thuê lẫn chủ trọ.',
  },
];

const AboutPage = () => {
  useEffect(() => {
    document.title = 'Giới thiệu — Webtro';
  }, []);

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 3, md: 5 } }}>
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/" underline="hover" color="inherit">
          Trang chủ
        </Link>
        <Typography color="text.primary">Giới thiệu</Typography>
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
              Giới thiệu về Webtro
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

export default AboutPage;
