import { Chip, Alert, Tooltip } from '@mui/material';
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import ReportGmailerrorredIcon from '@mui/icons-material/ReportGmailerrorred';

/**
 * Nhãn điểm uy tín (docs/04 mục 6 #10, [§5.8][§3.8]).
 *
 * NHẬN `level` TỪ API — KHÔNG tự so ngưỡng (không hardcode trust.threshold.* — docs/04 5.1.3).
 * Backend trả trustLevel: 'NORMAL' | 'RISKY' | 'NEED_REVIEW'.
 *
 * Props:
 * - score (num): điểm 0..100
 * - level: 'NORMAL' | 'RISKY' | 'NEED_REVIEW'
 * - variant: 'badge' (chip số + nhãn) | 'inline' (chip nhỏ) | 'alert' (Alert cảnh báo nhẹ)
 * - showWarning (bool): với variant badge/inline, chỉ hiện khi level != NORMAL
 */
const LEVEL_META = {
  NORMAL: { label: 'Uy tín tốt', color: 'success', chipColor: 'success', icon: VerifiedUserOutlinedIcon },
  RISKY: { label: 'Cần thận trọng', color: 'warning', chipColor: 'warning', icon: ShieldOutlinedIcon },
  NEED_REVIEW: { label: 'Uy tín thấp', color: 'error', chipColor: 'error', icon: ReportGmailerrorredIcon },
};

const WARNING_TEXT =
  'Tin này có điểm uy tín thấp. Hãy kiểm tra kỹ thông tin và không chuyển tiền trước khi xem phòng.';

export default function TrustScoreBadge({
  score,
  level = 'NORMAL',
  variant = 'badge',
  showWarning = false,
  size = 'small',
}) {
  const meta = LEVEL_META[level] || LEVEL_META.NORMAL;
  const Icon = meta.icon;
  const isLow = level !== 'NORMAL';

  if (variant === 'alert') {
    // Cảnh báo nhẹ [§3.8]: RISKY -> warning, NEED_REVIEW -> error.
    if (!isLow) return null;
    return (
      <Alert severity={level === 'NEED_REVIEW' ? 'error' : 'warning'} variant="outlined">
        {WARNING_TEXT}
      </Alert>
    );
  }

  // Với biến thể badge/inline: nếu chỉ muốn hiện khi rủi ro thì bỏ qua khi NORMAL.
  if (showWarning && !isLow) return null;

  const scoreText = typeof score === 'number' ? `${score}` : '—';
  const chipLabel = variant === 'inline' ? meta.label : `${scoreText} · ${meta.label}`;

  return (
    <Tooltip title={isLow ? WARNING_TEXT : `Điểm uy tín: ${scoreText}/100`}>
      <Chip
        size={size}
        color={meta.chipColor}
        variant={isLow ? 'filled' : 'outlined'}
        icon={<Icon />}
        label={chipLabel}
        sx={{ fontWeight: 600 }}
      />
    </Tooltip>
  );
}
