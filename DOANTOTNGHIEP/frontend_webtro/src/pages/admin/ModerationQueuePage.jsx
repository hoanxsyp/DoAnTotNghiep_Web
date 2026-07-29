import { useState } from 'react';
import {
  Box, Tabs, Tab, Stack, Button, Typography, Link, Chip,
} from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import adminApi from '@/api/adminApi';
import { LISTING_STATUS, CATEGORY_CODES } from '@/constants';
import { REJECT_REASON_OPTIONS } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { formatPrice, formatArea, fromNow } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

/**
 * Hàng đợi kiểm duyệt (docs/04 §10 §7.4). Hai tab: tin PENDING (duyệt/từ chối) và tin NEED_REVIEW
 * (gỡ cờ/ẩn). Quyền LISTING_MODERATE. Dùng chung endpoint getModerationQueue lọc theo trạng thái.
 */
const ModerationQueuePage = () => {
  const [tab, setTab] = useState(LISTING_STATUS.PENDING);
  const {
    items, total, loading, error, page, size, setPage, setSize, reload,
  } = usePagedResource(
    (params) => adminApi.getModerationQueue({ ...params, status: tab }),
    { initialSort: 'createdAt,asc' },
  );

  const [active, setActive] = useState(null);
  const [dialog, setDialog] = useState(null); // reject | hide
  const [submitting, setSubmitting] = useState(false);

  const changeTab = (_, v) => { setTab(v); setPage(0); reload(); };

  const quick = async (fn, msg, row, payload) => {
    try {
      await fn(row.id, payload);
      notify.success(msg);
      reload();
    } catch (err) {
      notify.apiError(err);
    }
  };

  const withDialog = async (fn, msg, payload) => {
    setSubmitting(true);
    try {
      await fn(active.id, payload);
      notify.success(msg);
      setDialog(null);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const isPending = tab === LISTING_STATUS.PENDING;

  const columns = [
    {
      key: 'title', label: 'Tin đăng', render: (r) => (
        <Box sx={{ minWidth: 0, maxWidth: 340 }}>
          <Link href={`/admin/tin-dang/${r.id}`} underline="hover" color="inherit">
            <Typography variant="body2" fontWeight={600} noWrap>{r.title}</Typography>
          </Link>
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
            {r.categoryName || CATEGORY_CODES[r.categoryCode] || ''} · {r.ownerName || ''}
          </Typography>
        </Box>
      ),
    },
    { key: 'price', label: 'Giá', align: 'right', render: (r) => formatPrice(r.price) },
    { key: 'area', label: 'Diện tích', align: 'right', render: (r) => formatArea(r.area) },
    ...(isPending ? [] : [{
      key: 'reason', label: 'Lý do cần kiểm tra', render: (r) => (
        <Chip size="small" variant="outlined" color="warning" label={r.needReviewReason || r.autoHideReason || 'Cần kiểm tra'} />
      ),
    }]),
    { key: 'createdAt', label: 'Chờ từ', render: (r) => fromNow(r.submittedAt || r.createdAt) },
    {
      key: 'actions', label: 'Thao tác', align: 'right', render: (r) => (
        <Stack direction="row" spacing={0.5} justifyContent="flex-end">
          {isPending ? (
            <>
              <Button size="small" variant="contained" color="success" startIcon={<CheckIcon />} onClick={() => quick(adminApi.approveListing, 'Đã duyệt tin', r, {})}>Duyệt</Button>
              <Button size="small" variant="outlined" color="error" startIcon={<CloseIcon />} onClick={() => { setActive(r); setDialog('reject'); }}>Từ chối</Button>
            </>
          ) : (
            <>
              <Button size="small" variant="contained" color="success" startIcon={<CheckIcon />} onClick={() => quick(adminApi.clearNeedReview, 'Đã gỡ cờ, tin trở lại hiển thị', r, {})}>Gỡ cờ</Button>
              <Button size="small" variant="outlined" color="warning" startIcon={<VisibilityOffIcon />} onClick={() => { setActive(r); setDialog('hide'); }}>Ẩn tin</Button>
            </>
          )}
        </Stack>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader title="Hàng đợi kiểm duyệt" subtitle={`${total} tin trong hàng đợi`} />

      <Tabs value={tab} onChange={changeTab} sx={{ mb: 2 }}>
        <Tab value={LISTING_STATUS.PENDING} label="Chờ duyệt" />
        <Tab value={LISTING_STATUS.NEED_REVIEW} label="Cần kiểm tra" />
      </Tabs>

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText={isPending ? 'Không có tin nào chờ duyệt' : 'Không có tin nào cần kiểm tra'}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <ConfirmDialog
        open={dialog === 'reject'}
        title="Từ chối tin"
        message={`Từ chối tin "${active?.title}"?`}
        confirmText="Từ chối"
        confirmColor="error"
        requireReason
        reasonLabel="Lý do từ chối (gửi cho chủ trọ)"
        selectOptions={REJECT_REASON_OPTIONS}
        selectLabel="Nhóm lý do"
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={({ reason, selectValue }) => withDialog(adminApi.rejectListing, 'Đã từ chối tin', { reasonCode: selectValue, reason })}
      />
      <ConfirmDialog
        open={dialog === 'hide'}
        title="Ẩn tin"
        message={`Ẩn tin "${active?.title}" khỏi công khai?`}
        confirmText="Ẩn tin"
        confirmColor="warning"
        requireReason
        reasonLabel="Lý do ẩn"
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={({ reason }) => withDialog(adminApi.hideListing, 'Đã ẩn tin', { reason })}
      />
    </Box>
  );
};

export default ModerationQueuePage;
