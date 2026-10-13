import { useState } from 'react';
import {
  Box, Stack, TextField, MenuItem, Chip, Typography, IconButton,
  InputAdornment, Menu, ListItemIcon, ListItemText, Button,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import VisibilityIcon from '@mui/icons-material/Visibility';
import ReportGmailerrorredIcon from '@mui/icons-material/ReportGmailerrorred';
import adminApi from '@/api/adminApi';
import { COMMENT_STATUS_META, metaChip } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { fromNow } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

const STATUS_OPTIONS = ['VISIBLE', 'PENDING', 'HIDDEN', 'DELETED'];

/**
 * Kiểm duyệt bình luận (docs/04 §10.9, API 4.x). Ẩn/hiện, đánh dấu spam. Quyền COMMENT_MODERATE.
 * Lọc theo trạng thái + từ khóa.
 */
const CommentsPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getComments, { initialSort: 'createdAt,desc' });

  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [menuAnchor, setMenuAnchor] = useState(null);
  const [active, setActive] = useState(null);
  const [dialog, setDialog] = useState(null); // hide | spam
  const [submitting, setSubmitting] = useState(false);

  const applyFilters = (next = {}) => {
    setParams({
      keyword: keyword.trim() || undefined,
      status: status || undefined,
      ...next,
    });
  };

  const openMenu = (e, row) => { setMenuAnchor(e.currentTarget); setActive(row); };

  const quick = async (fn, msg, row) => {
    setMenuAnchor(null);
    try {
      await fn(row.id);
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
      key: 'content', label: 'Bình luận', render: (r) => (
        <Box sx={{ minWidth: 0, maxWidth: 400 }}>
          <Typography variant="body2" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {r.content}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {r.authorName} · trên tin {r.listingTitle ? `"${r.listingTitle}"` : `#${r.listingId}`}
          </Typography>
        </Box>
      ),
    },
    {
      key: 'status', label: 'Trạng thái', render: (r) => {
        const m = metaChip(COMMENT_STATUS_META, r.status);
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
      <AdminPageHeader title="Kiểm duyệt bình luận" subtitle={`${total} bình luận`} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          size="small"
          placeholder="Tìm nội dung bình luận"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && applyFilters()}
          sx={{ flexGrow: 1, minWidth: 220 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        />
        <TextField select size="small" label="Trạng thái" value={status} onChange={(e) => { setStatus(e.target.value); applyFilters({ status: e.target.value || undefined }); }} sx={{ minWidth: 160 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {STATUS_OPTIONS.map((s) => <MenuItem key={s} value={s}>{COMMENT_STATUS_META[s].label}</MenuItem>)}
        </TextField>
        <Button variant="contained" onClick={() => applyFilters()}>Tìm</Button>
      </Stack>

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Không có bình luận phù hợp"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={() => setMenuAnchor(null)}>
        {active?.status === 'HIDDEN' ? (
          <MenuItem onClick={() => quick(adminApi.unhideComment, 'Đã hiện lại bình luận', active)}>
            <ListItemIcon><VisibilityIcon fontSize="small" color="success" /></ListItemIcon>
            <ListItemText>Hiện lại</ListItemText>
          </MenuItem>
        ) : (
          <MenuItem onClick={() => { setMenuAnchor(null); setDialog('hide'); }}>
            <ListItemIcon><VisibilityOffIcon fontSize="small" /></ListItemIcon>
            <ListItemText>Ẩn bình luận</ListItemText>
          </MenuItem>
        )}
        <MenuItem onClick={() => { setMenuAnchor(null); setDialog('spam'); }}>
          <ListItemIcon><ReportGmailerrorredIcon fontSize="small" color="error" /></ListItemIcon>
          <ListItemText>Đánh dấu spam</ListItemText>
        </MenuItem>
      </Menu>

      <ConfirmDialog
        open={dialog === 'hide'}
        title="Ẩn bình luận"
        message="Ẩn bình luận này khỏi trang tin?"
        confirmText="Ẩn"
        confirmColor="warning"
        requireReason
        reasonLabel="Lý do ẩn"
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={({ reason }) => withDialog(adminApi.hideComment, 'Đã ẩn bình luận', { reason })}
      />
      <ConfirmDialog
        open={dialog === 'spam'}
        title="Đánh dấu spam"
        message="Đánh dấu bình luận này là spam?"
        confirmText="Đánh dấu spam"
        confirmColor="error"
        requireReason
        reasonLabel="Ghi chú"
        reasonRequired={false}
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={({ reason }) => withDialog(adminApi.markCommentSpam, 'Đã đánh dấu spam', { reason })}
      />
    </Box>
  );
};

export default CommentsPage;
