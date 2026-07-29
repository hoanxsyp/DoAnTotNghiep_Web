import { useEffect, useState } from 'react';
import {
  Box,
  Stack,
  Typography,
  Divider,
  Button,
  ToggleButton,
  ToggleButtonGroup,
  MenuItem,
  TextField,
  FormControlLabel,
  Checkbox,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import AddressSelector from '@/components/filter/AddressSelector';
import AmenityPicker from '@/components/filter/AmenityPicker';
import PriceRangeSlider from '@/components/search/PriceRangeSlider';
import { CATEGORY_CODES, GENDER_REQUIREMENT, FURNITURE_STATUS } from '@/constants';

/**
 * Bộ lọc đầy đủ 13 tiêu chí [§3.7] (docs/04 mục 6 #3, màn 5.1.2).
 *
 * - mode='instant' (sidebar desktop): mỗi thay đổi gọi onChange ngay.
 * - mode='draft' (drawer mobile): giữ state nháp, chỉ onApply khi bấm "Xem N kết quả".
 * - Nhóm "Ở ghép" chỉ hiện khi categoryCode === ROOMMATE.
 * - Bọc AddressSelector, PriceRangeSlider, AmenityPicker.
 *
 * Props: value (obj filter), onChange (fn), onApply (fn), onReset (fn),
 *        mode 'instant'|'draft', resultCount (num), loading (bool)
 *
 * filter: { provinceId, districtId, wardId, priceFrom, priceTo, areaFrom, areaTo,
 *   categoryCode, genderRequirement, maxOccupants, furnitureStatus, toiletType,
 *   curfewType, petAllowed, parkingAvailable, amenityIds[] }
 */

const TOILET = { PRIVATE: 'Riêng', SHARED: 'Chung' };
const CURFEW = { FREE: 'Tự do', CURFEW: 'Có giờ giấc' };
const PRICE_MAX = 20000000;
const AREA_MAX = 100;

export default function SearchFilterPanel({
  value = {},
  onChange,
  onApply,
  onReset,
  mode = 'instant',
  resultCount,
  loading = false,
}) {
  const isDraft = mode === 'draft';
  const [draft, setDraft] = useState(value);

  // Đồng bộ khi value ngoài đổi (áp lọc từ URL, reset...).
  useEffect(() => {
    setDraft(value);
  }, [value]);

  const current = isDraft ? draft : value;

  const patch = (partial) => {
    const next = { ...current, ...partial };
    if (isDraft) setDraft(next);
    else onChange?.(next);
  };

  const handleReset = () => {
    const empty = {};
    if (isDraft) setDraft(empty);
    onReset?.();
  };

  const handleApply = () => onApply?.(draft);

  const isRoommate = current.categoryCode === 'ROOMMATE';

  // Nhóm toggle single-select có thể bỏ chọn (bấm lại về null).
  const toggleProps = (field) => ({
    exclusive: true,
    size: 'small',
    value: current[field] ?? null,
    onChange: (_, v) => patch({ [field]: v }),
  });

  return (
    <Box>
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ mb: 1 }}
      >
        <Typography variant="h6">Bộ lọc</Typography>
        <Button size="small" color="inherit" onClick={handleReset}>
          Xóa lọc
        </Button>
      </Stack>
      <Divider sx={{ mb: 2 }} />

      <Stack spacing={2} divider={<Divider flexItem />}>
        {/* Khu vực */}
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Khu vực
          </Typography>
          <AddressSelector
            value={{
              provinceId: current.provinceId ?? null,
              districtId: current.districtId ?? null,
              wardId: current.wardId ?? null,
            }}
            onChange={(v) =>
              patch({ provinceId: v.provinceId, districtId: v.districtId, wardId: v.wardId })
            }
          />
        </Box>

        {/* Loại tin */}
        <TextField
          select
          label="Loại tin"
          value={current.categoryCode ?? ''}
          onChange={(e) => patch({ categoryCode: e.target.value || null })}
        >
          <MenuItem value="">Tất cả</MenuItem>
          {Object.entries(CATEGORY_CODES).map(([code, label]) => (
            <MenuItem key={code} value={code}>
              {label}
            </MenuItem>
          ))}
        </TextField>

        {/* Khoảng giá */}
        <PriceRangeSlider
          label="Khoảng giá (đồng/tháng)"
          min={0}
          max={PRICE_MAX}
          step={500000}
          unit="đ"
          value={[current.priceFrom ?? 0, current.priceTo ?? PRICE_MAX]}
          onChange={([from, to]) =>
            patch({ priceFrom: from || undefined, priceTo: to >= PRICE_MAX ? undefined : to })
          }
        />

        {/* Diện tích */}
        <PriceRangeSlider
          label="Diện tích (m²)"
          min={0}
          max={AREA_MAX}
          step={5}
          unit="m²"
          value={[current.areaFrom ?? 0, current.areaTo ?? AREA_MAX]}
          onChange={([from, to]) =>
            patch({ areaFrom: from || undefined, areaTo: to >= AREA_MAX ? undefined : to })
          }
        />

        {/* Ở ghép — chỉ hiện khi categoryCode = ROOMMATE */}
        {isRoommate && (
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Ở ghép
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Giới tính
            </Typography>
            <ToggleButtonGroup {...toggleProps('genderRequirement')} sx={{ display: 'flex', mt: 0.5 }}>
              {Object.entries(GENDER_REQUIREMENT).map(([code, label]) => (
                <ToggleButton key={code} value={code} sx={{ flex: 1 }}>
                  {label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
            <TextField
              select
              label="Số người ở tối đa"
              value={current.maxOccupants ?? ''}
              onChange={(e) =>
                patch({ maxOccupants: e.target.value ? Number(e.target.value) : undefined })
              }
              sx={{ mt: 1.5 }}
            >
              <MenuItem value="">Không giới hạn</MenuItem>
              {[1, 2, 3, 4, 5, 6].map((n) => (
                <MenuItem key={n} value={n}>
                  {n === 6 ? '6 người trở lên' : `${n} người`}
                </MenuItem>
              ))}
            </TextField>
          </Box>
        )}

        {/* Nội thất */}
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Nội thất
          </Typography>
          <ToggleButtonGroup {...toggleProps('furnitureStatus')} sx={{ display: 'flex' }}>
            {Object.entries(FURNITURE_STATUS).map(([code, label]) => (
              <ToggleButton key={code} value={code} sx={{ flex: 1 }}>
                {label}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Box>

        {/* Nhà vệ sinh */}
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Nhà vệ sinh
          </Typography>
          <ToggleButtonGroup {...toggleProps('toiletType')} sx={{ display: 'flex' }}>
            {Object.entries(TOILET).map(([code, label]) => (
              <ToggleButton key={code} value={code} sx={{ flex: 1 }}>
                {label}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Box>

        {/* Giờ giấc */}
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Giờ giấc
          </Typography>
          <ToggleButtonGroup {...toggleProps('curfewType')} sx={{ display: 'flex' }}>
            {Object.entries(CURFEW).map(([code, label]) => (
              <ToggleButton key={code} value={code} sx={{ flex: 1 }}>
                {label}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Box>

        {/* Cờ tiện ích khác */}
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
            Khác
          </Typography>
          <FormControlLabel
            control={
              <Checkbox
                size="small"
                checked={!!current.petAllowed}
                onChange={(e) => patch({ petAllowed: e.target.checked || undefined })}
              />
            }
            label="Cho nuôi thú cưng"
          />
          <FormControlLabel
            control={
              <Checkbox
                size="small"
                checked={!!current.parkingAvailable}
                onChange={(e) => patch({ parkingAvailable: e.target.checked || undefined })}
              />
            }
            label="Có chỗ để xe"
          />
        </Box>

        {/* Tiện ích */}
        <Accordion disableGutters elevation={0} defaultExpanded sx={{ '&:before': { display: 'none' } }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ px: 0 }}>
            <Typography variant="subtitle2">Tiện ích</Typography>
          </AccordionSummary>
          <AccordionDetails sx={{ px: 0 }}>
            <AmenityPicker
              value={current.amenityIds ?? []}
              onChange={(ids) => patch({ amenityIds: ids.length ? ids : undefined })}
            />
          </AccordionDetails>
        </Accordion>
      </Stack>

      {/* Footer hành động — draft mode (mobile): nút "Xem N kết quả" apply + đóng */}
      {isDraft ? (
        <Stack
          direction="row"
          spacing={1}
          sx={{
            position: 'sticky',
            bottom: 0,
            bgcolor: 'background.paper',
            py: 1.5,
            mt: 1,
            borderTop: '1px solid',
            borderColor: 'divider',
          }}
        >
          <Button color="inherit" onClick={handleReset} sx={{ flexShrink: 0 }}>
            Xóa lọc
          </Button>
          <Button variant="contained" fullWidth onClick={handleApply} disabled={loading}>
            {typeof resultCount === 'number'
              ? `Xem ${resultCount.toLocaleString('vi-VN')} kết quả`
              : 'Áp dụng'}
          </Button>
        </Stack>
      ) : (
        onApply && (
          <Button variant="contained" fullWidth sx={{ mt: 2 }} onClick={() => onApply(current)}>
            Áp dụng bộ lọc
          </Button>
        )
      )}
    </Box>
  );
}
