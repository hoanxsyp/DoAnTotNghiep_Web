import { useEffect, useState } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import {
  Container, Box, Grid, Typography, Button, Stack, Chip, Paper, TextField, MenuItem,
  Card, CardActionArea, Alert, Divider,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import HomeWorkOutlinedIcon from '@mui/icons-material/HomeWorkOutlined';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import listingApi from '@/api/listingApi';
import searchApi from '@/api/searchApi';
import catalogApi from '@/api/catalogApi';
import ListingGrid from '@/components/listing/ListingGrid';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import useAuth from '@/hooks/useAuth';
import { ROLES, CATEGORY_CODES } from '@/constants';
import { compactNumber } from '@/utils/format';

const toItems = (d) => (Array.isArray(d) ? d : d?.items ?? []);

const PRICE_PRESETS = [
  { label: 'Tất cả mức giá', value: '' },
  { label: 'Dưới 2 triệu', value: '0-2000000' },
  { label: '2 - 4 triệu', value: '2000000-4000000' },
  { label: '4 - 6 triệu', value: '4000000-6000000' },
  { label: 'Trên 6 triệu', value: '6000000-' },
];

const QUICK_CHIPS = [
  { label: 'Dưới 3 triệu', params: { priceTo: 3000000 } },
  { label: 'Ở ghép', params: { categoryCode: 'ROOMMATE' } },
  { label: 'Chung cư mini', params: { categoryCode: 'MINI_APARTMENT' } },
];

const buildQuery = (obj) => {
  const p = new URLSearchParams();
  Object.entries(obj).forEach(([k, v]) => {
    if (v !== '' && v != null) p.set(k, v);
  });
  return p.toString();
};

/** Section tin đăng: tiêu đề + link "Xem tất cả" + lưới. Ẩn hẳn khi rỗng (nếu hideWhenEmpty). */
const ListingSection = ({ title, icon, listings, loading, error, seeAllTo, hideWhenEmpty, columns }) => {
  if (!loading && !error && hideWhenEmpty && listings.length === 0) return null;
  if (error && hideWhenEmpty) return null;
  return (
    <Box sx={{ mb: 5 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Stack direction="row" alignItems="center" spacing={1}>
          {icon}
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            {title}
          </Typography>
        </Stack>
        {seeAllTo && (
          <Button component={RouterLink} to={seeAllTo} size="small">
            Xem tất cả →
          </Button>
        )}
      </Stack>
      {error ? (
        <Alert severity="error" variant="outlined">
          Không tải được mục này. Vui lòng thử lại sau.
        </Alert>
      ) : (
        <ListingGrid
          items={listings}
          loading={loading}
          skeletonCount={columns?.lg || 4}
          columns={columns}
        />
      )}
    </Box>
  );
};

const HomePage = () => {
  const navigate = useNavigate();
  const { hasAnyRole } = useAuth();
  const canPost = hasAnyRole([ROLES.LANDLORD, ROLES.ADMIN]);

  // Hero search state
  const [categoryCode, setCategoryCode] = useState('');
  const [provinceId, setProvinceId] = useState('');
  const [priceRange, setPriceRange] = useState('');
  const [keyword, setKeyword] = useState('');

  // Catalog
  const [categories, setCategories] = useState([]);
  const [provinces, setProvinces] = useState([]);

  // Sections
  const [featured, setFeatured] = useState({ data: [], loading: true, error: false });
  const [suggested, setSuggested] = useState({ data: [], loading: true, error: false });
  const [newest, setNewest] = useState({ data: [], loading: true, error: false });

  useEffect(() => {
    document.title = 'Webtro — Tìm phòng trọ nhanh và an toàn';
    let alive = true;

    catalogApi
      .getCategories()
      .then((d) => alive && setCategories(toItems(d)))
      .catch(() => {});
    catalogApi
      .getProvinces({ withCount: true, size: 8, sort: 'listingCount,desc' })
      .then((d) => alive && setProvinces(toItems(d)))
      .catch(() => {});

    searchApi
      .searchListings({ promoted: true, size: 4, sort: 'priority,desc' })
      .then((d) => alive && setFeatured({ data: toItems(d), loading: false, error: false }))
      .catch(() => alive && setFeatured({ data: [], loading: false, error: true }));

    listingApi
      .getSuggested({ source: 'HOMEPAGE', size: 8 })
      .then((d) => alive && setSuggested({ data: toItems(d), loading: false, error: false }))
      .catch(() => alive && setSuggested({ data: [], loading: false, error: true }));

    searchApi
      .searchListings({ size: 8, sort: 'publishedAt,desc' })
      .then((d) => alive && setNewest({ data: toItems(d), loading: false, error: false }))
      .catch(() => alive && setNewest({ data: [], loading: false, error: true }));

    return () => {
      alive = false;
    };
  }, []);

  const handleSearch = (e) => {
    e?.preventDefault();
    const [priceFrom, priceTo] = priceRange ? priceRange.split('-') : ['', ''];
    const q = buildQuery({
      keyword: keyword.trim(),
      categoryCode,
      provinceId,
      priceFrom,
      priceTo,
    });
    navigate(`/tim-kiem${q ? `?${q}` : ''}`);
  };

  const categoryOptions = categories.length
    ? categories.map((c) => ({ code: c.code, name: c.name, count: c.listingCount }))
    : Object.entries(CATEGORY_CODES).map(([code, name]) => ({ code, name }));

  return (
    <Box>
      {/* HERO */}
      <Box
        sx={{
          background: 'linear-gradient(135deg, #0F766E 0%, #0B5A54 100%)',
          color: '#fff',
          py: { xs: 5, md: 8 },
        }}
      >
        <Container maxWidth="lg">
          <Typography variant="h3" component="h1" sx={{ fontWeight: 800, fontSize: { xs: '2rem', md: '2.75rem' } }}>
            Tìm phòng trọ ưng ý, nhanh và an toàn
          </Typography>
          <Typography sx={{ mt: 1, opacity: 0.9 }}>
            Hàng nghìn tin đăng đã kiểm duyệt trên toàn quốc
          </Typography>

          <Paper
            component="form"
            onSubmit={handleSearch}
            sx={{ mt: 3, p: { xs: 1.5, md: 1 }, borderRadius: 2 }}
          >
            <Grid container spacing={1} alignItems="center">
              <Grid item xs={12} md={2.5}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="Loại tin"
                  value={categoryCode}
                  onChange={(e) => setCategoryCode(e.target.value)}
                >
                  <MenuItem value="">Tất cả loại</MenuItem>
                  {categoryOptions.map((c) => (
                    <MenuItem key={c.code} value={c.code}>
                      {c.name}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={2.5}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="Tỉnh/TP"
                  value={provinceId}
                  onChange={(e) => setProvinceId(e.target.value)}
                >
                  <MenuItem value="">Toàn quốc</MenuItem>
                  {provinces.map((p) => (
                    <MenuItem key={p.id} value={p.id}>
                      {p.name}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={2.5}>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="Khoảng giá"
                  value={priceRange}
                  onChange={(e) => setPriceRange(e.target.value)}
                >
                  {PRICE_PRESETS.map((p) => (
                    <MenuItem key={p.label} value={p.value}>
                      {p.label}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={2.5}>
                <TextField
                  fullWidth
                  size="small"
                  placeholder="Từ khóa..."
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  inputProps={{ maxLength: 100 }}
                />
              </Grid>
              <Grid item xs={12} md={2}>
                <Button
                  type="submit"
                  variant="contained"
                  fullWidth
                  startIcon={<SearchIcon />}
                  sx={{ height: 40 }}
                >
                  Tìm
                </Button>
              </Grid>
            </Grid>
          </Paper>

          <Stack direction="row" spacing={1} sx={{ mt: 2, flexWrap: 'wrap', gap: 1 }}>
            <Typography variant="body2" sx={{ opacity: 0.85, alignSelf: 'center' }}>
              Gợi ý nhanh:
            </Typography>
            {QUICK_CHIPS.map((c) => (
              <Chip
                key={c.label}
                label={c.label}
                onClick={() => navigate(`/tim-kiem?${buildQuery(c.params)}`)}
                sx={{ bgcolor: 'rgba(255,255,255,0.18)', color: '#fff', '&:hover': { bgcolor: 'rgba(255,255,255,0.3)' } }}
              />
            ))}
          </Stack>
        </Container>
      </Box>

      <Container maxWidth="lg" sx={{ py: { xs: 4, md: 6 } }}>
        {/* DANH MỤC */}
        <Box sx={{ mb: 5 }}>
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
            Danh mục phổ biến
          </Typography>
          {categories.length === 0 ? (
            <Grid container spacing={2}>
              {Array.from({ length: 7 }).map((_, i) => (
                // eslint-disable-next-line react/no-array-index-key
                <Grid item xs={4} sm={3} md={12 / 7} key={i}>
                  <LoadingSkeleton variant="stat-card" />
                </Grid>
              ))}
            </Grid>
          ) : (
            <Grid container spacing={2}>
              {categoryOptions.map((c) => (
                <Grid item xs={4} sm={3} md={12 / 7} key={c.code}>
                  <Card variant="outlined" sx={{ height: '100%' }}>
                    <CardActionArea
                      component={RouterLink}
                      to={`/tim-kiem?categoryCode=${c.code}`}
                      sx={{ p: 2, textAlign: 'center', height: '100%' }}
                    >
                      <HomeWorkOutlinedIcon color="primary" />
                      <Typography variant="body2" sx={{ fontWeight: 600, mt: 0.5 }}>
                        {c.name}
                      </Typography>
                      {typeof c.count === 'number' && (
                        <Typography variant="caption" color="text.secondary">
                          {compactNumber(c.count)} tin
                        </Typography>
                      )}
                    </CardActionArea>
                  </Card>
                </Grid>
              ))}
            </Grid>
          )}
        </Box>

        <ListingSection
          title="Tin nổi bật"
          icon={<span>★</span>}
          listings={featured.data}
          loading={featured.loading}
          error={featured.error}
          seeAllTo="/tim-kiem?promoted=true"
          hideWhenEmpty
          columns={{ xs: 1, sm: 2, md: 4 }}
        />

        <ListingSection
          title="Gợi ý cho bạn"
          icon={<span>✨</span>}
          listings={suggested.data}
          loading={suggested.loading}
          error={suggested.error}
          seeAllTo="/tim-kiem"
          hideWhenEmpty
          columns={{ xs: 1, sm: 2, md: 4 }}
        />

        <ListingSection
          title="Tin mới nhất"
          icon={<span>🆕</span>}
          listings={newest.data}
          loading={newest.loading}
          error={newest.error}
          seeAllTo="/tim-kiem?sort=publishedAt,desc"
          columns={{ xs: 1, sm: 2, md: 4 }}
        />

        {/* KHU VỰC PHỔ BIẾN */}
        {provinces.length > 0 && (
          <Box sx={{ mb: 5 }}>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2 }}>
              <PlaceOutlinedIcon color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Khu vực phổ biến
              </Typography>
            </Stack>
            <Grid container spacing={2}>
              {provinces.map((p) => (
                <Grid item xs={6} sm={4} md={3} key={p.id}>
                  <Card variant="outlined">
                    <CardActionArea component={RouterLink} to={`/tim-kiem?provinceId=${p.id}`} sx={{ p: 2 }}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                        {p.name}
                      </Typography>
                      {typeof p.listingCount === 'number' && (
                        <Typography variant="caption" color="text.secondary">
                          {compactNumber(p.listingCount)} tin
                        </Typography>
                      )}
                    </CardActionArea>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Box>
        )}

        {/* CTA CHỦ TRỌ */}
        {!canPost && (
          <>
            <Divider sx={{ mb: 3, display: { xs: 'none', sm: 'block' } }} />
            <Paper
              variant="outlined"
              sx={{
                p: 3,
                display: { xs: 'none', sm: 'flex' },
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: 2,
              }}
            >
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Bạn là chủ trọ?
                </Typography>
                <Typography color="text.secondary">
                  Đăng tin miễn phí, tiếp cận hàng nghìn người thuê.
                </Typography>
              </Box>
              <Button variant="contained" component={RouterLink} to="/dang-ky">
                Đăng tin ngay
              </Button>
            </Paper>
          </>
        )}
      </Container>
    </Box>
  );
};

export default HomePage;
