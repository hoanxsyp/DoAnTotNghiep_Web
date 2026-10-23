import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { useSelector } from 'react-redux';
import {
  Alert, Avatar, Box, Button, Card, CardContent, Divider, Grid, Rating, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import rentalUnitApi from '@/api/rentalUnitApi';
import reviewApi from '@/api/reviewApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import StatusChip from '@/components/common/StatusChip';
import RentalUnitFormDialog from '@/components/listing/RentalUnitFormDialog';
import notify from '@/utils/toast';
import { formatDate } from '@/utils/format';
import { ROLES } from '@/constants';
import { selectRole } from '@/redux/authSlice';

/** Chi tiết một phòng: thông tin, lịch sử các tin đã đăng và đánh giá của phòng qua mọi lần đăng. */
const RentalUnitDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const role = useSelector(selectRole);
  const [unit, setUnit] = useState(null);
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await rentalUnitApi.getDetail(id);
      setUnit(data);
      // Đánh giá thuộc về phòng: đọc qua bất kỳ tin còn tồn tại nào của phòng.
      const anyListing = data?.listings?.[0];
      if (anyListing && data.reviewCount > 0) {
        const page = await reviewApi.getReviews(anyListing.id, { size: 50 }).catch(() => null);
        setReviews(page?.items || []);
      } else {
        setReviews([]);
      }
    } catch (e) {
      notify.apiError(e, 'Không tải được thông tin phòng');
      setUnit(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const relist = async () => {
    setActing(true);
    try {
      const created = await rentalUnitApi.relist(id);
      notify.success('Đã tạo tin nháp mới cho phòng. Hãy kiểm tra lại rồi gửi duyệt.');
      navigate(`/quan-ly/tin-dang/${created.id}/sua`);
    } catch (e) {
      notify.apiError(e, 'Không đăng lại được phòng');
    } finally {
      setActing(false);
    }
  };

  if (loading) return <LoadingSkeleton variant="listing-detail" />;
  if (!unit) {
    return (
      <EmptyState
        title="Không tìm thấy phòng"
        action={<Button variant="contained" component={RouterLink} to="/quan-ly/phong">Về danh sách phòng</Button>}
      />
    );
  }

  const actions = unit.availableActions || [];

  return (
    <Box>
      <PageHeader
        title={unit.name}
        subtitle={unit.fullAddress}
        action={(
          <Stack direction="row" spacing={1}>
            {actions.includes('RELIST') && <Button variant="contained" disabled={acting} onClick={relist}>Đăng lại</Button>}
            {actions.includes('CREATE_LISTING') && (
              <Button variant="outlined" component={RouterLink} to={`/quan-ly/tin-dang/tao?rentalUnitId=${unit.id}`}>
                Đăng tin mới
              </Button>
            )}
            {actions.includes('EDIT') && <Button onClick={() => setEditing(true)}>Sửa phòng</Button>}
          </Stack>
        )}
      />

      {unit.status === 'ARCHIVED' && (
        <Alert severity="info" sx={{ mb: 2 }}>Phòng đã được lưu trữ. Khôi phục phòng ở trang danh sách để đăng tin lại.</Alert>
      )}

      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="subtitle2" color="text.secondary">Điểm của phòng</Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="h4" sx={{ fontWeight: 700 }}>
                    {unit.reviewCount ? Number(unit.averageRating).toFixed(1) : '—'}
                  </Typography>
                  <Rating readOnly precision={0.1} value={Number(unit.averageRating) || 0} />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {unit.reviewCount || 0} đánh giá qua mọi lần đăng tin
                </Typography>
                <Divider />
                {unit.unitLabel && <Typography variant="body2">Số phòng: <b>{unit.unitLabel}</b></Typography>}
                <Typography variant="body2">Địa chỉ: {unit.fullAddress}</Typography>
                {unit.addressLocked && (
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <LockOutlinedIcon fontSize="small" color="action" />
                    <Typography variant="caption" color="text.secondary">
                      Đã có đánh giá nên địa chỉ và số phòng bị khoá
                    </Typography>
                  </Stack>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Các tin đã đăng cho phòng này</Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Tin đăng</TableCell>
                      <TableCell>Trạng thái</TableCell>
                      <TableCell>Ngày tạo</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(unit.listings || []).map((l) => (
                      <TableRow key={l.id} hover>
                        <TableCell>
                          <Stack direction="row" spacing={1} alignItems="center">
                            <Avatar variant="rounded" src={l.thumbnailUrl} sx={{ width: 48, height: 36 }} />
                            <Typography
                              component={RouterLink} to={`/quan-ly/tin-dang/${l.id}/sua`} variant="body2"
                              sx={{ color: 'text.primary', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
                            >
                              {l.title}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell><StatusChip status={l.status} /></TableCell>
                        <TableCell>{formatDate(l.createdAt)}</TableCell>
                      </TableRow>
                    ))}
                    {!(unit.listings || []).length && (
                      <TableRow><TableCell colSpan={3}>
                        <Typography variant="body2" color="text.secondary">Phòng chưa có tin đăng nào.</Typography>
                      </TableCell></TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Đánh giá của phòng</Typography>
              {reviews.length === 0 ? (
                <Typography variant="body2" color="text.secondary">Chưa có đánh giá.</Typography>
              ) : (
                <Stack spacing={1.5} divider={<Divider flexItem />}>
                  {reviews.map((r) => (
                    <Box key={r.id}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Typography variant="subtitle2">{r.author?.fullName || 'Người thuê'}</Typography>
                        <Rating size="small" readOnly value={r.rating} />
                        <Typography variant="caption" color="text.secondary">{formatDate(r.createdAt)}</Typography>
                      </Stack>
                      {r.rentedViaListingCreatedAt && (
                        <Typography variant="caption" color="text.secondary">
                          Thuê qua tin tháng {formatMonth(r.rentedViaListingCreatedAt)}
                        </Typography>
                      )}
                      {r.content && <Typography variant="body2" sx={{ mt: 0.5 }}>{r.content}</Typography>}
                    </Box>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <RentalUnitFormDialog
        open={editing}
        unit={unit}
        canOverrideLock={role === ROLES.ADMIN}
        onClose={() => setEditing(false)}
        onSaved={() => { setEditing(false); load(); }}
      />
    </Box>
  );
};

const formatMonth = (value) => {
  const d = new Date(value);
  return `${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
};

export default RentalUnitDetailPage;
