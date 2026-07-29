import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box, Card, CardContent, Stack, Avatar, Typography, Chip, IconButton, Tooltip, Pagination,
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Link,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import reviewApi from '@/api/reviewApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import RatingStars from '@/components/interaction/RatingStars';
import StatusChip from '@/components/common/StatusChip';
import notify from '@/utils/toast';
import { formatDateTime } from '@/utils/format';

const SIZE = 20;

const MyReviewsPage = () => {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [editing, setEditing] = useState(null);
  const [editForm, setEditForm] = useState({ rating: 5, content: '' });
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await reviewApi.getMyReviews({ page, size: SIZE, sort: 'createdAt,desc' });
      setItems(data?.items || []);
      setTotalPages(data?.totalPages || 0);
    } catch (e) {
      notify.apiError(e, 'Không tải được đánh giá');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => { load(); }, [load]);

  const openEdit = (r) => {
    setEditing(r);
    setEditForm({ rating: r.rating, content: r.content || '' });
  };

  const saveEdit = async () => {
    if (!editForm.rating) { notify.warning('Vui lòng chọn số sao'); return; }
    if (!editForm.content.trim()) { notify.warning('Vui lòng nhập nội dung đánh giá'); return; }
    setSaving(true);
    try {
      await reviewApi.update(editing.id, { rating: editForm.rating, content: editForm.content.trim() });
      notify.success('Đã cập nhật đánh giá');
      setItems((prev) => prev.map((i) => (i.id === editing.id ? { ...i, ...editForm, content: editForm.content.trim() } : i)));
      setEditing(null);
    } catch (e) {
      notify.apiError(e, 'Cập nhật đánh giá thất bại');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    const r = deleting;
    setDeleting(null);
    if (!r) return;
    try {
      await reviewApi.remove(r.id);
      notify.success('Đã xóa đánh giá');
      setItems((prev) => prev.filter((i) => i.id !== r.id));
    } catch (e) {
      notify.apiError(e, 'Xóa đánh giá thất bại');
    }
  };

  return (
    <Box>
      <PageHeader title="Đánh giá của tôi" subtitle="Các đánh giá bạn đã gửi về tin đăng / chủ trọ" />

      {loading ? (
        <Stack spacing={2}>{Array.from({ length: 3 }).map((_, i) => <Card key={i} sx={{ p: 2 }}><LoadingSkeleton variant="comment" /></Card>)}</Stack>
      ) : items.length === 0 ? (
        <EmptyState title="Bạn chưa có đánh giá nào" description="Sau khi liên hệ và trải nghiệm, hãy để lại đánh giá giúp người thuê khác." />
      ) : (
        <Stack spacing={2}>
          {items.map((r) => (
            <Card key={r.id}>
              <CardContent>
                <Stack direction="row" spacing={2}>
                  <Avatar variant="rounded" src={r.listingThumbnailUrl} sx={{ width: 72, height: 56 }} />
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                      <Box sx={{ minWidth: 0 }}>
                        <Link component={RouterLink} to={`/tin/${r.listingId}`} variant="subtitle1" sx={{ fontWeight: 600 }}>
                          {r.listingTitle}
                        </Link>
                        <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 0.5, flexWrap: 'wrap' }}>
                          <RatingStars value={r.rating} readOnly showValue={false} size="small" />
                          {r.listingStatus && <StatusChip status={r.listingStatus} />}
                          {r.status === 'HIDDEN' && <Chip size="small" color="error" label="Đã bị ẩn" />}
                        </Stack>
                      </Box>
                      <Stack direction="row" spacing={0.5}>
                        {r.editable && (
                          <Tooltip title="Sửa đánh giá">
                            <IconButton size="small" onClick={() => openEdit(r)}><EditIcon fontSize="small" /></IconButton>
                          </Tooltip>
                        )}
                        <Tooltip title="Xóa đánh giá">
                          <IconButton size="small" color="error" onClick={() => setDeleting(r)}><DeleteOutlineIcon fontSize="small" /></IconButton>
                        </Tooltip>
                      </Stack>
                    </Stack>
                    <Typography variant="body2" sx={{ mt: 1 }}>{r.content}</Typography>
                    {r.moderationReason && (
                      <Typography variant="caption" color="error" sx={{ display: 'block', mt: 0.5 }}>
                        Lý do ẩn: {r.moderationReason}
                      </Typography>
                    )}
                    <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 1 }}>
                      {formatDateTime(r.createdAt)}
                      {r.editable && r.editableUntil ? ` · Có thể sửa đến ${formatDateTime(r.editableUntil)}` : ''}
                    </Typography>
                  </Box>
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}

      {totalPages > 1 && (
        <Stack alignItems="center" sx={{ mt: 3 }}>
          <Pagination count={totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} color="primary" />
        </Stack>
      )}

      <Dialog open={!!editing} onClose={() => !saving && setEditing(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Sửa đánh giá</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <RatingStars value={editForm.rating} onChange={(v) => setEditForm((f) => ({ ...f, rating: v || 0 }))} showValue={false} />
            <TextField
              label="Nội dung" multiline minRows={3} fullWidth
              value={editForm.content}
              onChange={(e) => setEditForm((f) => ({ ...f, content: e.target.value }))}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setEditing(null)} disabled={saving} color="inherit">Hủy</Button>
          <Button onClick={saveEdit} variant="contained" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu'}</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={!!deleting}
        title="Xóa đánh giá"
        message="Bạn chắc chắn muốn xóa đánh giá này?"
        confirmText="Xóa"
        severity="error"
        onConfirm={handleDelete}
        onCancel={() => setDeleting(null)}
      />
    </Box>
  );
};

export default MyReviewsPage;
