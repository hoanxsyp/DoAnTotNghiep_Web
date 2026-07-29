import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams, Link as RouterLink } from 'react-router-dom';
import {
  Container, Grid, Box, Typography, Stack, Chip, Button, TextField, MenuItem, Paper,
  Breadcrumbs, Link, Pagination, Drawer, IconButton, Divider, ToggleButton, ToggleButtonGroup,
  FormControlLabel, Checkbox, FormGroup, LinearProgress, Accordion, AccordionSummary,
  AccordionDetails, Badge,
} from '@mui/material';
import TuneIcon from '@mui/icons-material/Tune';
import CloseIcon from '@mui/icons-material/Close';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import searchApi from '@/api/searchApi';
import catalogApi from '@/api/catalogApi';
import listingApi from '@/api/listingApi';
import ListingGrid from '@/components/listing/ListingGrid';
import PriceRangeSlider from '@/components/search/PriceRangeSlider';
import EmptyState from '@/components/common/EmptyState';
import useResponsive from '@/hooks/useResponsive';
import { GENDER_REQUIREMENT, FURNITURE_STATUS, PAGE_SIZE } from '@/constants';

const toItems = (d) => (Array.isArray(d) ? d : d?.items ?? []);

const SORTS = [
  { value: 'relevance,desc', label: 'Liên quan nhất' },
  { value: 'publishedAt,desc', label: 'Mới nhất' },
  { value: 'price,asc', label: 'Giá thấp → cao' },
  { value: 'price,desc', label: 'Giá cao → thấp' },
  { value: 'area,desc', label: 'Diện tích lớn nhất' },
];

const TOILET = { PRIVATE: 'Riêng', SHARED: 'Chung' };
const CURFEW = { FREE: 'Tự do', CURFEW: 'Có giờ giấc' };

const PRICE_MAX = 20000000;
const AREA_MAX = 100;

