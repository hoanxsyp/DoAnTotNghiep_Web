import { useMemo } from 'react';
import { Card, CardContent, CardHeader, Box, Button, Skeleton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js';
import { Line, Bar, Doughnut } from 'react-chartjs-2';
import EmptyState from '@/components/common/EmptyState';

// Đăng ký 1 lần cho toàn app.
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
  Filler,
);

/**
 * Bọc react-chartjs-2 (docs/04 mục 6 #17, canonical 1.2).
 *
 * - Màu lấy từ theme.palette qua useTheme() -> tự đúng ở dark theme (KHÔNG hex cứng).
 * - maintainAspectRatio: false. Tooltip + legend tiếng Việt.
 * - prefers-reduced-motion -> tắt animation.
 *
 * Props: title, subtitle, type 'line'|'bar'|'doughnut', data, options, loading, error,
 *        onRetry, height (num=280), emptyState (node), actions (node)
 */
const CHART_MAP = { line: Line, bar: Bar, doughnut: Doughnut };

const prefersReducedMotion =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export default function ChartCard({
  title,
  subtitle,
  type = 'line',
  data,
  options,
  loading = false,
  error,
  onRetry,
  height = 280,
  emptyState,
  actions,
}) {
  const theme = useTheme();
  const ChartComp = CHART_MAP[type] || Line;

  const mergedOptions = useMemo(() => {
    const textColor = theme.palette.text.secondary;
    const gridColor = theme.palette.divider;
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: prefersReducedMotion ? false : undefined,
      plugins: {
        legend: {
          display: type === 'doughnut',
          position: type === 'doughnut' ? 'bottom' : 'top',
          labels: { color: textColor, font: { family: theme.typography.fontFamily } },
        },
        tooltip: {
          bodyFont: { family: theme.typography.fontFamily },
          titleFont: { family: theme.typography.fontFamily },
        },
      },
      scales:
        type === 'doughnut'
          ? undefined
          : {
              x: { ticks: { color: textColor }, grid: { color: gridColor } },
              y: { ticks: { color: textColor }, grid: { color: gridColor } },
            },
      ...options,
    };
  }, [theme, type, options]);

  // Áp màu theme cho dataset chưa khai báo màu.
  const themedData = useMemo(() => {
    if (!data) return data;
    const palette = [
      theme.palette.primary.main,
      theme.palette.secondary.main,
      theme.palette.success.main,
      theme.palette.info.main,
      theme.palette.warning.main,
      theme.palette.error.main,
    ];
    return {
      ...data,
      datasets: (data.datasets || []).map((ds, i) => {
        const base = palette[i % palette.length];
        if (type === 'doughnut') {
          return {
            backgroundColor: ds.backgroundColor || palette,
            borderColor: ds.borderColor || theme.palette.background.paper,
            ...ds,
          };
        }
        return {
          backgroundColor: ds.backgroundColor || (type === 'bar' ? base : `${base}33`),
          borderColor: ds.borderColor || base,
          pointBackgroundColor: ds.pointBackgroundColor || base,
          tension: ds.tension ?? 0.3,
          fill: ds.fill ?? type === 'line',
          ...ds,
        };
      }),
    };
  }, [data, theme, type]);

  const hasData =
    themedData && themedData.datasets && themedData.datasets.some((d) => (d.data || []).length > 0);

  const renderBody = () => {
    if (loading) return <Skeleton variant="rectangular" height={height} sx={{ borderRadius: 1 }} />;
    if (error) {
      return (
        <EmptyState
          size="sm"
          title="Không tải được biểu đồ"
          description={error?.message}
          action={
            onRetry ? (
              <Button variant="contained" size="small" onClick={onRetry}>
                Thử lại
              </Button>
            ) : undefined
          }
        />
      );
    }
    if (!hasData) return emptyState || <EmptyState size="sm" title="Chưa có dữ liệu để hiển thị" />;
    return (
      <Box sx={{ height, position: 'relative' }}>
        <ChartComp data={themedData} options={mergedOptions} />
      </Box>
    );
  };

  return (
    <Card sx={{ height: '100%' }}>
      {(title || actions) && (
        <CardHeader
          title={title}
          subheader={subtitle}
          action={actions}
          titleTypographyProps={{ variant: 'h6' }}
          subheaderTypographyProps={{ variant: 'caption' }}
        />
      )}
      <CardContent>{renderBody()}</CardContent>
    </Card>
  );
}
