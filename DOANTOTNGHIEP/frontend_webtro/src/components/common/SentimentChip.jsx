import { Chip, Tooltip } from '@mui/material';
import SentimentSatisfiedAltIcon from '@mui/icons-material/SentimentSatisfiedAlt';
import SentimentNeutralIcon from '@mui/icons-material/SentimentNeutral';
import SentimentVeryDissatisfiedIcon from '@mui/icons-material/SentimentVeryDissatisfied';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { SENTIMENT_META } from '@/constants';

/**
 * Chip cảm xúc (docs/04 mục 6, component #11). Map SentimentLabel -> {icon, màu, nhãn}.
 * Luôn có icon VÀ text (mục 1.3 — không chỉ dựa vào màu).
 * confidence < 0.5 -> thêm ⚠ + tooltip "Độ tin cậy thấp" [§9.1].
 *
 * Props: label (SentimentLabel), score (num), confidence (num 0..1), showScore (bool)
 */
const ICONS = {
  POSITIVE: SentimentSatisfiedAltIcon,
  NEUTRAL: SentimentNeutralIcon,
  NEGATIVE: SentimentVeryDissatisfiedIcon,
  MIXED: CompareArrowsIcon,
  PENDING_ANALYSIS: HourglassEmptyIcon,
};

export default function SentimentChip({ label, score, confidence, showScore = false, size = 'small' }) {
  if (!label) return null;

  const meta = SENTIMENT_META[label] || { label, color: 'default' };
  const lowConfidence = typeof confidence === 'number' && confidence < 0.5;
  const Icon = lowConfidence ? WarningAmberIcon : ICONS[label] || SentimentNeutralIcon;

  let text = meta.label;
  if (showScore && typeof score === 'number') {
    text = `${meta.label} (${score.toFixed(1)})`;
  }

  const chip = (
    <Chip
      size={size}
      color={meta.color}
      variant={meta.color === 'default' ? 'outlined' : 'filled'}
      icon={<Icon />}
      label={text}
    />
  );

  if (lowConfidence) {
    return (
      <Tooltip title="Độ tin cậy thấp — kết quả phân tích chỉ mang tính tham khảo">
        <span>{chip}</span>
      </Tooltip>
    );
  }

  return chip;
}
