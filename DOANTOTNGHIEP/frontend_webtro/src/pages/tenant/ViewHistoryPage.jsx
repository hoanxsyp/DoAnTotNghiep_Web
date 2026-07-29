import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box, Card, Stack, Button, Pagination, List, ListItem, ListItemAvatar, ListItemText,
  Avatar, IconButton, Tooltip, Typography, Divider, Chip,
} from '@mui/material';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import historyApi from '@/api/historyApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import StatusChip from '@/components/common/StatusChip';
import notify from '@/utils/toast';
import { formatPrice, formatArea, formatDateTime } from '@/utils/format';

const SIZE = 20;

const ViewHistoryPage = () => {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [confirmClear, setConfirmClear] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await historyApi.getViews({ page, size: SIZE, sort: 'viewedAt,desc' });
      setItems(data?.items || []);
      setTotalPages(data?.totalPages || 0);
      setTotal(data?.totalElements || 0);
    } catch (e) {
      notify.apiError(e, 'Không tải được lịch sử xem');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => { load(); }, [load]);

  const handleDelete = async (id) => {
    try {
      await historyApi.deleteView(id);
      setItems((prev) => prev.filter((i) => i.id !== id));
      setTotal((n) => Math.max(0, n - 1));
    } catch (e) {
      notify.apiError(e, 'Xóa mục lịch sử thất bại');
    }
  };

  const handleClearAll = async () => {
    setConfirmClear(false);
    try {
      await historyApi.clearViews();
      notify.success('Đã xóa toàn bộ lịch sử xem');
      setPage(0);
      load();
    } catch (e) {
      notify.apiError(e, 'Xóa lịch sử thất bại');
    }
  };

  return (
    <Box>
      <PageHeader
        title="Lịch sử xem"
        subtitle={total ? `${total} tin bạn đã xem gần đây` : 'Các tin bạn đã xem sẽ hiện ở đây'}
        action={items.length > 0 && (
          <Button color="error" variant="outlined" startIcon={<DeleteSweepIcon />} onClick={() => setConfirmClear(true)}>
            Xóa tất cả
          </Button>
        )}
      />

      {loading ? (
        <Card sx={{ p: 2 }}><LoadingSkeleton variant="list-item" count={6} /></Card>
      ) : items.length === 0 ? (
        <EmptyState title="Chưa có lịch sử xem" description="Hãy khám phá các tin đăng để xây dựng gợi ý phù hợp hơn cho bạn." />
      ) : (
        <>
          <Card>
            <List disablePadding>
              {items.map((it, idx) => (
                <Box key={it.id}>
                  <ListItem
                    secondaryAction={(
                      <Tooltip title="Xóa khỏi lịch sử">
                        <IconButton edge="end" onClick={() => handleDelete(it.id)}>
                          <DeleteOutlineIcon />
                        </IconButton>
                      </Tooltip>
                    )}
                  >
                    <ListItemAvatar>
                      <Avatar variant="rounded" src={it.thumbnailUrl} sx={{ width: 64, height: 48, mr: 1 }} />
                    </ListItemAvatar>
                    <ListItemText
                      primary={(
                        <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap' }}>
                          <Typography
                            component={RouterLink}
                            to={`/tin/${it.slug ? `${it.slug}-` : ''}${it.listingId}`}
                            variant="subtitle2"
                            sx={{ fontWeight: 600, color: 'text.primary', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
                          >
                            {it.title}
                          </Typography>
                          {it.status && <StatusChip status={it.status} />}
                          {it.notAvailable && <Chip size="small" label="Không còn hiển thị" color="default" />}
                        </Stack>
                      )}
                      secondary={(
                        <Stack direction="row" spacing={1.5} sx={{ mt: 0.5, flexWrap: 'wrap' }} component="span">
                          <Typography variant="caption" color="primary" sx={{ fontWeight: 600 }}>{formatPrice(it.price)}</Typography>
                          <Typography variant="caption" color="text.secondary">{formatArea(it.area)}</Typography>
                          <Typography variant="caption" color="text.secondary">{it.shortAddress}</Typography>
                          <Typography variant="caption" color="text.disabled">· Xem lúc {formatDateTime(it.viewedAt)}</Typography>
                        </Stack>
                      )}
                    />
                  </ListItem>
                  {idx < items.length - 1 && <Divider component="li" />}
                </Box>
              ))}
            </List>
          </Card>
          {totalPages > 1 && (
            <Stack alignItems="center" sx={{ mt: 3 }}>
              <Pagination count={totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} color="primary" />
            </Stack>
          )}
        </>
      )}

      <ConfirmDialog
        open={confirmClear}
        title="Xóa toàn bộ lịch sử xem"
        message="Toàn bộ lịch sử xem sẽ bị xóa và không thể khôi phục. Tiếp tục?"
        confirmText="Xóa tất cả"
        severity="error"
        onConfirm={handleClearAll}
        onCancel={() => setConfirmClear(false)}
      />
    </Box>
  );
};

export default ViewHistoryPage;