// Loại ký tự nguy hiểm khỏi keyword (lớp lọc UX; chống XSS/SQLi thật ở BE).
const sanitizeKeyword = (s = '') => s.replace(/[<>"'`\\]/g, '').slice(0, 100);

const FilterPanel = ({ filters, onChange, onReset, categories, provinces, districts, amenities }) => {
  const set = (key) => (value) => onChange(key, value);
  const amenityIds = filters.amenityIds ? filters.amenityIds.split(',').filter(Boolean) : [];

  const toggleAmenity = (id) => {
    const idStr = String(id);
    const next = amenityIds.includes(idStr)
      ? amenityIds.filter((x) => x !== idStr)
      : [...amenityIds, idStr];
    onChange('amenityIds', next.join(','));
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
          Bộ lọc
        </Typography>
        <Button size="small" color="inherit" onClick={onReset}>
          Xóa tất cả
        </Button>
      </Stack>

      <Accordion defaultExpanded disableGutters>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>Loại tin</AccordionSummary>
        <AccordionDetails>
          <FormGroup>
            {categories.map((c) => (
              <FormControlLabel
                key={c.code}
                control={
                  <Checkbox
                    size="small"
                    checked={filters.categoryCode === c.code}
                    onChange={(e) => set('categoryCode')(e.target.checked ? c.code : '')}
                  />
                }
                label={c.name}
              />
            ))}
          </FormGroup>
        </AccordionDetails>
      </Accordion>

      <Accordion defaultExpanded disableGutters>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>Khu vực</AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2}>
            <TextField
              select
              size="small"
              label="Tỉnh/TP"
              value={filters.provinceId || ''}
              onChange={(e) => {
                onChange('provinceId', e.target.value);
                onChange('districtId', '');
              }}
            >
              <MenuItem value="">Toàn quốc</MenuItem>
              {provinces.map((p) => (
                <MenuItem key={p.id} value={p.id}>
                  {p.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Quận/huyện"
              value={filters.districtId || ''}
              onChange={(e) => set('districtId')(e.target.value)}
              disabled={!filters.provinceId}
            >
              <MenuItem value="">Tất cả quận/huyện</MenuItem>
              {districts.map((d) => (
                <MenuItem key={d.id} value={d.id}>
                  {d.name}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </AccordionDetails>
      </Accordion>

      <Accordion defaultExpanded disableGutters>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>Khoảng giá</AccordionSummary>
        <AccordionDetails>
          <PriceRangeSlider
            min={0}
            max={PRICE_MAX}
            step={500000}
            unit="đ"
            value={[Number(filters.priceFrom) || 0, Number(filters.priceTo) || PRICE_MAX]}
            onChange={([from, to]) => {
              onChange('priceFrom', from > 0 ? from : '');
              onChange('priceTo', to < PRICE_MAX ? to : '');
            }}
          />
        </AccordionDetails>
      </Accordion>

      <Accordion disableGutters>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>Diện tích</AccordionSummary>
        <AccordionDetails>
          <PriceRangeSlider
            min={0}
            max={AREA_MAX}
            step={5}
            unit="m²"
            value={[Number(filters.areaFrom) || 0, Number(filters.areaTo) || AREA_MAX]}
            onChange={([from, to]) => {
              onChange('areaFrom', from > 0 ? from : '');
              onChange('areaTo', to < AREA_MAX ? to : '');
            }}
          />
        </AccordionDetails>
      </Accordion>

      {filters.categoryCode === 'ROOMMATE' && (
        <Accordion defaultExpanded disableGutters>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>Ở ghép — giới tính</AccordionSummary>
          <AccordionDetails>
            <ToggleButtonGroup
              exclusive
              size="small"
              value={filters.genderRequirement || ''}
              onChange={(_, v) => set('genderRequirement')(v || '')}
            >
              {Object.entries(GENDER_REQUIREMENT).map(([k, label]) => (
                <ToggleButton key={k} value={k}>
                  {label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
          </AccordionDetails>
        </Accordion>
      )}

      <Accordion disableGutters>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>Nội thất</AccordionSummary>
        <AccordionDetails>
          <ToggleButtonGroup
            exclusive
            size="small"
            value={filters.furnitureStatus || ''}
            onChange={(_, v) => set('furnitureStatus')(v || '')}
          >
            {Object.entries(FURNITURE_STATUS).map(([k, label]) => (
              <ToggleButton key={k} value={k}>
                {label}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </AccordionDetails>
      </Accordion>

      <Accordion disableGutters>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>Nhà vệ sinh &amp; giờ giấc</AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2}>
            <ToggleButtonGroup
              exclusive
              size="small"
              value={filters.toiletType || ''}
              onChange={(_, v) => set('toiletType')(v || '')}
            >
              {Object.entries(TOILET).map(([k, label]) => (
                <ToggleButton key={k} value={k}>
                  {label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
            <ToggleButtonGroup
              exclusive
              size="small"
              value={filters.curfewType || ''}
              onChange={(_, v) => set('curfewType')(v || '')}
            >
              {Object.entries(CURFEW).map(([k, label]) => (
                <ToggleButton key={k} value={k}>
                  {label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
            <FormGroup>
              <FormControlLabel
                control={
                  <Checkbox
                    size="small"
                    checked={filters.petAllowed === 'true'}
                    onChange={(e) => set('petAllowed')(e.target.checked ? 'true' : '')}
                  />
                }
                label="Cho nuôi thú cưng"
              />
              <FormControlLabel
                control={
                  <Checkbox
                    size="small"
                    checked={filters.parkingAvailable === 'true'}
                    onChange={(e) => set('parkingAvailable')(e.target.checked ? 'true' : '')}
                  />
                }
                label="Có chỗ để xe"
              />
            </FormGroup>
          </Stack>
        </AccordionDetails>
      </Accordion>

      {amenities.length > 0 && (
        <Accordion disableGutters>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>Tiện ích</AccordionSummary>
          <AccordionDetails>
            <FormGroup>
              {amenities.slice(0, 12).map((a) => (
                <FormControlLabel
                  key={a.id}
                  control={
                    <Checkbox
                      size="small"
                      checked={amenityIds.includes(String(a.id))}
                      onChange={() => toggleAmenity(a.id)}
                    />
                  }
                  label={a.name}
                />
              ))}
            </FormGroup>
          </AccordionDetails>
        </Accordion>
      )}
    </Box>
  );
};

const FILTER_KEYS = [
  'keyword', 'categoryCode', 'provinceId', 'districtId', 'priceFrom', 'priceTo',
  'areaFrom', 'areaTo', 'genderRequirement', 'furnitureStatus', 'toiletType', 'curfewType',
  'petAllowed', 'parkingAvailable', 'amenityIds',
];

const SearchPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const { isMobile } = useResponsive();

  const [categories, setCategories] = useState([]);
  const [provinces, setProvinces] = useState([]);
  const [districts, setDistricts] = useState([]);
  const [amenities, setAmenities] = useState([]);

  const [result, setResult] = useState({ items: [], totalElements: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [suggested, setSuggested] = useState([]);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const filters = useMemo(() => {
    const obj = {};
    FILTER_KEYS.forEach((k) => {
      const v = searchParams.get(k);
      if (v != null) obj[k] = v;
    });
    return obj;
  }, [searchParams]);

  const page = Number(searchParams.get('page')) || 0;
  const sort = searchParams.get('sort') || 'relevance,desc';

  const activeFilterCount = FILTER_KEYS.filter(
    (k) => k !== 'keyword' && searchParams.get(k),
  ).length;

  // Catalog: tải 1 lần
  useEffect(() => {
    catalogApi.getCategories().then((d) => setCategories(toItems(d))).catch(() => {});
    catalogApi
      .getProvinces()
      .then((d) => setProvinces(toItems(d)))
      .catch(() => {});
    catalogApi.getAmenities().then((d) => setAmenities(toItems(d))).catch(() => {});
  }, []);

  // Districts theo province
  useEffect(() => {
    if (!filters.provinceId) {
      setDistricts([]);
      return;
    }
    catalogApi.getDistricts(filters.provinceId).then((d) => setDistricts(toItems(d))).catch(() => setDistricts([]));
  }, [filters.provinceId]);

  // Fetch kết quả mỗi khi URL đổi
  const queryString = searchParams.toString();
  useEffect(() => {
    document.title = 'Kết quả tìm kiếm — Webtro';
    let alive = true;
    setLoading(true);
    setError(false);

    const params = {};
    FILTER_KEYS.forEach((k) => {
      const v = searchParams.get(k);
      if (v) params[k] = k === 'keyword' ? sanitizeKeyword(v) : v;
    });
    params.page = page;
    params.size = PAGE_SIZE;
    params.sort = sort;

    searchApi
      .searchListings(params)
      .then((data) => {
        if (!alive) return;
        setResult({
          items: toItems(data),
          totalElements: data?.totalElements ?? toItems(data).length,
          totalPages: data?.totalPages ?? 1,
        });
        if (toItems(data).length === 0) {
          listingApi
            .getSuggested({ source: 'LOW_RESULT_SEARCH', size: 6 })
            .then((s) => alive && setSuggested(toItems(s)))
            .catch(() => {});
        } else {
          setSuggested([]);
        }
      })
      .catch(() => alive && setError(true))
      .finally(() => alive && setLoading(false));

    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryString]);

  const updateParam = useCallback(
    (key, value) => {
      const next = new URLSearchParams(searchParams);
      if (value === '' || value == null) next.delete(key);
      else next.set(key, value);
      if (key !== 'page') next.set('page', '0'); // đổi filter -> về trang đầu
      setSearchParams(next, { replace: key !== 'page' });
    },
    [searchParams, setSearchParams],
  );

  const handleReset = useCallback(() => {
    setSearchParams({}, { replace: true });
  }, [setSearchParams]);

  const heading = useMemo(() => {
    const parts = [];
    if (filters.categoryCode) {
      const c = categories.find((x) => x.code === filters.categoryCode);
      parts.push(c?.name || 'Tin cho thuê');
    } else {
      parts.push('Phòng trọ, nhà cho thuê');
    }
    if (filters.provinceId) {
      const p = provinces.find((x) => String(x.id) === String(filters.provinceId));
      if (p) parts.push(`tại ${p.name}`);
    }
    return parts.join(' ');
  }, [filters.categoryCode, filters.provinceId, categories, provinces]);

  const panel = (
    <FilterPanel
      filters={filters}
      onChange={updateParam}
      onReset={handleReset}
      categories={categories}
      provinces={provinces}
      districts={districts}
      amenities={amenities}
    />
  );

  const emptyNode = (
    <Box>
      <EmptyState
        title="Không tìm thấy tin nào phù hợp"
        description="Bộ lọc của bạn hiện hơi hẹp. Thử nới khoảng giá, mở rộng khu vực hoặc bỏ bớt tiêu chí."
        action={
          <Button variant="contained" onClick={handleReset}>
            Xóa toàn bộ bộ lọc
          </Button>
        }
      />
      {suggested.length > 0 && (
        <Box sx={{ mt: 2 }}>
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
            ✨ Có thể bạn quan tâm
          </Typography>
          <ListingGrid items={suggested} columns={{ xs: 1, sm: 2, md: 3 }} />
        </Box>
      )}
    </Box>
  );

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 2, md: 3 } }}>
      <Breadcrumbs sx={{ mb: 1 }}>
        <Link component={RouterLink} to="/" underline="hover" color="inherit">
          Trang chủ
        </Link>
        <Typography color="text.primary">Tìm kiếm</Typography>
      </Breadcrumbs>

      <Grid container spacing={3}>
        {/* Sidebar filter (md+) */}
        {!isMobile && (
          <Grid item md={3}>
            <Paper variant="outlined" sx={{ p: 2, position: 'sticky', top: 88 }}>
              {panel}
            </Paper>
          </Grid>
        )}

        <Grid item xs={12} md={!isMobile ? 9 : 12}>
          <Typography variant="h5" component="h1" sx={{ fontWeight: 700 }}>
            {heading}
          </Typography>

          {/* Thanh công cụ */}
          <Stack
            direction="row"
            justifyContent="space-between"
            alignItems="center"
            sx={{ mt: 1, mb: 2, flexWrap: 'wrap', gap: 1 }}
          >
            <Typography color="text.secondary">
              {loading ? 'Đang tìm…' : `Tìm thấy ${result.totalElements} tin`}
            </Typography>
            <Stack direction="row" spacing={1}>
              {isMobile && (
                <Button
                  variant="outlined"
                  startIcon={
                    <Badge badgeContent={activeFilterCount} color="primary">
                      <TuneIcon />
                    </Badge>
                  }
                  onClick={() => setDrawerOpen(true)}
                >
                  Bộ lọc
                </Button>
              )}
              <TextField
                select
                size="small"
                label="Sắp xếp"
                value={sort}
                onChange={(e) => updateParam('sort', e.target.value)}
                sx={{ minWidth: 180 }}
              >
                {SORTS.map((s) => (
                  <MenuItem key={s.value} value={s.value}>
                    {s.label}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
          </Stack>

          {/* Chip filter đang bật */}
          {activeFilterCount > 0 && (
            <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap', gap: 1 }}>
              {FILTER_KEYS.filter((k) => k !== 'keyword' && searchParams.get(k)).map((k) => (
                <Chip key={k} label={k} onDelete={() => updateParam(k, '')} size="small" />
              ))}
              <Button size="small" onClick={handleReset}>
                Xóa lọc
              </Button>
            </Stack>
          )}

          {loading && <LinearProgress sx={{ mb: 1 }} />}

          {error ? (
            <EmptyState
              title="Không tải được kết quả"
              description="Đã có lỗi khi tìm kiếm. Vui lòng thử lại."
              action={
                <Button variant="contained" onClick={() => updateParam('_r', Date.now())}>
                  Thử lại
                </Button>
              }
            />
          ) : (
            <ListingGrid
              items={result.items}
              loading={loading}
              skeletonCount={6}
              emptyState={emptyNode}
              columns={{ xs: 1, sm: 2, md: 3 }}
            />
          )}

          {result.totalPages > 1 && !error && (
            <Stack alignItems="center" sx={{ mt: 4 }}>
              <Pagination
                count={result.totalPages}
                page={page + 1}
                onChange={(_, p) => updateParam('page', String(p - 1))}
                color="primary"
              />
            </Stack>
          )}
        </Grid>
      </Grid>

      {/* Drawer filter (mobile) */}
      <Drawer
        anchor="bottom"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        PaperProps={{ sx: { height: '92vh', borderTopLeftRadius: 16, borderTopRightRadius: 16 } }}
      >
        <Box sx={{ p: 2, position: 'sticky', top: 0, bgcolor: 'background.paper', zIndex: 1 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              Bộ lọc
            </Typography>
            <IconButton onClick={() => setDrawerOpen(false)} aria-label="Đóng bộ lọc">
              <CloseIcon />
            </IconButton>
          </Stack>
          <Divider sx={{ mt: 1 }} />
        </Box>
        <Box sx={{ p: 2, overflowY: 'auto', flex: 1 }}>{panel}</Box>
        <Box sx={{ p: 2, position: 'sticky', bottom: 0, bgcolor: 'background.paper', boxShadow: 3 }}>
          <Button variant="contained" fullWidth onClick={() => setDrawerOpen(false)}>
            Xem {result.totalElements} kết quả
          </Button>
        </Box>
      </Drawer>
    </Container>
  );
};

export default SearchPage;
