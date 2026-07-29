import { useState } from 'react';
import {
  Box, Stack, TextField, MenuItem, Typography, IconButton, InputAdornment,
  Menu, ListItemIcon, ListItemText, Button, Link,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import VisibilityIcon from '@mui/icons-material/Visibility';
import LockIcon from '@mui/icons-material/Lock';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import adminApi from '@/api/adminApi';
import { LISTING_STATUS, CATEGORY_CODES } from '@/constants';
import { REJECT_REASON_OPTIONS, SEVERITY_OPTIONS } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { formatPrice, formatArea, formatDateTime } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';
import StatusChip from '@/components/common/StatusChip';

const STATUS_LIST = Object.values(LISTING_STATUS);

/**
 * Quản lý tin đăng (docs/04 §10.4, API 4.14.x). Xem mọi tin (LISTING_VIEW_ANY), sửa trạng thái
 * (LISTING_MODERATE), khóa/mở khóa (LISTING_LOCK). Lọc theo trạng thái + từ khóa + loại.
 */
const ListingsPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getListings, { initialSort: 'createdAt,desc' });

  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [category, setCategory] = useState('');
  const [menuAnchor, setMenuAnchor] = useState(null);
  const [active, setActive] = useState(null);
  const [dialog, setDialog] = useState(null); // reject | hide | lock | flag
  const [submitting, setSubmitting] = useState(false);

  const applyFilters = (next = {}) => {
    setParams({
      keyword: keyword.trim() || undefined,
      status: status || undefined,
      category: category || undefined,
      ...next,
    });
  };

  const openMenu = (e, row) => { setMenuAnchor(e.currentTarget); setActive(row); };

  const quick = async (fn, msg, payload) => {
    setMenuAnchor(null);
    try {
      await fn(active.id, payload);
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

  const columns = [
    {
      key: 'title', label: 'Tin đăng', render: (r) => (
        <Box sx={{ minWidth: 0, maxWidth: 320 }}>
          <Link href={`/admin/tin-dang/${r.id}`} underline="hover" color="inherit">
            <Typography variant="body2" fontWeight={600} noWrap>{r.title}</Typography>
          </Link>
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
            {r.categoryName || CATEGORY_CODES[r.categoryCode] || ''} · {r.provinceName || ''}
          </Typography>
        </Box>
      ),
    },
    { key: 'ownerName', label: 'Chủ trọ', render: (r) => r.ownerName || '—' },
    { key: 'price', label: 'Giá', align: 'right', render: (r) => formatPrice(r.price) },
    { key: 'area', label: 'Diện tích', align: 'right', render: (r) => formatArea(r.area) },
    { key: 'status', label: 'Trạng thái', render: (r) => <StatusChip status={r.status} /> },
    { key: 'createdAt', label: 'Ngày đăng', render: (r) => formatDateTime(r.createdAt, 'DD/MM/YYYY') },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <IconButton size="small" onClick={(e) => openMenu(e, r)}><MoreVertIcon fontSize="small" /></IconButton>
      ),
    },
  ];

  const s = active?.status;

  return (
    <Box>
      <AdminPageHeader title="Quản lý tin đăng" subtitle={`${total} tin`} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          size="small"
          placeholder="Tìm theo tiêu đề, mã tin"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && applyFilters()}
          sx={{ flexGrow: 1, minWidth: 220 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        />
        <TextField select size="small" label="Trạng thái" value={status} onChange={(e) => { setStatus(e.target.value); applyFilters({ status: e.target.value || undefined }); }} sx={{ minWidth: 170 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {STATUS_LIST.map((st) => <MenuItem key={st} value={st}><StatusChip status={st} /></MenuItem>)}
        </TextField>
        <TextField select size="small" label="Loại tin" value={category} onChange={(e) => { setCategory(e.target.value); applyFilters({ category: e.target.value || undefined }); }} sx={{ minWidth: 170 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {Object.entries(CATEGORY_CODES).map(([code, label]) => <MenuItem key={code} value={code}>{label}</MenuItem>)}
        </TextField>
        <Button variant="contained" onClick={() => applyFilters()}>Tìm</Button>
      </Stack>

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Không có tin phù hợp"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={() => setMenuAnchor(null)}>
        {s === LISTING_STATUS.PENDING && (
          <MenuItem onClick={() => quick(adminApi.approveListing, 'Đã duyệt tin', {})}>
            <ListItemIcon><CheckIcon fontSize="small" color="success" /></ListItemIcon>
            <ListItemText>Duyệt tin</ListItemText>
          </MenuItem>
        )}
        {s === LISTING_STATUS.PENDING && (
          <MenuItem onClick={() => { setMenuAnchor(null); setDialog('reject'); }}>
            <ListItemIcon><CloseIcon fontSize="small" color="error" /></ListItemIcon>
            <ListItemText>Từ chối tin</ListItemText>
          </MenuItem>
        )}
        {(s === LISTING_STATUS.ACTIVE || s === LISTING_STATUS.NEED_REVIEW) && (
          <MenuItem onClick={() => { setMenuAnchor(null); setDialog('hide'); }}>
            <ListItemIcon><VisibilityOffIcon fontSize="small" /></ListItemIcon>
            <ListItemText>Ẩn tin</ListItemText>
          </MenuItem>
        )}
        {s === LISTING_STATUS.HIDDEN && (
          <MenuItem onClick={() => quick(adminApi.unhideListing, 'Đã hiện lại tin', {})}>
            <ListItemIcon><VisibilityIcon fontSize="small" color="success" /></ListItemIcon>
            <ListItemText>Hiện lại tin</ListItemText>
          </MenuItem>
        )}
        {s === LISTING_STATUS.ACTIVE && (
          <MenuItem onClick={() => quick(adminApi.flagNeedReview, 'Đã gắn cờ cần kiểm tra', {})}>
            <ListItemIcon><VisibilityIcon fontSize="small" color="warning" /></ListItemIcon>
            <ListItemText>Gắn cờ cần kiểm tra</ListItemText>
          </MenuItem>
        )}
        {s === LISTING_STATUS.NEED_REVIEW && (
          <MenuItem onClick={() => quick(adminApi.clearNeedReview, 'Đã gỡ cờ cần kiểm tra', {})}>
            <ListItemIcon><CheckIcon fontSize="small" color="success" /></ListItemIcon>
            <ListItemText>Gỡ cờ cần kiểm tra</ListItemText>
          </MenuItem>
        )}
        {s === LISTING_STATUS.LOCKED ? (
          <MenuItem onClick={() => quick(adminApi.unlockListing, 'Đã mở khóa tin', undefined)}>
            <ListItemIcon><LockOpenIcon fontSize="small" /></ListItemIcon>
            <ListItemText>Mở khóa tin</ListItemText>
          </MenuItem>
        ) : (
          <MenuItem onClick={() => { setMenuAnchor(null); setDialog('lock'); }}>
            <ListItemIcon><LockIcon fontSize="small" color="error" /></ListItemIcon>
            <ListItemText>Khóa tin</ListItemText>
          </MenuItem>
        )}
      </Menu>

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
        message={`Tạm ẩn tin "${active?.title}" khỏi công khai?`}
        confirmText="Ẩn tin"
        confirmColor="warning"
        requireReason
        reasonLabel="Lý do ẩn"
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={({ reason }) => withDialog(adminApi.hideListing, 'Đã ẩn tin', { reason })}
      />
      <ConfirmDialog
        open={dialog === 'lock'}
        title="Khóa tin"
        message={`Khóa tin "${active?.title}"? Chủ trọ không thao tác được cho tới khi mở khóa.`}
        confirmText="Khóa tin"
        confirmColor="error"
        requireReason
        reasonLabel="Lý do khóa"
        selectOptions={SEVERITY_OPTIONS}
        selectLabel="Mức độ vi phạm"
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={({ reason, selectValue }) => withDialog(adminApi.lockListing, 'Đã khóa tin', { reason, severity: selectValue })}
      />
    </Box>
  );
};

export default ListingsPage;
