import { useState } from 'react';
import {
  Box, Stack, TextField, MenuItem, Chip, Typography, IconButton, Rating,
  InputAdornment, Menu, ListItemIcon, ListItemText, Button,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import VisibilityIcon from '@mui/icons-material/Visibility';
import adminApi from '@/api/adminApi';
import { REVIEW_STATUS_META, metaChip } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { fromNow } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

const STATUS_OPTIONS = ['VISIBLE', 'HIDDEN', 'DELETED'];

/**
 * Kiểm duyệt đánh giá (docs/04 §10.9). Ẩn đánh giá vi phạm / hiện lại. Quyền REVIEW_MODERATE.
 */
const ReviewsPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getReviews, { initialSort: 'createdAt,desc' });

  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [menuAnchor, setMenuAnchor] = useState(null);
  const [active, setActive] = useState(null);
  const [dialog, setDialog] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const applyFilters = (next = {}) => {
    setParams({ keyword: keyword.trim() || undefined, status: status || undefined, ...next });
  };

  const openMenu = (e, row) => { setMenuAnchor(e.currentTarget); setActive(row); };

  const unhide = async (row) => {
    setMenuAnchor(null);
    try {
      await adminApi.unhideReview(row.id);
      notify.success('Đã hiện lại đánh giá');
      reload();
    } catch (err) {
      notify.apiError(err);
    }
  };

  const handleHide = async ({ reason }) => {
    setSubmitting(true);
    try {
      await adminApi.hideReview(active.id, { reason });
      notify.success('Đã ẩn đánh giá');
      setDialog(false);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      key: 'content', label: 'Đánh giá', render: (r) => (
        <Box sx={{ minWidth: 0, maxWidth: 400 }}>
          <Rating value={Number(r.rating) || 0} precision={0.5} size="small" readOnly />
          <Typography variant="body2" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {r.content}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {r.authorName} · {r.landlordName ? `chủ trọ ${r.landlordName}` : `tin #${r.listingId}`}
          </Typography>
        </Box>
      ),
    },
    {
      key: 'status', label: 'Trạng thái', render: (r) => {
        const m = metaChip(REVIEW_STATUS_META, r.status);
        return <Chip size="small" label={m.label} color={m.color} />;
      },
    },
    { key: 'createdAt', label: 'Thời gian', render: (r) => fromNow(r.createdAt) },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <IconButton size="small" onClick={(e) => openMenu(e, r)}><MoreVertIcon fontSize="small" /></IconButton>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader title="Quản lý đánh giá" subtitle={`${total} đánh giá`} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          size="small"
          placeholder="Tìm nội dung đánh giá"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && applyFilters()}
          sx={{ flexGrow: 1, minWidth: 220 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        />
        <TextField select size="small" label="Trạng thái" value={status} onChange={(e) => { setStatus(e.target.value); applyFilters({ status: e.target.value || undefined }); }} sx={{ minWidth: 160 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {STATUS_OPTIONS.map((s) => <MenuItem key={s} value={s}>{REVIEW_STATUS_META[s].label}</MenuItem>)}
        </TextField>
        <Button variant="contained" onClick={() => applyFilters()}>Tìm</Button>
      </Stack>

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Không có đánh giá phù hợp"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={() => setMenuAnchor(null)}>
        {active?.status === 'HIDDEN' ? (
          <MenuItem onClick={() => unhide(active)}>
            <ListItemIcon><VisibilityIcon fontSize="small" color="success" /></ListItemIcon>
            <ListItemText>Hiện lại</ListItemText>
          </MenuItem>
        ) : (
          <MenuItem onClick={() => { setMenuAnchor(null); setDialog(true); }}>
            <ListItemIcon><VisibilityOffIcon fontSize="small" color="error" /></ListItemIcon>
            <ListItemText>Ẩn đánh giá</ListItemText>
          </MenuItem>
        )}
      </Menu>

      <ConfirmDialog
        open={dialog}
        title="Ẩn đánh giá"
        message="Ẩn đánh giá vi phạm khỏi hồ sơ chủ trọ / tin đăng?"
        confirmText="Ẩn"
        confirmColor="error"
        requireReason
        reasonLabel="Lý do ẩn"
        loading={submitting}
        onClose={() => setDialog(false)}
        onConfirm={handleHide}
      />
    </Box>
  );
};

export default ReviewsPage;
