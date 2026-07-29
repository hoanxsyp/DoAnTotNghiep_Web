import { useState } from 'react';
import {
  Box, Stack, TextField, MenuItem, Chip, Typography, IconButton,
  InputAdornment, Menu, ListItemIcon, ListItemText, Button,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import ReplayIcon from '@mui/icons-material/Replay';
import SyncIcon from '@mui/icons-material/Sync';
import adminApi from '@/api/adminApi';
import { PAYMENT_STATUS_META } from '@/constants';
import { PAYMENT_METHOD_META, metaChip } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { formatCurrency, formatDateTime } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

const STATUS_OPTIONS = ['PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED'];
const METHOD_OPTIONS = Object.keys(PAYMENT_METHOD_META);

/**
 * Quản lý giao dịch thanh toán (docs/04 §10.7, ADM-09). Lọc theo trạng thái/phương thức; hoàn tiền
 * (giao dịch SUCCESS); đối soát giao dịch PENDING. Quyền PAYMENT_MANAGE — chỉ Admin.
 */
const PaymentsPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getPayments, { initialSort: 'createdAt,desc' });

  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [method, setMethod] = useState('');
  const [menuAnchor, setMenuAnchor] = useState(null);
  const [active, setActive] = useState(null);
  const [refundDialog, setRefundDialog] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const applyFilters = (next = {}) => {
    setParams({
      keyword: keyword.trim() || undefined,
      status: status || undefined,
      method: method || undefined,
      ...next,
    });
  };

  const openMenu = (e, row) => { setMenuAnchor(e.currentTarget); setActive(row); };

  const reconcile = async (row) => {
    setMenuAnchor(null);
    try {
      await adminApi.reconcilePayment(row.id);
      notify.success('Đã gửi yêu cầu đối soát');
      reload();
    } catch (err) {
      notify.apiError(err);
    }
  };

  const handleRefund = async ({ reason }) => {
    setSubmitting(true);
    try {
      await adminApi.refundPayment(active.id, { reason });
      notify.success('Đã hoàn tiền giao dịch');
      setRefundDialog(false);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'code', label: 'Mã GD', render: (r) => <Typography variant="body2" fontFamily="monospace">{r.transactionCode || r.code || `#${r.id}`}</Typography> },
    {
      key: 'user', label: 'Người mua', render: (r) => (
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="body2" noWrap>{r.userName || r.buyerName || '—'}</Typography>
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{r.packageName || ''}</Typography>
        </Box>
      ),
    },
    { key: 'amount', label: 'Số tiền', align: 'right', render: (r) => formatCurrency(r.amount) },
    { key: 'method', label: 'Phương thức', render: (r) => PAYMENT_METHOD_META[r.method] || r.method },
    {
      key: 'status', label: 'Trạng thái', render: (r) => {
        const m = metaChip(PAYMENT_STATUS_META, r.status);
        return <Chip size="small" label={m.label} color={m.color} />;
      },
    },
    { key: 'createdAt', label: 'Thời gian', render: (r) => formatDateTime(r.createdAt) },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        (r.status === 'SUCCESS' || r.status === 'PENDING') && (
          <IconButton size="small" onClick={(e) => openMenu(e, r)}><MoreVertIcon fontSize="small" /></IconButton>
        )
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader title="Quản lý thanh toán" subtitle={`${total} giao dịch`} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          size="small"
          placeholder="Tìm mã giao dịch, người mua"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && applyFilters()}
          sx={{ flexGrow: 1, minWidth: 220 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        />
        <TextField select size="small" label="Trạng thái" value={status} onChange={(e) => { setStatus(e.target.value); applyFilters({ status: e.target.value || undefined }); }} sx={{ minWidth: 160 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {STATUS_OPTIONS.map((s) => <MenuItem key={s} value={s}>{PAYMENT_STATUS_META[s].label}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Phương thức" value={method} onChange={(e) => { setMethod(e.target.value); applyFilters({ method: e.target.value || undefined }); }} sx={{ minWidth: 160 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {METHOD_OPTIONS.map((m) => <MenuItem key={m} value={m}>{PAYMENT_METHOD_META[m]}</MenuItem>)}
        </TextField>
        <Button variant="contained" onClick={() => applyFilters()}>Tìm</Button>
      </Stack>

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Không có giao dịch phù hợp"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={() => setMenuAnchor(null)}>
        {active?.status === 'SUCCESS' && (
          <MenuItem onClick={() => { setMenuAnchor(null); setRefundDialog(true); }}>
            <ListItemIcon><ReplayIcon fontSize="small" color="warning" /></ListItemIcon>
            <ListItemText>Hoàn tiền</ListItemText>
          </MenuItem>
        )}
        {active?.status === 'PENDING' && (
          <MenuItem onClick={() => reconcile(active)}>
            <ListItemIcon><SyncIcon fontSize="small" /></ListItemIcon>
            <ListItemText>Đối soát</ListItemText>
          </MenuItem>
        )}
      </Menu>

      <ConfirmDialog
        open={refundDialog}
        title="Hoàn tiền giao dịch"
        message={`Hoàn tiền giao dịch ${active?.transactionCode || `#${active?.id}`} (${formatCurrency(active?.amount)})?`}
        confirmText="Hoàn tiền"
        confirmColor="warning"
        requireReason
        reasonLabel="Lý do hoàn tiền"
        loading={submitting}
        onClose={() => setRefundDialog(false)}
        onConfirm={handleRefund}
      />
    </Box>
  );
};

export default PaymentsPage;
