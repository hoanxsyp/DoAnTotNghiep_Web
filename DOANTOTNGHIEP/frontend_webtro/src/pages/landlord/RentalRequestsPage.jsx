import { useCallback, useEffect, useState } from 'react';
import {
  Avatar, Box, Button, Card, Chip, MenuItem, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material';
import rentalApi from '@/api/rentalApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import notify from '@/utils/toast';
import { formatDate } from '@/utils/format';
import { RENTAL_STATUS_META } from '@/constants';

const ACTION_COPY = {
  approve: { title: 'Duyệt lịch xem phòng', text: 'Đồng ý để người thuê đến xem phòng vào ngày đã chọn?', confirm: 'Duyệt' },
  reject: { title: 'Từ chối yêu cầu', text: 'Từ chối yêu cầu thuê phòng này?', confirm: 'Từ chối', severity: 'error' },
  rented: { title: 'Xác nhận đã thuê', text: 'Chỉ xác nhận sau khi hai bên đã gặp mặt và hoàn tất thỏa thuận trực tiếp.', confirm: 'Xác nhận đã thuê' },
  checkout: { title: 'Xác nhận đã trả phòng', text: 'Sau khi xác nhận, tin đăng sẽ được mở lại để có thể cho thuê tiếp.', confirm: 'Xác nhận trả phòng' },
};

const LandlordRentalRequestsPage = () => {
  const [items, setItems] = useState([]);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size, sort: 'createdAt,desc' };
      if (status) params.status = status;
      const data = await rentalApi.getForLandlord(params);
      setItems(data?.items || []);
      setTotal(data?.totalElements || 0);
    } catch (error) {
      notify.apiError(error, 'Không tải được yêu cầu thuê phòng');
    } finally {
      setLoading(false);
    }
  }, [page, size, status]);

  useEffect(() => { load(); }, [load]);

  const execute = async () => {
    if (!action) return;
    setSubmitting(true);
    try {
      if (action.type === 'approve') await rentalApi.approve(action.item.id);
      if (action.type === 'reject') await rentalApi.reject(action.item.id);
      if (action.type === 'rented') await rentalApi.confirmRented(action.item.id);
      if (action.type === 'checkout') await rentalApi.confirmCheckedOut(action.item.id);
      notify.success('Đã cập nhật yêu cầu thuê phòng.');
      setAction(null);
      load();
    } catch (error) {
      notify.apiError(error, 'Không cập nhật được yêu cầu');
    } finally {
      setSubmitting(false);
    }
  };

  const copy = action ? ACTION_COPY[action.type] : null;

  return (
    <Box>
      <PageHeader
        title="Yêu cầu thuê phòng"
        subtitle="Duyệt lịch xem phòng và xác nhận tình trạng thuê thực tế"
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
          <Table sx={{ minWidth: 920 }}>
            <TableHead>
              <TableRow>
                <TableCell>Người thuê</TableCell>
                <TableCell>Phòng</TableCell>
                <TableCell>Ngày muốn xem</TableCell>
                <TableCell>Trạng thái</TableCell>
                <TableCell align="right">Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={5}><LoadingSkeleton variant="table" columns={5} count={6} /></TableCell></TableRow>
              ) : items.length === 0 ? (
                <TableRow><TableCell colSpan={5} sx={{ border: 0 }}>
                  <EmptyState title="Chưa có yêu cầu thuê phòng" />
                </TableCell></TableRow>
              ) : items.map((item) => {
                const meta = RENTAL_STATUS_META[item.status] || { label: item.status, color: 'default' };
                return (
                  <TableRow key={item.id} hover>
                    <TableCell>
                      <Stack direction="row" spacing={1.25} alignItems="center">
                        <Avatar src={item.tenantAvatarUrl}>{item.tenantName?.charAt(0)}</Avatar>
                        <Box>
                          <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>{item.tenantName}</Typography>
                          <Typography variant="caption" color="text.secondary">{item.tenantPhone || 'Chưa có SĐT'}</Typography>
                        </Box>
                      </Stack>
                    </TableCell>
                    <TableCell>{item.listingTitle}</TableCell>
                    <TableCell>{formatDate(item.desiredViewingDate)}</TableCell>
                    <TableCell><Chip size="small" color={meta.color} label={meta.label} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        {item.status === 'PENDING' && <Button size="small" onClick={() => setAction({ type: 'approve', item })}>Duyệt</Button>}
                        {['PENDING', 'APPROVED'].includes(item.status) && <Button size="small" color="error" onClick={() => setAction({ type: 'reject', item })}>Từ chối</Button>}
                        {item.status === 'APPROVED' && <Button size="small" variant="contained" onClick={() => setAction({ type: 'rented', item })}>Xác nhận đã thuê</Button>}
                        {item.status === 'RENTED' && <Button size="small" variant="outlined" onClick={() => setAction({ type: 'checkout', item })}>Đã trả phòng</Button>}
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
        open={!!action}
        title={copy?.title}
        message={copy?.text}
        confirmText={copy?.confirm}
        severity={copy?.severity || 'warning'}
        loading={submitting}
        onConfirm={execute}
        onCancel={() => setAction(null)}
      />
    </Box>
  );
};

export default LandlordRentalRequestsPage;
