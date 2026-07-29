import { Card, CardContent, Box, Typography, Stack, Skeleton, Avatar } from '@mui/material';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';

/**
 * Thẻ số liệu (docs/04 mục 6 #16).
 *
 * - delta > 0 -> ▲ xanh; < 0 -> ▼ đỏ.
 * - onClick -> con trỏ pointer + role="button" + focus ring.
 * - severity='error' -> viền đỏ (VD "7 báo cáo chờ xử lý").
 * - Giá trị format vi-VN.
 *
 * Props: label, value, icon (node), color (str palette), delta (num), deltaLabel,
 *        loading (bool), onClick (fn), severity 'error'|..., hint
 */
const fmt = (v) => (typeof v === 'number' ? v.toLocaleString('vi-VN') : v);

export default function StatCard({
  label,
  value,
  icon,
  color = 'primary',
  delta,
  deltaLabel,
  loading = false,
  onClick,
  severity,
  hint,
}) {
  const clickable = !!onClick;
  const deltaUp = typeof delta === 'number' && delta > 0;
  const deltaDown = typeof delta === 'number' && delta < 0;

  return (
    <Card
      onClick={onClick}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
      onKeyDown={
        clickable
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClick();
              }
            }
          : undefined
      }
      sx={{
        height: '100%',
        ...(severity === 'error' && { borderColor: 'error.main' }),
        ...(clickable && {
          cursor: 'pointer',
          transition: 'box-shadow .2s',
          '&:hover': { boxShadow: 3 },
          '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main' },
        }),
      }}
    >
      <CardContent>
        <Stack direction="row" spacing={2} alignItems="center">
          {icon && (
            <Avatar
              variant="rounded"
              sx={{
                bgcolor: `${color}.main`,
                color: `${color}.contrastText`,
                width: 48,
                height: 48,
              }}
            >
              {icon}
            </Avatar>
          )}
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="body2" color="text.secondary" noWrap>
              {label}
            </Typography>
            {loading ? (
              <Skeleton width="60%" height={34} />
            ) : (
              <Typography variant="h5" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                {fmt(value)}
              </Typography>
            )}

            {typeof delta === 'number' && !loading && (
              <Stack direction="row" spacing={0.25} alignItems="center" sx={{ mt: 0.25 }}>
                {deltaUp && <ArrowUpwardIcon sx={{ fontSize: 16, color: 'success.main' }} />}
                {deltaDown && <ArrowDownwardIcon sx={{ fontSize: 16, color: 'error.main' }} />}
                <Typography
                  variant="caption"
                  sx={{ color: deltaUp ? 'success.main' : deltaDown ? 'error.main' : 'text.secondary' }}
                >
                  {Math.abs(delta)}%{deltaLabel ? ` ${deltaLabel}` : ''}
                </Typography>
              </Stack>
            )}

            {hint && !loading && (
              <Typography variant="caption" color="text.secondary">
                {hint}
              </Typography>
            )}
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}
