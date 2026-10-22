import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box, Button, Card, Chip, MenuItem, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TablePagination, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import HomeWorkOutlinedIcon from '@mui/icons-material/HomeWorkOutlined';
import RateReviewOutlinedIcon from '@mui/icons-material/RateReviewOutlined';
import EditCalendarOutlinedIcon from '@mui/icons-material/EditCalendarOutlined';
import dayjs from 'dayjs';
import rentalApi from '@/api/rentalApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import RentalViewingDateDialog from '@/components/rental/RentalViewingDateDialog';
import notify from '@/utils/toast';
import { formatDate, formatPrice } from '@/utils/format';
import { RENTAL_STATUS_META } from '@/constants';

const RentalRequestsPage = () => {
  const [items, setItems] = useState([]);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [cancelling, setCancelling] = useState(null);
  const [editing, setEditing] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size, sort: 'createdAt,desc' };
      if (status) params.status = status;
      const data = await rentalApi.getMine(params);
      setItems(data?.items || []);
      setTotal(data?.totalElements || 0);
    } catch (error) {
      notify.apiError(error, 'Không tải được yêu cầu thuê phòng');
    } finally {
      setLoading(false);
    }
  }, [page, size, status]);

  useEffect(() => { load(); }, [load]);

  const cancel = async () => {
    if (!cancelling) return;
    try {
      await rentalApi.cancel(cancelling.id);
      notify.success('Đã hủy yêu cầu thuê phòng.');
      setCancelling(null);
      load();
    } catch (error) {
      notify.apiError(error, 'Không hủy được yêu cầu');
    }
  };

  return (
    <Box>
      <PageHeader
        title="Yêu cầu thuê phòng"
        subtitle="Theo dõi lịch xem phòng và trạng thái thuê của bạn"
        action={(
          <TextField
            select size="small" label="Trạng thái" value={status}
            onChange={(event) => { setStatus(event.target.value); setPage(0); }}
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="">Tất cả</MenuItem>
            {Object.entries(RENTAL_STATUS_META).map(([key, meta]) => (
              <MenuItem key={key} value={key}>{meta.label}</MenuItem>
            ))}
          </TextField>
        )}
      />

      <Card>
        <TableContainer>
          <Table sx={{ minWidth: 760 }}>
            <TableHead>
              <TableRow>
                <TableCell>Phòng</TableCell>
                <TableCell>Ngày muốn xem</TableCell>
                <TableCell>Trạng thái</TableCell>
                <TableCell>Phản hồi chủ trọ</TableCell>
                <TableCell align="right">Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={5}><LoadingSkeleton variant="table" columns={5} count={5} /></TableCell></TableRow>
              ) : items.length === 0 ? (
                <TableRow><TableCell colSpan={5} sx={{ border: 0 }}>
                  <EmptyState
                    title="Chưa có yêu cầu thuê phòng"
                    description="Bạn có thể gửi yêu cầu từ trang chi tiết hoặc danh sách tin đã lưu."
                    icon={<HomeWorkOutlinedIcon />}
                  />
                </TableCell></TableRow>
              ) : items.map((item) => {
                const meta = RENTAL_STATUS_META[item.status] || { label: item.status, color: 'default' };
                const detailUrl = `/tin/${item.listingSlug ? `${item.listingSlug}-` : ''}${item.listingId}`;
                const approvedCancellationAllowed = item.status !== 'APPROVED'
                  || dayjs(item.desiredViewingDate).isAfter(dayjs().add(1, 'day'), 'day');
                return (
                  <TableRow key={item.id} hover>
                    <TableCell>
                      <Typography component={RouterLink} to={detailUrl} color="inherit" sx={{ fontWeight: 600, textDecoration: 'none' }}>
                        {item.listingTitle}
                      </Typography>
                      {item.rentalUnitName && item.rentalUnitName !== item.listingTitle && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                          Phòng: {item.rentalUnitName}
                        </Typography>
                      )}
                      <Typography variant="caption" color="primary">{formatPrice(item.listingPrice)}/tháng</Typography>
                    </TableCell>
                    <TableCell>{formatDate(item.desiredViewingDate)}</TableCell>
                    <TableCell><Chip size="small" color={meta.color} label={meta.label} /></TableCell>
                    <TableCell>{item.landlordNote || '—'}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        {item.status === 'PENDING' && (
                          <Button
                            size="small"
                            startIcon={<EditCalendarOutlinedIcon />}
                            onClick={() => setEditing(item)}
                          >
                            Đổi ngày
                          </Button>
                        )}
                        {['PENDING', 'APPROVED'].includes(item.status) && (
                          <Tooltip
                            title={approvedCancellationAllowed
                              ? ''
                              : 'Yêu cầu đã duyệt chỉ được hủy trước ngày xem ít nhất 2 ngày.'}
                          >
                            <span>
                              <Button
                                size="small"
                                color="error"
                                disabled={!approvedCancellationAllowed}
                                onClick={() => setCancelling(item)}
                              >
                                Hủy
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                        {['RENTED', 'CHECKED_OUT'].includes(item.status) && (
                          <Button size="small" startIcon={<RateReviewOutlinedIcon />} component={RouterLink} to={detailUrl}>
                            Đánh giá
                          </Button>
                        )}
                      </Stack>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div" count={total} page={page} onPageChange={(_, next) => setPage(next)}
          rowsPerPage={size} onRowsPerPageChange={(event) => { setSize(Number(event.target.value)); setPage(0); }}
          rowsPerPageOptions={[20, 50]} labelRowsPerPage="Số dòng"
        />
      </Card>

      <ConfirmDialog
        open={!!cancelling}
        title="Hủy yêu cầu thuê phòng"
        message={`Bạn có chắc muốn hủy yêu cầu thuê “${cancelling?.listingTitle || ''}”? Chủ trọ sẽ nhận được thông báo về việc hủy này.`}
        confirmText="Hủy yêu cầu"
        severity="error"
        onConfirm={cancel}
        onCancel={() => setCancelling(null)}
      />

      <RentalViewingDateDialog
        open={!!editing}
        request={editing}
        onClose={() => setEditing(null)}
        onSuccess={load}
      />
    </Box>
  );
};

export default RentalRequestsPage;
