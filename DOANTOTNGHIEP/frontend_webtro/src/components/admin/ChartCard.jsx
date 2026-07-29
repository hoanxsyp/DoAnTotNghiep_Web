import { Card, CardHeader, CardContent, Box, Skeleton, Typography } from '@mui/material';
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

// Đăng ký một lần cho toàn app (react-chartjs-2 yêu cầu). Idempotent nên gọi ở module-level an toàn.
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
 * Khung thẻ biểu đồ cho Dashboard/Thống kê (docs/04 §10.1). Nhận biểu đồ đã dựng (react-chartjs-2)
 * qua `children`, hoặc hiển thị skeleton khi `loading`, thông điệp rỗng khi không có dữ liệu.
 * Không tự gọi API (luật F3).
 */
const ChartCard = ({ title, action, loading = false, empty = false, height = 300, children }) => (
  <Card variant="outlined" sx={{ height: '100%' }}>
    <CardHeader
      title={title}
      action={action}
      titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }}
    />
    <CardContent>
      <Box sx={{ height, position: 'relative' }}>
        {loading ? (
          <Skeleton variant="rounded" width="100%" height={height} />
        ) : empty ? (
          <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              Chưa có dữ liệu để hiển thị
            </Typography>
          </Box>
        ) : (
          children
        )}
      </Box>
    </CardContent>
  </Card>
);

export default ChartCard;
