import { useState } from 'react';
import {
  Box, Stack, TextField, MenuItem, Chip, Typography, Button,
  ToggleButtonGroup, ToggleButton,
} from '@mui/material';
import GavelIcon from '@mui/icons-material/Gavel';
import adminApi from '@/api/adminApi';
import { REPORT_REASONS } from '@/constants';
import {
  REPORT_STATUS_META, REPORT_SEVERITY_META, REPORT_TARGET_META,
  MODERATION_RESULT_OPTIONS, metaChip,
} from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { fromNow } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

const STATUS_OPTIONS = ['PENDING', 'REVIEWING', 'RESOLVED', 'REJECTED'];
const SEVERITY_FILTER = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/**
 * Quản lý báo cáo (docs/04 §10.8, API 4.16). Xem phẳng hoặc gom nhóm theo đối tượng (groupBy=TARGET)
 * để xử lý một lần cho nhiều report cùng target. Kết quả xử lý theo ModerationResult. Quyền
 * REPORT_RESOLVE (khóa tin cần thêm LISTING_LOCK — backend kiểm tra lại).
 */
const ReportsPage = () => {
  const [groupBy, setGroupBy] = useState('NONE');
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getReports, { initialParams: { groupBy: 'NONE' } });

  const [status, setStatus] = useState('');
  const [severity, setSeverity] = useState('');
  const [active, setActive] = useState(null);
  const [dialog, setDialog] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const applyFilters = (next = {}) => {
    setParams({
      groupBy,
      status: status || undefined,
      severity: severity || undefined,
      ...next,
    });
  };

  const changeGroup = (_, v) => {
    if (!v) return;
    setGroupBy(v);
    setPage(0);
    setParams({ groupBy: v, status: status || undefined, severity: severity || undefined });
  };

  const handleResolve = async ({ reason, selectValue }) => {
    setSubmitting(true);
    try {
      if (groupBy === 'TARGET') {
        await adminApi.resolveReportGroup({
          targetType: active.targetType, targetId: active.targetId,
          result: selectValue, note: reason,
        });
      } else {
        await adminApi.resolveReport(active.id, { result: selectValue, note: reason });
      }
      notify.success('Đã xử lý báo cáo');
      setDialog(false);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const flatColumns = [
    {
      key: 'target', label: 'Đối tượng', render: (r) => (
        <Box sx={{ minWidth: 0, maxWidth: 300 }}>
          <Typography variant="body2" fontWeight={600} noWrap>{r.targetTitle || `${REPORT_TARGET_META[r.targetType]} #${r.targetId}`}</Typography>
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
            {REPORT_TARGET_META[r.targetType]} · {r.targetOwnerName || ''}
          </Typography>
        </Box>
      ),
    },
    { key: 'reason', label: 'Lý do', render: (r) => r.reasonLabel || REPORT_REASONS[r.reason] || r.reason },
    {
      key: 'severity', label: 'Mức độ', render: (r) => {
        const m = metaChip(REPORT_SEVERITY_META, r.severity);
        return <Chip size="small" label={m.label} color={m.color} />;
      },
    },
    { key: 'related', label: 'Liên quan', align: 'center', render: (r) => r.relatedReportCount ?? 0 },
    {
      key: 'status', label: 'Trạng thái', render: (r) => {
        const m = metaChip(REPORT_STATUS_META, r.status);
        return <Chip size="small" label={r.statusLabel || m.label} color={m.color} />;
      },
    },
    { key: 'createdAt', label: 'Thời gian', render: (r) => fromNow(r.createdAt) },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        (r.status === 'PENDING' || r.status === 'REVIEWING') && (
          <Button size="small" variant="outlined" startIcon={<GavelIcon />} onClick={() => { setActive(r); setDialog(true); }}>Xử lý</Button>
        )
      ),
    },
  ];

  const groupColumns = [
    {
      key: 'target', label: 'Đối tượng', render: (r) => (
        <Box sx={{ minWidth: 0, maxWidth: 300 }}>
          <Typography variant="body2" fontWeight={600} noWrap>{r.targetTitle || `${REPORT_TARGET_META[r.targetType]} #${r.targetId}`}</Typography>
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{r.targetOwnerName || ''}</Typography>
        </Box>
      ),
    },
    { key: 'reportCount', label: 'Số báo cáo', align: 'center', render: (r) => r.reportCount },
    { key: 'distinct', label: 'Người báo cáo', align: 'center', render: (r) => r.distinctReporterCount },
    {
      key: 'maxSeverity', label: 'Mức cao nhất', render: (r) => {
        const m = metaChip(REPORT_SEVERITY_META, r.maxSeverity);
        return <Chip size="small" label={m.label} color={m.color} />;
      },
    },
    {
      key: 'reasons', label: 'Theo lý do', render: (r) => (
        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
          {Object.entries(r.reportsByReason || {}).map(([reason, count]) => (
            <Chip key={reason} size="small" variant="outlined" label={`${REPORT_REASONS[reason] || reason}: ${count}`} />
          ))}
        </Stack>
      ),
    },
    { key: 'last', label: 'Gần nhất', render: (r) => fromNow(r.lastReportedAt) },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <Button size="small" variant="contained" startIcon={<GavelIcon />} onClick={() => { setActive(r); setDialog(true); }}>Xử lý nhóm</Button>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader
        title="Quản lý báo cáo"
        subtitle={`${total} ${groupBy === 'TARGET' ? 'nhóm' : 'báo cáo'}`}
        actions={
          <ToggleButtonGroup size="small" exclusive value={groupBy} onChange={changeGroup}>
            <ToggleButton value="NONE">Danh sách</ToggleButton>
            <ToggleButton value="TARGET">Gom nhóm</ToggleButton>
          </ToggleButtonGroup>
        }
      />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField select size="small" label="Trạng thái" value={status} onChange={(e) => { setStatus(e.target.value); applyFilters({ status: e.target.value || undefined }); }} sx={{ minWidth: 180 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {STATUS_OPTIONS.map((s) => <MenuItem key={s} value={s}>{REPORT_STATUS_META[s].label}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Mức độ" value={severity} onChange={(e) => { setSeverity(e.target.value); applyFilters({ severity: e.target.value || undefined }); }} sx={{ minWidth: 180 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {SEVERITY_FILTER.map((s) => <MenuItem key={s} value={s}>{REPORT_SEVERITY_META[s].label}</MenuItem>)}
        </TextField>
      </Stack>

      <AdminDataTable
        columns={groupBy === 'TARGET' ? groupColumns : flatColumns}
        rows={items}
        rowKey={groupBy === 'TARGET' ? (r) => `${r.targetType}-${r.targetId}` : (r) => r.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Không có báo cáo phù hợp"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <ConfirmDialog
        open={dialog}
        title={groupBy === 'TARGET' ? 'Xử lý cả nhóm báo cáo' : 'Xử lý báo cáo'}
        message={
          groupBy === 'TARGET'
            ? `Áp dụng một quyết định cho ${active?.reportCount || 0} báo cáo về "${active?.targetTitle || ''}".`
            : `Xử lý báo cáo về "${active?.targetTitle || ''}".`
        }
        confirmText="Xử lý"
        confirmColor="primary"
        requireReason
        reasonLabel="Ghi chú xử lý (nội bộ)"
        reasonRequired={false}
        selectOptions={MODERATION_RESULT_OPTIONS}
        selectLabel="Kết quả xử lý"
        loading={submitting}
        onClose={() => setDialog(false)}
        onConfirm={handleResolve}
      />
    </Box>
  );
};

export default ReportsPage;
