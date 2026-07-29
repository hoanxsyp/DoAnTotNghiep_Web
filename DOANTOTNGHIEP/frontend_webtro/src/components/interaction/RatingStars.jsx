import { Rating, Stack, Typography, Box } from '@mui/material';
import StarIcon from '@mui/icons-material/Star';

/**
 * Sao đánh giá 1–5 (docs/04 mục 6 #12, [§3.12]).
 *
 * - readOnly -> hiển thị "4,2/5 (12 đánh giá)".
 * - Editable -> aria-label từng sao ("1 sao"...) cho screen reader.
 *
 * Props: value (num), onChange (fn), readOnly (bool), size 'small'|'medium'|'large',
 *        showValue (bool), count (num — số đánh giá), precision (num)
 */
const formatVi = (n) => String(n).replace('.', ',');

export default function RatingStars({
  value = 0,
  onChange,
  readOnly = false,
  size = 'medium',
  showValue = true,
  count,
  precision = readOnly ? 0.1 : 1,
}) {
  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <Rating
        value={Number(value) || 0}
        onChange={(_, v) => onChange?.(v)}
        readOnly={readOnly}
        precision={precision}
        size={size}
        emptyIcon={<StarIcon style={{ opacity: 0.35 }} fontSize="inherit" />}
        getLabelText={(v) => `${v} sao`}
      />

      {showValue && (
        <Box component="span">
          {readOnly ? (
            <Typography variant="body2" color="text.secondary" component="span">
              <b>{formatVi(Number(value || 0).toFixed(1))}</b>/5
              {typeof count === 'number' && ` (${count} đánh giá)`}
            </Typography>
          ) : (
            value > 0 && (
              <Typography variant="body2" color="text.secondary" component="span">
                {value}/5
              </Typography>
            )
          )}
        </Box>
      )}
    </Stack>
  );
}
