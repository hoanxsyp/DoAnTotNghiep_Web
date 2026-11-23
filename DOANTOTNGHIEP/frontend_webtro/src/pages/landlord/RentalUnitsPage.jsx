import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import {
  Box, Button, Card, CardActions, CardContent, Chip, Grid, InputAdornment, Rating, Stack, TablePagination,
  TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import rentalUnitApi from '@/api/rentalUnitApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import StatusChip from '@/components/common/StatusChip';
import RentalUnitFormDialog from '@/components/listing/RentalUnitFormDialog';
import notify from '@/utils/toast';
import { ROLES } from '@/constants';
import { selectRole } from '@/redux/authSlice';

const STATUS_FILTERS = [
  { value: 'ACTIVE', label: 'Đang dùng' },
  { value: 'ARCHIVED', label: 'Đã lưu trữ' },
  { value: '', label: 'Tất cả' },
];

/**
 * "Phòng của tôi" (docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 5). Mỗi tin đăng thuộc một phòng;
 * đánh giá gắn với phòng nên đóng tin rồi "Đăng lại" vẫn giữ đánh giá. Phòng có lịch sử không xoá
 * được, chỉ lưu trữ (RU-5).
 */
const RentalUnitsPage = () => {
  const navigate = useNavigate();
  const role = useSelector(selectRole);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(12);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState('ACTIVE');
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [dialog, setDialog] = useState({ open: false, unit: null });
  const [confirm, setConfirm] = useState(null); // { action, unit }
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size, sort: 'updatedAt,desc' };
      if (status) params.status = status;
      if (search) params.keyword = search;
      const data = await rentalUnitApi.getMine(params);
      setItems(data?.items || []);
      setTotal(data?.totalElements || 0);
    } catch (e) {
      notify.apiError(e, 'Không tải được danh sách phòng');
    } finally {
      setLoading(false);
    }
  }, [page, size, status, search]);

  useEffect(() => { load(); }, [load]);

  const run = async (action, unit) => {
    setActing(true);
    try {
      if (action === 'RELIST') {
        const created = await rentalUnitApi.relist(unit.id);
        notify.success('Đã tạo tin nháp mới cho phòng. Hãy kiểm tra lại rồi gửi duyệt.');
        navigate(`/quan-ly/tin-dang/${created.id}/sua`);
        return;
      }
      if (action === 'ARCHIVE') await rentalUnitApi.archive(unit.id);
      if (action === 'UNARCHIVE') await rentalUnitApi.unarchive(unit.id);
      notify.success('Thao tác thành công');
      setConfirm(null);
      load();
    } catch (e) {
      notify.apiError(e, 'Thao tác thất bại');
    } finally {
      setActing(false);
    }
  };

  const actions = (u) => u.availableActions || [];

  return (
    <Box>
      <PageHeader
        title="Phòng của tôi"
        subtitle="Mỗi tin đăng thuộc một phòng. Đánh giá gắn với phòng nên đăng lại tin vẫn giữ đánh giá cũ."
        action={(
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, unit: null })}>
            Thêm phòng
          </Button>
        )}
      />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }} justifyContent="space-between">
        <ToggleButtonGroup
          size="small" exclusive value={status}
          onChange={(_, v) => { if (v !== null) { setStatus(v); setPage(0); } }}
        >
          {STATUS_FILTERS.map((f) => <ToggleButton key={f.value || 'all'} value={f.value}>{f.label}</ToggleButton>)}
        </ToggleButtonGroup>
        <TextField
          size="small" placeholder="Tìm theo tên, địa chỉ, số phòng…" value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { setPage(0); setSearch(keyword.trim()); } }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }}
          sx={{ minWidth: 280 }}
        />
      </Stack>

      {loading ? (
        <LoadingSkeleton variant="listing-card" count={6} />
      ) : items.length === 0 ? (
        <EmptyState
          title="Chưa có phòng"
          description="Phòng được tạo tự động khi bạn đăng tin, hoặc bạn có thể thêm phòng trước rồi đăng tin sau."
          action={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, unit: null })}>Thêm phòng</Button>}
        />
      ) : (
        <Grid container spacing={2}>
          {items.map((u) => (
            <Grid item xs={12} sm={6} lg={4} key={u.id}>
              <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column', opacity: u.status === 'ARCHIVED' ? 0.7 : 1 }}>
                <CardContent sx={{ flexGrow: 1 }}>
                  <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                    <Typography
                      component={RouterLink} to={`/quan-ly/phong/${u.id}`}
                      variant="subtitle1"
                      sx={{ fontWeight: 700, color: 'text.primary', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
                    >
                      {u.name}
                    </Typography>
                    <Stack direction="row" spacing={0.5} alignItems="center">
                      {u.addressLocked && (
                        <Tooltip title="Đã có đánh giá: không đổi được địa chỉ và số phòng">
                          <LockOutlinedIcon fontSize="small" color="action" />
                        </Tooltip>
                      )}
                      {u.status === 'ARCHIVED' && <Chip size="small" label="Đã lưu trữ" />}
                    </Stack>
                  </Stack>
                  {u.unitLabel && <Typography variant="body2" color="text.secondary">Số phòng: {u.unitLabel}</Typography>}
                  <Stack direction="row" spacing={0.5} alignItems="flex-start" sx={{ mt: 1 }}>
                    <PlaceOutlinedIcon sx={{ fontSize: 16, mt: '2px' }} color="action" />
                    <Typography variant="body2" color="text.secondary">{u.fullAddress}</Typography>
                  </Stack>
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
                    <Rating size="small" readOnly precision={0.1} value={Number(u.averageRating) || 0} />
                    <Typography variant="caption" color="text.secondary">
                      {u.reviewCount ? `${Number(u.averageRating).toFixed(1)} · ${u.reviewCount} đánh giá` : 'Chưa có đánh giá'}
                    </Typography>
                  </Stack>
                  <Box sx={{ mt: 1.5 }}>
                    {u.liveListing ? (
                      <Stack direction="row" spacing={1} alignItems="center">
                        <StatusChip status={u.liveListing.status} />
                        <Typography
                          component={RouterLink} to={`/quan-ly/tin-dang/${u.liveListing.id}/sua`}
                          variant="body2" noWrap sx={{ color: 'text.primary', maxWidth: 220, '&:hover': { color: 'primary.main' } }}
                        >
                          {u.liveListing.title}
                        </Typography>
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        Không có tin đang đăng · {u.listingCount || 0} tin trước đây
                      </Typography>
                    )}
                  </Box>
                </CardContent>
                <CardActions sx={{ flexWrap: 'wrap', gap: 0.5, px: 2, pb: 2 }}>
                  {actions(u).includes('RELIST') && (
                    <Button size="small" variant="contained" disabled={acting} onClick={() => run('RELIST', u)}>Đăng lại</Button>
                  )}
                  {actions(u).includes('CREATE_LISTING') && (
                    <Button size="small" variant={actions(u).includes('RELIST') ? 'outlined' : 'contained'}
                      component={RouterLink} to={`/quan-ly/tin-dang/tao?rentalUnitId=${u.id}`}>
                      Đăng tin mới
                    </Button>
                  )}
                  {actions(u).includes('EDIT') && (
                    <Button size="small" onClick={() => setDialog({ open: true, unit: u })}>Sửa</Button>
                  )}
                  {actions(u).includes('ARCHIVE') && (
                    <Button size="small" color="inherit" onClick={() => setConfirm({ action: 'ARCHIVE', unit: u })}>Lưu trữ</Button>
                  )}
                  {actions(u).includes('UNARCHIVE') && (
                    <Button size="small" disabled={acting} onClick={() => run('UNARCHIVE', u)}>Khôi phục</Button>
                  )}
                </CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {total > size && (
        <TablePagination
          component="div" count={total} page={page} rowsPerPage={size}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[12, 24, 48]} labelRowsPerPage="Số phòng"
        />
      )}

      <RentalUnitFormDialog
        open={dialog.open}
        unit={dialog.unit}
        canOverrideLock={role === ROLES.ADMIN}
        onClose={() => setDialog({ open: false, unit: null })}
        onSaved={() => { setDialog({ open: false, unit: null }); load(); }}
      />

      <ConfirmDialog
        open={confirm?.action === 'ARCHIVE'}
        title="Lưu trữ phòng"
        message={`Lưu trữ phòng "${confirm?.unit?.name || ''}"? Phòng sẽ không dùng để đăng tin được nữa, nhưng đánh giá cũ vẫn giữ trên hồ sơ của bạn. Bạn có thể khôi phục sau.`}
        confirmText="Lưu trữ"
        loading={acting}
        onConfirm={() => run('ARCHIVE', confirm.unit)}
        onCancel={() => setConfirm(null)}
      />
    </Box>
  );
};

export default RentalUnitsPage;
