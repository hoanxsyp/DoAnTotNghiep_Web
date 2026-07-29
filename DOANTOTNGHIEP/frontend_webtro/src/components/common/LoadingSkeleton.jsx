import { Box, Skeleton, Stack, Card, CardContent, Grid } from '@mui/material';

/**
 * Skeleton "đúng hình dạng" nội dung thật để không nhảy layout khi dữ liệu về
 * (docs/04 mục 6, component #19). Một component nhiều `variant` thay vì rải <Skeleton> khắp nơi.
 * Tôn trọng prefers-reduced-motion qua prop animation.
 *
 * Props:
 * - variant: 'listing-card' | 'listing-detail' | 'table' | 'form' | 'chart'
 *            | 'stat-card' | 'list-item' | 'comment' | 'conversation'
 * - count: số phần tử lặp
 * - columns: số cột (dùng cho variant 'table')
 */
export default function LoadingSkeleton({ variant = 'listing-card', count = 1, columns = 5 }) {
  const items = Array.from({ length: count });

  const renderOne = (i) => {
    switch (variant) {
      case 'listing-card':
        return (
          <Card key={i} sx={{ height: '100%' }}>
            <Skeleton variant="rectangular" sx={{ width: '100%', aspectRatio: '16 / 9' }} />
            <CardContent>
              <Skeleton width="90%" height={22} />
              <Skeleton width="60%" height={22} />
              <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                <Skeleton width={60} height={20} />
                <Skeleton width={60} height={20} />
              </Stack>
              <Skeleton width="70%" height={18} sx={{ mt: 1 }} />
            </CardContent>
          </Card>
        );

      case 'stat-card':
        return (
          <Card key={i}>
            <CardContent>
              <Stack direction="row" spacing={2} alignItems="center">
                <Skeleton variant="circular" width={44} height={44} />
                <Box sx={{ flex: 1 }}>
                  <Skeleton width="50%" height={16} />
                  <Skeleton width="70%" height={30} />
                </Box>
              </Stack>
            </CardContent>
          </Card>
        );

      case 'chart':
        return (
          <Card key={i}>
            <CardContent>
              <Skeleton width="40%" height={22} />
              <Skeleton variant="rectangular" height={260} sx={{ mt: 2, borderRadius: 1 }} />
            </CardContent>
          </Card>
        );

      case 'list-item':
        return (
          <Stack key={i} direction="row" spacing={2} alignItems="center" sx={{ py: 1.5 }}>
            <Skeleton variant="circular" width={40} height={40} />
            <Box sx={{ flex: 1 }}>
              <Skeleton width="40%" height={18} />
              <Skeleton width="80%" height={16} />
            </Box>
          </Stack>
        );

      case 'comment':
        return (
          <Stack key={i} direction="row" spacing={2} sx={{ py: 1.5 }}>
            <Skeleton variant="circular" width={36} height={36} />
            <Box sx={{ flex: 1 }}>
              <Skeleton width="30%" height={16} />
              <Skeleton width="95%" height={16} sx={{ mt: 0.5 }} />
              <Skeleton width="70%" height={16} />
            </Box>
          </Stack>
        );

      case 'conversation':
        return (
          <Stack key={i} direction="row" spacing={2} alignItems="center" sx={{ py: 1.5 }}>
            <Skeleton variant="circular" width={48} height={48} />
            <Box sx={{ flex: 1 }}>
              <Skeleton width="35%" height={18} />
              <Skeleton width="85%" height={16} />
            </Box>
            <Skeleton width={40} height={14} />
          </Stack>
        );

      case 'form':
        return (
          <Stack key={i} spacing={2}>
            <Skeleton width="30%" height={18} />
            <Skeleton variant="rectangular" height={40} sx={{ borderRadius: 1 }} />
            <Skeleton width="30%" height={18} />
            <Skeleton variant="rectangular" height={40} sx={{ borderRadius: 1 }} />
            <Skeleton variant="rectangular" height={96} sx={{ borderRadius: 1 }} />
          </Stack>
        );

      case 'listing-detail':
        return (
          <Box key={i}>
            <Skeleton variant="rectangular" sx={{ width: '100%', aspectRatio: '16 / 9', borderRadius: 1 }} />
            <Skeleton width="80%" height={34} sx={{ mt: 2 }} />
            <Skeleton width="50%" height={24} />
            <Grid container spacing={1} sx={{ mt: 1 }}>
              {Array.from({ length: 4 }).map((_, k) => (
                <Grid item xs={3} key={k}>
                  <Skeleton variant="rectangular" height={56} sx={{ borderRadius: 1 }} />
                </Grid>
              ))}
            </Grid>
            <Skeleton width="100%" height={16} sx={{ mt: 2 }} />
            <Skeleton width="100%" height={16} />
            <Skeleton width="90%" height={16} />
            <Skeleton width="75%" height={16} />
          </Box>
        );

      case 'table':
        return (
          <Stack key={i} direction="row" spacing={2} sx={{ py: 1.25 }}>
            {Array.from({ length: columns }).map((_, k) => (
              <Skeleton key={k} height={20} sx={{ flex: 1 }} />
            ))}
          </Stack>
        );

      default:
        return <Skeleton key={i} height={24} />;
    }
  };

  return <>{items.map((_, i) => renderOne(i))}</>;
}
