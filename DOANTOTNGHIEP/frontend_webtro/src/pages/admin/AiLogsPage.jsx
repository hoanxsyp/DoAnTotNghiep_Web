import { useState } from 'react';
import {
  Box, Tabs, Tab, Stack, TextField, MenuItem, Chip, Typography,
} from '@mui/material';
import adminApi from '@/api/adminApi';
import { AI_MODULE_META } from '@/config/adminMeta';
import { formatDateTime, formatPrice } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';

const MODULES = Object.keys(AI_MODULE_META);

const TABS = { LOGS: 'logs', PRICE: 'price' };

/**
 * Log & cảnh báo AI (docs/04 §10.10, AI-07). 2 tab: log gọi AI, tin lệch giá. Quyền AI_LOG_VIEW
 * (Moderator cũng có).
 */
const AiLogsPage = () => {
  const [tab, setTab] = useState(TABS.LOGS);
  const [module, setModule] = useState('');

  const fetcher = (params) => {
    if (tab === TABS.PRICE) return adminApi.getPriceDeviations(params);
    return adminApi.getAiLogs(params);
  };

  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource((params) => fetcher({ ...params, module: module || undefined }), {
    initialSort: 'createdAt,desc',
  });

  const changeTab = (_, v) => { setTab(v); setPage(0); reload(); };

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

  const columnsByTab = { [TABS.LOGS]: logColumns, [TABS.PRICE]: priceColumns };
  const emptyByTab = {
    [TABS.LOGS]: 'Chưa có log AI nào',
    [TABS.PRICE]: 'Không có tin lệch giá',
  };

  return (
    <Box>
      <AdminPageHeader title="Log & cảnh báo AI" subtitle={`${total} bản ghi`} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} justifyContent="space-between" sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={changeTab}>
          <Tab value={TABS.LOGS} label="Log gọi AI" />
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
