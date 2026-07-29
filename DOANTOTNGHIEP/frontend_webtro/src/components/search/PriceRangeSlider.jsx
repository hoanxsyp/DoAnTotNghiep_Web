import { useEffect, useRef, useState } from 'react';
import { Box, Slider, TextField, Stack, Typography, InputAdornment } from '@mui/material';

/**
 * Slider 2 đầu + 2 ô nhập tay (docs/04 mục 6 #4). Dùng lại cho cả GIÁ và DIỆN TÍCH.
 *
 * - Slider không thể đảo from > to [§3.7].
 * - Ô nhập tay: tự hoán đổi khi blur nếu from > to.
 * - Hiển thị định dạng vi-VN, parse về number khi gửi.
 * - Debounce 400ms trước onChange.
 *
 * Props: min, max, value ([from,to]), onChange (fn), step, marks (arr), showInputs (bool),
 *        unit ('đ'|'m²'), label
 */
export default function PriceRangeSlider({
  min = 0,
  max = 20000000,
  value,
  onChange,
  step = 500000,
  marks,
  showInputs = true,
  unit = 'đ',
  label,
}) {
  const controlled = Array.isArray(value) ? value : [min, max];
  const [local, setLocal] = useState(controlled);
  const timer = useRef(null);

  // Đồng bộ khi prop value đổi từ ngoài (reset lọc...).
  useEffect(() => {
    if (Array.isArray(value)) setLocal(value);
  }, [value?.[0], value?.[1]]); // eslint-disable-line react-hooks/exhaustive-deps

  const emit = (next) => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => onChange?.(next), 400);
  };

  const handleSlider = (_, next) => {
    setLocal(next);
    emit(next);
  };

  const handleInput = (idx) => (e) => {
    const raw = e.target.value.replace(/[^\d]/g, '');
    const num = raw === '' ? (idx === 0 ? min : max) : Number(raw);
    const next = idx === 0 ? [num, local[1]] : [local[0], num];
    setLocal(next);
  };

  const handleBlur = () => {
    let [from, to] = local;
    from = Math.max(min, Math.min(from, max));
    to = Math.max(min, Math.min(to, max));
    if (from > to) [from, to] = [to, from]; // tự hoán đổi
    const next = [from, to];
    setLocal(next);
    emit(next);
  };

  const fmt = (n) => Number(n).toLocaleString('vi-VN');

  return (
    <Box>
      {label && (
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          {label}
        </Typography>
      )}
      <Box sx={{ px: 1 }}>
        <Slider
          value={local}
          onChange={handleSlider}
          min={min}
          max={max}
          step={step}
          marks={marks}
          disableSwap
          valueLabelDisplay="auto"
          valueLabelFormat={(v) => `${fmt(v)} ${unit}`}
          getAriaLabel={(i) => (i === 0 ? 'Giá trị từ' : 'Giá trị đến')}
        />
      </Box>

      {showInputs && (
        <Stack direction="row" spacing={1} alignItems="center">
          <TextField
            label="Từ"
            value={fmt(local[0])}
            onChange={handleInput(0)}
            onBlur={handleBlur}
            inputProps={{ inputMode: 'numeric' }}
            InputProps={{ endAdornment: <InputAdornment position="end">{unit}</InputAdornment> }}
          />
          <Typography color="text.secondary">–</Typography>
          <TextField
            label="Đến"
            value={fmt(local[1])}
            onChange={handleInput(1)}
            onBlur={handleBlur}
            inputProps={{ inputMode: 'numeric' }}
            InputProps={{ endAdornment: <InputAdornment position="end">{unit}</InputAdornment> }}
          />
        </Stack>
      )}
    </Box>
  );
}
