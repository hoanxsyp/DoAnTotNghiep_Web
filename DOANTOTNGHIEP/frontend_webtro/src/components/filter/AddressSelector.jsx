import { useEffect, useState } from 'react';
import { Autocomplete, TextField, Stack } from '@mui/material';
import catalogApi from '@/api/catalogApi';
import notify from '@/utils/toast';

/**
 * 3 Autocomplete tỉnh/huyện/xã LIÊN TẦNG (docs/04 mục 6 #8, 6.3).
 *
 * - Chọn Tỉnh -> tải Huyện + RESET Huyện/Xã; chọn Huyện -> tải Xã + reset Xã.
 * - Dữ liệu lấy từ @/api/catalogApi, cache ở module (mỗi danh mục chỉ gọi 1 lần/phiên [§11.11]).
 * - Tìm kiếm bỏ dấu (gõ "quan 1" ra "Quận 1").
 *
 * Props: value ({provinceId, districtId, wardId}), onChange (fn), required (bool),
 *        levels (1..3, mặc định 3), error (obj {provinceId,districtId,wardId}), disabled (bool)
 *
 * catalogApi kỳ vọng: getProvinces(), getDistricts(provinceId), getWards(districtId)
 * — trả mảng {id, name, ...}. File catalogApi do agent khác tạo cùng lúc.
 */

// Cache toàn phiên để không gọi lại API [§11.11].
const cache = { provinces: null, districts: {}, wards: {} };

const removeDiacritics = (str = '') =>
  str
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D');

const filterNoDiacritics = (options, { inputValue }) => {
  const q = removeDiacritics(inputValue).toLowerCase().trim();
  if (!q) return options;
  return options.filter((o) => removeDiacritics(o.name || '').toLowerCase().includes(q));
};

export default function AddressSelector({
  value = {},
  onChange,
  required = false,
  levels = 3,
  error = {},
  disabled = false,
}) {
  const [provinces, setProvinces] = useState(cache.provinces || []);
  const [districts, setDistricts] = useState([]);
  const [wards, setWards] = useState([]);
  const [loading, setLoading] = useState({ p: false, d: false, w: false });

  const { provinceId = null, districtId = null, wardId = null } = value;

  // Tải tỉnh 1 lần.
  useEffect(() => {
    let alive = true;
    if (cache.provinces) {
      setProvinces(cache.provinces);
      return undefined;
    }
    setLoading((s) => ({ ...s, p: true }));
    catalogApi
      .getProvinces()
      .then((data) => {
        cache.provinces = data || [];
        if (alive) setProvinces(cache.provinces);
      })
      .catch((e) => notify.apiError(e))
      .finally(() => alive && setLoading((s) => ({ ...s, p: false })));
    return () => {
      alive = false;
    };
  }, []);

  // Tải huyện khi có tỉnh.
  useEffect(() => {
    let alive = true;
    if (!provinceId || levels < 2) {
      setDistricts([]);
      return undefined;
    }
    if (cache.districts[provinceId]) {
      setDistricts(cache.districts[provinceId]);
      return undefined;
    }
    setLoading((s) => ({ ...s, d: true }));
    catalogApi
      .getDistricts(provinceId)
      .then((data) => {
        cache.districts[provinceId] = data || [];
        if (alive) setDistricts(cache.districts[provinceId]);
      })
      .catch((e) => notify.apiError(e))
      .finally(() => alive && setLoading((s) => ({ ...s, d: false })));
    return () => {
      alive = false;
    };
  }, [provinceId, levels]);

  // Tải xã khi có huyện.
  useEffect(() => {
    let alive = true;
    if (!districtId || levels < 3) {
      setWards([]);
      return undefined;
    }
    if (cache.wards[districtId]) {
      setWards(cache.wards[districtId]);
      return undefined;
    }
    setLoading((s) => ({ ...s, w: true }));
    catalogApi
      .getWards(districtId)
      .then((data) => {
        cache.wards[districtId] = data || [];
        if (alive) setWards(cache.wards[districtId]);
      })
      .catch((e) => notify.apiError(e))
      .finally(() => alive && setLoading((s) => ({ ...s, w: false })));
    return () => {
      alive = false;
    };
  }, [districtId, levels]);

  // Đổi cấp cha PHẢI reset cấp con (tránh districtId thuộc tỉnh cũ).
  const handleProvince = (p) =>
    onChange?.({ provinceId: p?.id ?? null, districtId: null, wardId: null });
  const handleDistrict = (d) =>
    onChange?.({ provinceId, districtId: d?.id ?? null, wardId: null });
  const handleWard = (w) => onChange?.({ provinceId, districtId, wardId: w?.id ?? null });

  return (
    <Stack spacing={2}>
      <Autocomplete
        options={provinces}
        getOptionLabel={(o) => o?.name || ''}
        isOptionEqualToValue={(o, v) => o.id === v.id}
        value={provinces.find((p) => p.id === provinceId) ?? null}
        onChange={(_, v) => handleProvince(v)}
        loading={loading.p}
        disabled={disabled}
        filterOptions={filterNoDiacritics}
        renderInput={(params) => (
          <TextField
            {...params}
            label="Tỉnh/Thành phố"
            required={required}
            error={!!error?.provinceId}
            helperText={error?.provinceId?.message || error?.provinceId}
          />
        )}
      />

      {levels >= 2 && (
        <Autocomplete
          options={districts}
          getOptionLabel={(o) => o?.name || ''}
          isOptionEqualToValue={(o, v) => o.id === v.id}
          value={districts.find((d) => d.id === districtId) ?? null}
          onChange={(_, v) => handleDistrict(v)}
          loading={loading.d}
          disabled={disabled || !provinceId}
          filterOptions={filterNoDiacritics}
          renderInput={(params) => (
            <TextField
              {...params}
              label="Quận/Huyện"
              required={required}
              error={!!error?.districtId}
              helperText={error?.districtId?.message || error?.districtId}
            />
          )}
        />
      )}

      {levels >= 3 && (
        <Autocomplete
          options={wards}
          getOptionLabel={(o) => o?.name || ''}
          isOptionEqualToValue={(o, v) => o.id === v.id}
          value={wards.find((w) => w.id === wardId) ?? null}
          onChange={(_, v) => handleWard(v)}
          loading={loading.w}
          disabled={disabled || !districtId}
          filterOptions={filterNoDiacritics}
          renderInput={(params) => (
            <TextField
              {...params}
              label="Phường/Xã"
              required={required}
              error={!!error?.wardId}
              helperText={error?.wardId?.message || error?.wardId}
            />
          )}
        />
      )}
    </Stack>
  );
}
