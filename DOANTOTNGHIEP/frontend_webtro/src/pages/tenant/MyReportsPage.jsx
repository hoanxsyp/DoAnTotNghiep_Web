import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box, Card, CardContent, Stack, Chip, Typography, TextField, MenuItem, Pagination, Divider, Link,
} from '@mui/material';
import reportApi from '@/api/reportApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import notify from '@/utils/toast';
import { formatDateTime } from '@/utils/format';

const SIZE = 20;

const STATUS_META = {
  PENDING: { label: 'Chờ xử lý', color: 'warning' },
  REVIEWING: { label: 'Đang xem xét', color: 'info' },
  RESOLVED: { label: 'Đã xử lý', color: 'success' },
  REJECTED: { label: 'Đã từ chối', color: 'default' },
};
const SEVERITY_META = {
  LOW: { label: 'Nhẹ', color: 'default' },
  MEDIUM: { label: 'Trung bình', color: 'warning' },
  HIGH: { label: 'Cao', color: 'error' },
  CRITICAL: { label: 'Nghiêm trọng', color: 'error' },
};
const TARGET_LABEL = { LISTING: 'Tin đăng', COMMENT: 'Bình luận', USER: 'Người dùng', REVIEW: 'Đánh giá' };

const MyReportsPage = () => {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [status, setStatus] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size: SIZE, sort: 'createdAt,desc' };
      if (status) params.status = status;
      const data = await reportApi.getMyReports(params);
      setItems(data?.items || []);
      setTotalPages(data?.totalPages || 0);
    } catch (e) {
      notify.apiError(e, 'Không tải được báo cáo');
    } finally {
      setLoading(false);
    }
  }, [page, status]);

  useEffect(() => { load(); }, [load]);

  return (
    <Box>
      <PageHeader
        title="Báo cáo của tôi"
        subtitle="Theo dõi kết quả xử lý các báo cáo vi phạm bạn đã gửi"
        action={(
          <TextField select size="small" label="Trạng thái" value={status}
            onChange={(e) => { setPage(0); setStatus(e.target.value); }} sx={{ minWidth: 180 }}>
            <MenuItem value="">Tất cả</MenuItem>
            {Object.entries(STATUS_META).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}
          </TextField>
        )}
      />

      {loading ? (
        <Stack spacing={2}>{Array.from({ length: 4 }).map((_, i) => <Card key={i} sx={{ p: 2 }}><LoadingSkeleton variant="comment" /></Card>)}</Stack>
      ) : items.length === 0 ? (
        <EmptyState title="Bạn chưa gửi báo cáo nào" description="Khi phát hiện tin đăng hoặc nội dung vi phạm, hãy báo cáo để chúng tôi xử lý." />
      ) : (
        <Stack spacing={2}>
          {items.map((r) => {
            const st = STATUS_META[r.status] || { label: r.status, color: 'default' };
            const sev = SEVERITY_META[r.severity];
            return (
              <Card key={r.id}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1} sx={{ flexWrap: 'wrap' }}>
                    <Box sx={{ minWidth: 0 }}>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap', mb: 0.5 }}>
                        <Chip size="small" variant="outlined" label={TARGET_LABEL[r.targetType] || r.targetType} />
                        <Chip size="small" color="primary" variant="outlined" label={r.reasonLabel} />
                        {sev && <Chip size="small" color={sev.color} label={sev.label} />}
                      </Stack>
                      {r.targetUrl ? (
                        <Link component={RouterLink} to={r.targetUrl} variant="subtitle1" sx={{ fontWeight: 600 }}>
                          {r.targetTitle || `Đối tượng #${r.targetId}`}
                        </Link>
                      ) : (
                        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{r.targetTitle || `Đối tượng #${r.targetId}`}</Typography>
                      )}
                    </Box>
                    <Chip color={st.color} label={r.statusLabel || st.label} />
                  </Stack>

                  {r.description && (
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{r.description}</Typography>
                  )}

                  {r.resultLabel && (
                    <>
                      <Divider sx={{ my: 1.5 }} />
                      <Typography variant="body2"><b>Kết quả:</b> {r.resultLabel}</Typography>
                      {r.moderatorResponse && (
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                          Phản hồi: {r.moderatorResponse}
                        </Typography>
                      )}
                    </>
                  )}

                  <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 1.5 }}>
                    Gửi lúc {formatDateTime(r.createdAt)}{r.resolvedAt ? ` · Xử lý lúc ${formatDateTime(r.resolvedAt)}` : ''}
                  </Typography>
                </CardContent>
              </Card>
            );
          })}
        </Stack>
      )}

      {totalPages > 1 && (
        <Stack alignItems="center" sx={{ mt: 3 }}>
          <Pagination count={totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} color="primary" />
        </Stack>
      )}
    </Box>
  );
};

export default MyReportsPage;
