import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Grid, Stack, TextField, MenuItem, FormControlLabel, Switch, Pagination, Chip,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import favoriteApi from '@/api/favoriteApi';
import ListingCard from '@/components/listing/ListingCard';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import notify from '@/utils/toast';
import { PAGE_SIZE } from '@/constants';

const SORTS = [
  { value: 'createdAt,desc', label: 'Lưu gần nhất' },
  { value: 'price,asc', label: 'Giá thấp đến cao' },
  { value: 'price,desc', label: 'Giá cao đến thấp' },
];

const SavedListingsPage = () => {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [sort, setSort] = useState('createdAt,desc');
  const [availableOnly, setAvailableOnly] = useState(false);
  const [removing, setRemoving] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await favoriteApi.getMyFavorites({ page, size: PAGE_SIZE, sort, availableOnly });
      setItems(data?.items || []);
      setTotalPages(data?.totalPages || 0);
      setTotalElements(data?.totalElements || 0);
    } catch (e) {
      notify.apiError(e, 'Không tải được danh sách tin đã lưu');
    } finally {
      setLoading(false);
    }
  }, [page, sort, availableOnly]);

  useEffect(() => { load(); }, [load]);

  const handleConfirmRemove = async () => {
    const listing = removing;
    setRemoving(null);
    if (!listing) return;
    try {
      await favoriteApi.remove(listing.id);
      notify.success('Đã bỏ lưu tin');
      setItems((prev) => prev.filter((i) => i.id !== listing.id));
      setTotalElements((n) => Math.max(0, n - 1));
    } catch (e) {
      notify.apiError(e, 'Bỏ lưu tin thất bại');
    }
  };

  return (
    <Box>
      <PageHeader
        title="Tin đã lưu"
        subtitle={totalElements ? `${totalElements} tin trong danh sách yêu thích` : 'Danh sách tin bạn quan tâm'}
        action={(
          <Stack direction="row" spacing={1.5} alignItems="center">
            <TextField select size="small" value={sort} onChange={(e) => { setPage(0); setSort(e.target.value); }} sx={{ minWidth: 180 }}>
              {SORTS.map((s) => <MenuItem key={s.value} value={s.value}>{s.label}</MenuItem>)}
            </TextField>
            <FormControlLabel
              control={<Switch checked={availableOnly} onChange={(e) => { setPage(0); setAvailableOnly(e.target.checked); }} />}
              label="Còn hiển thị"
            />
          </Stack>
        )}
      />

      {loading ? (
        <Grid container spacing={2}>
          {Array.from({ length: 8 }).map((_, i) => (
            <Grid item xs={12} sm={6} md={4} lg={3} key={i}><LoadingSkeleton variant="listing-card" /></Grid>
          ))}
        </Grid>
      ) : items.length === 0 ? (
        <EmptyState
          title="Chưa có tin nào được lưu"
          description="Bấm biểu tượng trái tim trên tin đăng để lưu lại và xem sau."
          action={<Chip label="Tìm phòng ngay" color="primary" onClick={() => navigate('/tim-kiem')} clickable icon={<SearchIcon />} />}
        />
      ) : (
        <>
          <Grid container spacing={2}>
            {items.map((it) => (
              <Grid item xs={12} sm={6} md={4} lg={3} key={it.id}>
                <ListingCard
                  listing={{ ...it, favoritedByMe: true }}
                  showFavorite
                  unavailable={!!it.notAvailable}
                  onFavoriteToggle={() => setRemoving(it)}
                />
              </Grid>
            ))}
          </Grid>
          {totalPages > 1 && (
            <Stack alignItems="center" sx={{ mt: 3 }}>
              <Pagination count={totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} color="primary" />
            </Stack>
          )}
        </>
      )}

      <ConfirmDialog
        open={!!removing}
        title="Bỏ lưu tin"
        message={`Bỏ tin "${removing?.title || ''}" khỏi danh sách đã lưu?`}
        confirmText="Bỏ lưu"
        severity="warning"
        onConfirm={handleConfirmRemove}
        onCancel={() => setRemoving(null)}
      />
    </Box>
  );
};

export default SavedListingsPage;
