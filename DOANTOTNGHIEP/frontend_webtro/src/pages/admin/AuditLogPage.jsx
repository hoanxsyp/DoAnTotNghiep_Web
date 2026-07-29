import { useState } from 'react';
import {
  Box, Stack, TextField, MenuItem, Chip, Typography, IconButton, Dialog,
  DialogTitle, DialogContent, DialogActions, Button, Table, TableBody,
  TableRow, TableCell, Divider,
} from '@mui/material';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import dayjs from 'dayjs';
import adminApi from '@/api/adminApi';
import { AUDIT_ACTION_META } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { formatDateTime } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';

const ACTIONS = Object.keys(AUDIT_ACTION_META);
const TARGET_TYPES = ['USER', 'LISTING', 'PAYMENT', 'CONFIG', 'PACKAGE'];

/**
 * Nhật ký kiểm toán (docs/04 §10, API 4.20.3, canonical §11.4). Chỉ đọc. Lọc theo hành động, loại
 * đối tượng, khoảng ngày (≤ 90 ngày). Xem chi tiết thay đổi + IP/UA/requestId. Quyền AUDIT_LOG_VIEW.
 */
const AuditLogPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getAuditLogs, {
    initialParams: {
      from: dayjs().subtract(30, 'day').format('YYYY-MM-DD'),
      to: dayjs().format('YYYY-MM-DD'),
    },
    initialSort: 'createdAt,desc',
  });

  const [action, setAction] = useState('');
  const [targetType, setTargetType] = useState('');
  const [from, setFrom] = useState(dayjs().subtract(30, 'day').format('YYYY-MM-DD'));
  const [to, setTo] = useState(dayjs().format('YYYY-MM-DD'));
  const [detail, setDetail] = useState(null);

  const applyFilters = () => {
    if (dayjs(to).diff(dayjs(from), 'day') > 90) {
      notify.warning('Khoảng thời gian tối đa 90 ngày');
      return;
    }
    setParams({
      action: action || undefined,
      targetType: targetType || undefined,
      from,
      to,
    });
  };

  const columns = [
    {
      key: 'action', label: 'Hành động', render: (r) => (
        <Chip size="small" variant="outlined" label={r.actionLabel || AUDIT_ACTION_META[r.action] || r.action} />
      ),
    },
    {
      key: 'actor', label: 'Người thực hiện', render: (r) => (
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="body2" noWrap>{r.actorName || 'Hệ thống'}</Typography>
          <Typography variant="caption" color="text.secondary">{(r.actorRoles || []).join(', ')}</Typography>
        </Box>
      ),
    },
    {
      key: 'target', label: 'Đối tượng', render: (r) => (
        <Box sx={{ minWidth: 0, maxWidth: 280 }}>
          <Typography variant="body2" noWrap>{r.targetLabel || `${r.targetType} #${r.targetId ?? ''}`}</Typography>
          <Typography variant="caption" color="text.secondary">{r.targetType}</Typography>
        </Box>
      ),
    },
    { key: 'createdAt', label: 'Thời gian', render: (r) => formatDateTime(r.createdAt) },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <IconButton size="small" onClick={() => setDetail(r)}><InfoOutlinedIcon fontSize="small" /></IconButton>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader title="Nhật ký kiểm toán" subtitle={`${total} bản ghi (chỉ đọc)`} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
        <TextField select size="small" label="Hành động" value={action} onChange={(e) => setAction(e.target.value)} sx={{ minWidth: 180 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {ACTIONS.map((a) => <MenuItem key={a} value={a}>{AUDIT_ACTION_META[a]}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Loại đối tượng" value={targetType} onChange={(e) => setTargetType(e.target.value)} sx={{ minWidth: 150 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {TARGET_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
        </TextField>
        <TextField size="small" type="date" label="Từ ngày" InputLabelProps={{ shrink: true }} value={from} onChange={(e) => setFrom(e.target.value)} />
        <TextField size="small" type="date" label="Đến ngày" InputLabelProps={{ shrink: true }} value={to} onChange={(e) => setTo(e.target.value)} />
        <Button variant="contained" onClick={applyFilters}>Lọc</Button>
      </Stack>

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Không có bản ghi nào"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Dialog open={Boolean(detail)} onClose={() => setDetail(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{detail?.actionLabel || AUDIT_ACTION_META[detail?.action] || detail?.action}</DialogTitle>
        <DialogContent dividers>
          <Table size="small">
            <TableBody>
              <TableRow><TableCell sx={{ fontWeight: 600, width: 140 }}>Người thực hiện</TableCell><TableCell>{detail?.actorName} ({(detail?.actorRoles || []).join(', ')})</TableCell></TableRow>
              <TableRow><TableCell sx={{ fontWeight: 600 }}>Đối tượng</TableCell><TableCell>{detail?.targetLabel} · {detail?.targetType} #{detail?.targetId ?? ''}</TableCell></TableRow>
              <TableRow><TableCell sx={{ fontWeight: 600 }}>Thời gian</TableCell><TableCell>{formatDateTime(detail?.createdAt)}</TableCell></TableRow>
              <TableRow><TableCell sx={{ fontWeight: 600 }}>IP</TableCell><TableCell>{detail?.ipAddress || '—'}</TableCell></TableRow>
              <TableRow><TableCell sx={{ fontWeight: 600 }}>Request ID</TableCell><TableCell><Typography variant="caption" fontFamily="monospace">{detail?.requestId || '—'}</Typography></TableCell></TableRow>
            </TableBody>
          </Table>

          {detail?.reason && (
            <>
              <Divider sx={{ my: 2 }} />
              <Typography variant="subtitle2" gutterBottom>Lý do</Typography>
              <Typography variant="body2" color="text.secondary">{detail.reason}</Typography>
            </>
          )}

          {Array.isArray(detail?.changes) && detail.changes.length > 0 && (
            <>
              <Divider sx={{ my: 2 }} />
              <Typography variant="subtitle2" gutterBottom>Thay đổi</Typography>
              <Table size="small">
                <TableBody>
                  {detail.changes.map((c, i) => (
                    <TableRow key={i}>
                      <TableCell sx={{ fontWeight: 600, width: 140 }}>{c.field}</TableCell>
                      <TableCell>
                        <Typography variant="body2" component="span" color="error.main" sx={{ textDecoration: 'line-through', mr: 1 }}>{String(c.oldValue ?? '∅')}</Typography>
                        <Typography variant="body2" component="span" color="success.main">{String(c.newValue ?? '∅')}</Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetail(null)}>Đóng</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default AuditLogPage;
