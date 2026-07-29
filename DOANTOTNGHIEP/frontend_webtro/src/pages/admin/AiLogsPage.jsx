import { useState } from 'react';
import {
  Box, Tabs, Tab, Stack, TextField, MenuItem, Chip, Typography, Button, Tooltip,
} from '@mui/material';
import ReplayIcon from '@mui/icons-material/Replay';
import adminApi from '@/api/adminApi';
import { SENTIMENT_META } from '@/constants';
import { AI_MODULE_META, metaChip } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { formatDateTime, formatPrice, fromNow } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';

const MODULES = Object.keys(AI_MODULE_META);

const TABS = { LOGS: 'logs', ALERTS: 'alerts', PRICE: 'price' };

/**
 * Log & cảnh báo AI (docs/04 §10.10, AI-07). 3 tab: log gọi AI, cảnh báo sentiment tiêu cực, tin
 * lệch giá. Cho phép phân tích lại sentiment. Quyền AI_LOG_VIEW (Moderator cũng có).
 */
const AiLogsPage = () => {
  const [tab, setTab] = useState(TABS.LOGS);
  const [module, setModule] = useState('');

  const fetcher = (params) => {
    if (tab === TABS.ALERTS) return adminApi.getAiAlerts(params);
    if (tab === TABS.PRICE) return adminApi.getPriceDeviations(params);
    return adminApi.getAiLogs(params);
  };

  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource((params) => fetcher({ ...params, module: module || undefined }), {
    initialSort: 'createdAt,desc',
  });

  const changeTab = (_, v) => { setTab(v); setPage(0); reload(); };

  const reanalyze = async (row) => {
    try {
      await adminApi.reanalyzeSentiment({ commentId: row.commentId || row.targetId, listingId: row.listingId });
      notify.success('Đã gửi yêu cầu phân tích lại');
      reload();
    } catch (err) {
      notify.apiError(err);
    }
  };

  const logColumns = [
    { key: 'module', label: 'Module', render: (r) => <Chip size="small" variant="outlined" label={AI_MODULE_META[r.module] || r.module} /> },
    { key: 'summary', label: 'Nội dung', render: (r) => (
      <Typography variant="body2" sx={{ maxWidth: 420, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
        {r.summary || r.message || r.input || '—'}
      </Typography>
    ) },
    { key: 'status', label: 'Kết quả', render: (r) => r.status || r.result || '—' },
    { key: 'durationMs', label: 'Thời gian', align: 'right', render: (r) => (r.durationMs != null ? `${r.durationMs} ms` : '—') },
    { key: 'createdAt', label: 'Lúc', render: (r) => formatDateTime(r.createdAt) },
  ];

  const alertColumns = [
    { key: 'listing', label: 'Tin đăng', render: (r) => (
      <Box sx={{ minWidth: 0, maxWidth: 360 }}>
        <Typography variant="body2" fontWeight={600} noWrap>{r.listingTitle || `Tin #${r.listingId}`}</Typography>
        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{r.ownerName || ''}</Typography>
      </Box>
    ) },
    { key: 'sentiment', label: 'Cảm xúc', render: (r) => {
      const m = metaChip(SENTIMENT_META, r.sentimentLabel || 'NEGATIVE');
      return <Chip size="small" label={m.label} color={m.color} variant="outlined" />;
    } },
    { key: 'negativeRatio', label: 'Tỷ lệ tiêu cực', align: 'right', render: (r) => (r.negativeRatio != null ? `${Math.round(r.negativeRatio * 100)}%` : '—') },
    { key: 'negativeCount', label: 'Số BL tiêu cực', align: 'center', render: (r) => r.negativeCount ?? '—' },
    { key: 'createdAt', label: 'Phát hiện', render: (r) => fromNow(r.createdAt || r.flaggedAt) },
    { key: 'actions', label: '', align: 'right', render: (r) => (
      <Tooltip title="Phân tích lại"><Button size="small" startIcon={<ReplayIcon />} onClick={() => reanalyze(r)}>Phân tích lại</Button></Tooltip>
    ) },
  ];

  const priceColumns = [
    { key: 'listing', label: 'Tin đăng', render: (r) => (
      <Box sx={{ minWidth: 0, maxWidth: 360 }}>
        <Typography variant="body2" fontWeight={600} noWrap>{r.listingTitle || `Tin #${r.listingId}`}</Typography>
        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{r.categoryName || ''} · {r.wardName || r.districtName || ''}</Typography>
      </Box>
    ) },
    { key: 'price', label: 'Giá nhập', align: 'right', render: (r) => formatPrice(r.price || r.inputPrice) },
    { key: 'estimated', label: 'Giá đề xuất', align: 'right', render: (r) => formatPrice(r.estimatedPrice || r.suggestedPrice) },
    { key: 'deviation', label: 'Độ lệch', align: 'right', render: (r) => (
      <Chip size="small" color="warning" label={r.deviationRatio != null ? `${r.deviationRatio > 0 ? '+' : ''}${Math.round(r.deviationRatio * 100)}%` : '—'} />
    ) },
    { key: 'createdAt', label: 'Lúc', render: (r) => formatDateTime(r.createdAt) },
  ];

  const columnsByTab = { [TABS.LOGS]: logColumns, [TABS.ALERTS]: alertColumns, [TABS.PRICE]: priceColumns };
  const emptyByTab = {
    [TABS.LOGS]: 'Chưa có log AI nào',
    [TABS.ALERTS]: 'Không có cảnh báo cảm xúc',
    [TABS.PRICE]: 'Không có tin lệch giá',
  };

  return (
    <Box>
      <AdminPageHeader title="Log & cảnh báo AI" subtitle={`${total} bản ghi`} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} justifyContent="space-between" sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={changeTab}>
          <Tab value={TABS.LOGS} label="Log gọi AI" />
          <Tab value={TABS.ALERTS} label="Cảnh báo cảm xúc" />
          <Tab value={TABS.PRICE} label="Tin lệch giá" />
        </Tabs>
        {tab === TABS.LOGS && (
          <TextField select size="small" label="Module" value={module} onChange={(e) => { setModule(e.target.value); setParams({ module: e.target.value || undefined }); }} sx={{ minWidth: 200 }}>
            <MenuItem value="">Tất cả module</MenuItem>
            {MODULES.map((m) => <MenuItem key={m} value={m}>{AI_MODULE_META[m]}</MenuItem>)}
          </TextField>
        )}
      </Stack>

      <AdminDataTable
        columns={columnsByTab[tab]}
        rows={items}
        rowKey={(r) => r.id ?? `${r.listingId}-${r.createdAt}`}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText={emptyByTab[tab]}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />
    </Box>
  );
};

export default AiLogsPage;
