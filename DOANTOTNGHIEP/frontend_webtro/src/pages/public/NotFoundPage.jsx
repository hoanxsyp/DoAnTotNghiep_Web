import { useEffect, useState } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import {
  Container, Box, Typography, Button, Stack, Paper, InputBase, IconButton, Divider,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import SentimentDissatisfiedOutlinedIcon from '@mui/icons-material/SentimentDissatisfiedOutlined';
import listingApi from '@/api/listingApi';
import ListingGrid from '@/components/listing/ListingGrid';

const toItems = (d) => (Array.isArray(d) ? d : d?.items ?? []);

/**
 * Trang 404 (docs/04 mục 5.1.10). Hiện ngay, tin gợi ý tải sau; lỗi gợi ý -> ẩn section.
 */
const NotFoundPage = () => {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [suggested, setSuggested] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    document.title = 'Không tìm thấy trang — Webtro';
    let alive = true;
    listingApi
      .getSuggested({ source: 'HOMEPAGE', size: 4 })
      .then((data) => {
        if (alive) setSuggested(toItems(data));
      })
      .catch(() => {
        /* Trang 404 không báo thêm lỗi API — chỉ ẩn section gợi ý. */
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    const q = keyword.trim();
    navigate(`/tim-kiem${q ? `?keyword=${encodeURIComponent(q)}` : ''}`);
  };

  return (
    <Container maxWidth="md">
      <Box sx={{ py: { xs: 6, md: 10 }, textAlign: 'center' }}>
        <SentimentDissatisfiedOutlinedIcon sx={{ fontSize: 96, color: 'text.disabled' }} />
        <Typography variant="h3" sx={{ fontWeight: 800, mt: 1, color: 'primary.main' }}>
          404
        </Typography>
        <Typography variant="h5" component="h1" sx={{ fontWeight: 700, mt: 1 }}>
          Không tìm thấy trang này
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Trang bạn tìm có thể đã bị xóa, đổi địa chỉ hoặc chưa từng tồn tại.
        </Typography>

        <Paper
          component="form"
          onSubmit={handleSearch}
          variant="outlined"
          sx={{
            mt: 3,
            display: 'flex',
            alignItems: 'center',
            maxWidth: 520,
            mx: 'auto',
            px: 1.5,
            py: 0.5,
          }}
        >
          <SearchIcon color="action" />
          <InputBase
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tìm phòng trọ..."
            inputProps={{ maxLength: 100, 'aria-label': 'Tìm phòng trọ' }}
            sx={{ ml: 1, flex: 1 }}
          />
          <IconButton type="submit" color="primary" aria-label="Tìm">
            <SearchIcon />
          </IconButton>
        </Paper>

        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
          justifyContent="center"
          sx={{ mt: 3 }}
        >
          <Button variant="contained" component={RouterLink} to="/">
            Về trang chủ
          </Button>
          <Button variant="outlined" component={RouterLink} to="/tim-kiem">
            Xem tất cả tin đăng
          </Button>
        </Stack>
      </Box>

      {(loading || suggested.length > 0) && (
        <Box sx={{ pb: 8 }}>
          <Divider sx={{ mb: 3 }} />
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
            Có thể bạn quan tâm
          </Typography>
          <ListingGrid
            items={suggested}
            loading={loading}
            skeletonCount={4}
            columns={{ xs: 1, sm: 2, md: 4 }}
          />
        </Box>
      )}
    </Container>
  );
};

export default NotFoundPage;
