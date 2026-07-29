import { useCallback, useEffect, useState } from 'react';
import {
  Box, Card, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Stack, Typography,
  Chip, TextField, MenuItem, TablePagination, Button, Grid,
} from '@mui/material';
import paymentApi from '@/api/paymentApi';
import PageHeader from '@/components/dashboard/PageHeader';
import StatCard from '@/components/dashboard/StatCard';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import notify from '@/utils/toast';
import { formatCurrency, formatDateTime } from '@/utils/format';
import { PAYMENT_STATUS_META } from '@/constants';

const ContactsCell = ({ p }) => (
  <Box>
    <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>{p.packageName}</Typography>
    <Typography variant="caption" color="text.secondary" noWrap sx={{ maxWidth: 220, display: 'block' }}>{p.listingTitle}</Typography>
  </Box>
);

const PaymentsPage = () => {
  const [items, setItems] = useState([]);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState('');
  const [cancelTarget, setCancelTarget] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size, sort: 'createdAt,desc' };
      if (status) params.status = status;
      const data = await paymentApi.getMyPayments(params);
      setItems(data?.items || []);
      setTotal(data?.totalElements || 0);
      setSummary(data?.summary || null);
    } catch (e) {
      notify.apiError(e, 'Không tải được lịch sử thanh toán');
    } finally {
      setLoading(false);
    }
  }, [page, size, status]);

  useEffect(() => { load(); }, [load]);

  const handleCancel = async () => {
    const p = cancelTarget;
    setCancelTarget(null);
    if (!p) return;
    try {
      await paymentApi.cancelPayment(p.id);
      notify.success('Đã hủy giao dịch');
      load();
    } catch (e) {
      notify.apiError(e, 'Hủy giao dịch thất bại');
    }
  };

  return (
    <Box>
      <PageHeader
        title="Lịch sử thanh toán"
        subtitle="Toàn bộ giao dịch mua gói dịch vụ của bạn"
        action={(
          <TextField select size="small" label="Trạng thái" value={status} onChange={(e) => { setPage(0); setStatus(e.target.value); }} sx={{ minWidth: 160 }}>
            <MenuItem value="">Tất cả</MenuItem>
            {Object.entries(PAYMENT_STATUS_META).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}
          </TextField>
        )}
      />

      {summary && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={6} md={3}><StatCard label="Tổng đã thanh toán" value={formatCurrency(summary.totalPaid)} color="success" /></Grid>
          <Grid item xs={6} md={3}><StatCard label="Thành công" value={summary.successCount} color="success" /></Grid>
          <Grid item xs={6} md={3}><StatCard label="Thất bại" value={summary.failedCount} color="error" /></Grid>
          <Grid item xs={6} md={3}><StatCard label="Đã hoàn tiền" value={summary.refundedCount} color="info" /></Grid>
        </Grid>
      )}

      <Card>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table sx={{ minWidth: 760 }}>
            <TableHead>
              <TableRow>
                <TableCell>Mã giao dịch</TableCell>
                <TableCell>Gói / Tin</TableCell>
                <TableCell align="right">Số tiền</TableCell>
                <TableCell>Phương thức</TableCell>
                <TableCell>Trạng thái</TableCell>
                <TableCell>Thời gian</TableCell>
                <TableCell align="right">Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={7} sx={{ py: 3 }}><LoadingSkeleton variant="table" columns={7} count={6} /></TableCell></TableRow>
              ) : items.length === 0 ? (
                <TableRow><TableCell colSpan={7} sx={{ border: 0 }}><EmptyState title="Chưa có giao dịch nào" /></TableCell></TableRow>
              ) : items.map((p) => {
                const st = PAYMENT_STATUS_META[p.status] || { label: p.status, color: 'default' };
                return (
                  <TableRow key={p.id} hover>
                    <TableCell><Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{p.transactionCode}</Typography></TableCell>
                    <TableCell><ContactsCell p={p} /></TableCell>
                    <TableCell align="right"><Typography variant="body2" sx={{ fontWeight: 600 }}>{formatCurrency(p.amount)}</Typography></TableCell>
                    <TableCell><Typography variant="body2">{p.paymentMethod}</Typography></TableCell>
                    <TableCell><Chip size="small" color={st.color} label={p.statusLabel || st.label} /></TableCell>
                    <TableCell><Typography variant="caption">{formatDateTime(p.paidAt || p.createdAt)}</Typography></TableCell>
                    <TableCell align="right">
                      {p.status === 'PENDING' && (
                        <Button size="small" color="error" onClick={() => setCancelTarget(p)}>Hủy</Button>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div" count={total} page={page} onPageChange={(_, p) => setPage(p)}
          rowsPerPage={size} onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[20, 50, 100]} labelRowsPerPage="Số dòng"
        />
      </Card>

      <ConfirmDialog
        open={!!cancelTarget}
        title="Hủy giao dịch"
        message={`Hủy giao dịch ${cancelTarget?.transactionCode || ''}?`}
        confirmText="Hủy giao dịch"
        severity="warning"
        onConfirm={handleCancel}
        onCancel={() => setCancelTarget(null)}
      />
    </Box>
  );
};

export default PaymentsPage;
