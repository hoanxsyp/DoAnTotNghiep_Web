import { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Typography,
  FormGroup,
  FormControlLabel,
  Checkbox,
  Button,
  Stack,
  CircularProgress,
} from '@mui/material';
import catalogApi from '@/api/catalogApi';
import notify from '@/utils/toast';

/**
 * Chọn nhiều tiện ích, nhóm theo AmenityGroup (docs/04 mục 6 #5, [§10.5]).
 *
 * - Nhóm: nội thất / an ninh / sinh hoạt / giao thông.
 * - Mỗi nhóm hiện maxVisible mục + "Xem thêm ▾".
 * - Chỉ hiện tiện ích active.
 * - Nếu không truyền `amenities` qua props thì tự gọi @/api/catalogApi.getAmenities() (cache phiên).
 *
 * Props: amenities (arr, tùy chọn), value (arr id), onChange (fn), groupBy (bool),
 *        collapsible (bool), maxVisible (num)
 */

// Map cả 2 biến thể mã nhóm (API dùng NOI_THAT..., canonical §5 dùng FURNITURE...).
const GROUP_META = {
  NOI_THAT: { label: 'Nội thất', order: 1 },
  FURNITURE: { label: 'Nội thất', order: 1 },
  AN_NINH: { label: 'An ninh', order: 2 },
  SECURITY: { label: 'An ninh', order: 2 },
  SINH_HOAT: { label: 'Sinh hoạt', order: 3 },
  LIVING: { label: 'Sinh hoạt', order: 3 },
  UTILITY: { label: 'Sinh hoạt', order: 3 },
  GIAO_THONG: { label: 'Giao thông', order: 4 },
  TRANSPORT: { label: 'Giao thông', order: 4 },
};

let amenityCache = null;

export default function AmenityPicker({
  amenities: amenitiesProp,
  value = [],
  onChange,
  groupBy = true,
  collapsible = true,
  maxVisible = 6,
}) {
  const [amenities, setAmenities] = useState(amenitiesProp || amenityCache || []);
  const [loading, setLoading] = useState(false);
  const [expandedGroups, setExpandedGroups] = useState({});

  useEffect(() => {
    if (amenitiesProp) {
      setAmenities(amenitiesProp);
      return undefined;
    }
    if (amenityCache) {
      setAmenities(amenityCache);
      return undefined;
    }
    let alive = true;
    setLoading(true);
    catalogApi
      .getAmenities()
      .then((data) => {
        amenityCache = (data || []).filter((a) => a.active !== false);
        if (alive) setAmenities(amenityCache);
      })
      .catch((e) => notify.apiError(e))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [amenitiesProp]);

  const selected = new Set(value);

  const toggle = (id) => {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onChange?.(Array.from(next));
  };

  const groups = useMemo(() => {
    const active = amenities.filter((a) => a.active !== false);
    if (!groupBy) return [{ key: 'ALL', label: '', items: active }];
    const map = {};
    active.forEach((a) => {
      const key = a.group || 'OTHER';
      map[key] = map[key] || [];
      map[key].push(a);
    });
    return Object.keys(map)
      .map((key) => ({
        key,
        label: GROUP_META[key]?.label || 'Khác',
        order: GROUP_META[key]?.order ?? 99,
        items: map[key].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
      }))
      .sort((a, b) => a.order - b.order);
  }, [amenities, groupBy]);

  if (loading) {
    return (
      <Box sx={{ py: 2, textAlign: 'center' }}>
        <CircularProgress size={22} />
      </Box>
    );
  }

  return (
    <Stack spacing={2}>
      {groups.map((group) => {
        const isExpanded = expandedGroups[group.key];
        const visibleItems =
          collapsible && !isExpanded ? group.items.slice(0, maxVisible) : group.items;
        const hasMore = collapsible && group.items.length > maxVisible;

        return (
          <Box key={group.key}>
            {group.label && (
              <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                {group.label}
              </Typography>
            )}
            <FormGroup>
              {visibleItems.map((a) => (
                <FormControlLabel
                  key={a.id}
                  control={
                    <Checkbox
                      size="small"
                      checked={selected.has(a.id)}
                      onChange={() => toggle(a.id)}
                    />
                  }
                  label={a.name}
                />
              ))}
            </FormGroup>
            {hasMore && (
              <Button
                size="small"
                onClick={() =>
                  setExpandedGroups((s) => ({ ...s, [group.key]: !s[group.key] }))
                }
              >
                {isExpanded ? 'Thu gọn' : `Xem thêm (${group.items.length - maxVisible})`}
              </Button>
            )}
          </Box>
        );
      })}
    </Stack>
  );
}
