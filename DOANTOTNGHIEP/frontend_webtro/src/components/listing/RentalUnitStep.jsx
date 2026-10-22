import { Controller } from 'react-hook-form';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert, Autocomplete, Box, Button, Grid, Link, Rating, Stack, TextField, ToggleButton, ToggleButtonGroup,
  Typography,
} from '@mui/material';
import HomeWorkOutlinedIcon from '@mui/icons-material/HomeWorkOutlined';
import AddHomeOutlinedIcon from '@mui/icons-material/AddHomeOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import AddressSelector from '@/components/filter/AddressSelector';

/**
 * Bước "Phòng & địa chỉ" của wizard đăng tin (docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 5).
 *
 * - Đăng tin mới (chủ trọ): chọn phòng có sẵn (chưa có tin sống) hoặc tạo phòng mới. Địa chỉ của
 *   tin luôn lấy từ phòng (RU-3).
 * - Phòng mới: wizard dò trùng trước khi sang bước sau; trùng phòng của chính mình thì gợi ý dùng
 *   phòng đó (RU-6).
 * - Sửa tin: phòng đã có đánh giá thì khoá địa chỉ và số phòng (RU-4), trừ quản trị viên.
 */
export default function RentalUnitStep({
  control, errors, mode, canPickUnit, unitMode, onUnitModeChange, units, selectedUnit, onSelectUnit,
  duplicate, onUseDuplicate, rentalUnit, addressLocked,
}) {
  const creating = mode === 'create';
  const usingExisting = creating && canPickUnit && unitMode === 'existing';
  const fieldsDisabled = usingExisting || addressLocked;

  return (
    <Grid container spacing={2}>
      {creating && canPickUnit && (
        <Grid item xs={12}>
          <ToggleButtonGroup exclusive value={unitMode} onChange={(_, v) => v && onUnitModeChange(v)} size="small">
            <ToggleButton value="existing" disabled={!units.length}>
              <HomeWorkOutlinedIcon fontSize="small" sx={{ mr: 1 }} /> Chọn phòng có sẵn
            </ToggleButton>
            <ToggleButton value="new">
              <AddHomeOutlinedIcon fontSize="small" sx={{ mr: 1 }} /> Phòng mới
            </ToggleButton>
          </ToggleButtonGroup>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Đánh giá gắn với phòng, nên đăng lại tin cho phòng cũ sẽ giữ nguyên đánh giá trước đó.
          </Typography>
          {!units.length && (
            <Alert severity="info" sx={{ mt: 1.5 }}>
              Bạn chưa có phòng nào đang trống tin: mỗi phòng chỉ có một tin đăng tại một thời điểm. Hãy tạo
              phòng mới bên dưới, hoặc vào <Link component={RouterLink} to="/quan-ly/phong">Phòng của tôi</Link>{' '}
              và bấm &quot;Đăng lại&quot; cho phòng có tin đã đóng.
            </Alert>
          )}
        </Grid>
      )}

      {usingExisting && (
        <Grid item xs={12}>
          <Autocomplete
            options={units}
            value={selectedUnit}
            onChange={(_, u) => onSelectUnit(u)}
            getOptionLabel={(u) => [u.name, u.unitLabel && `(${u.unitLabel})`].filter(Boolean).join(' ')}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderOption={(props, u) => (
              <Box component="li" {...props} key={u.id}>
                <Stack sx={{ width: '100%' }}>
                  <Stack direction="row" justifyContent="space-between" spacing={1}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {u.name}{u.unitLabel ? ` · ${u.unitLabel}` : ''}
                    </Typography>
                    {u.reviewCount > 0 && (
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        <Rating size="small" readOnly precision={0.1} value={Number(u.averageRating) || 0} />
                        <Typography variant="caption">({u.reviewCount})</Typography>
                      </Stack>
                    )}
                  </Stack>
                  <Typography variant="caption" color="text.secondary">{u.fullAddress}</Typography>
                </Stack>
              </Box>
            )}
            renderInput={(params) => (
              <TextField {...params} label="Phòng" required helperText="Chỉ hiện phòng chưa có tin đang đăng" />
            )}
          />
        </Grid>
      )}

      {!creating && rentalUnit && (
        <Grid item xs={12}>
          <Alert severity={addressLocked ? 'warning' : 'info'} icon={addressLocked ? <LockOutlinedIcon /> : undefined}>
            Tin thuộc phòng <b>{rentalUnit.name}</b>
            {rentalUnit.reviewCount > 0 ? ` · ${rentalUnit.reviewCount} đánh giá` : ''}.
            {addressLocked
              ? ' Phòng đã có đánh giá nên không đổi được địa chỉ và số phòng.'
              : ' Đổi địa chỉ ở đây sẽ đổi địa chỉ của phòng và mọi tin của phòng.'}
          </Alert>
        </Grid>
      )}

      <Grid item xs={12} md={7}>
        <Controller name="provinceId" control={control} rules={{ required: true }} render={({ field: pField }) => (
          <Controller name="districtId" control={control} rules={{ required: true }} render={({ field: dField }) => (
            <Controller name="wardId" control={control} rules={{ required: true }} render={({ field: wField }) => (
              <AddressSelector
                required
                disabled={fieldsDisabled}
                value={{ provinceId: pField.value, districtId: dField.value, wardId: wField.value }}
                onChange={(val) => { pField.onChange(val.provinceId); dField.onChange(val.districtId); wField.onChange(val.wardId); }}
                error={{
                  provinceId: errors.provinceId ? 'Vui lòng chọn tỉnh/thành' : undefined,
                  districtId: errors.districtId ? 'Vui lòng chọn quận/huyện' : undefined,
                  wardId: errors.wardId ? 'Vui lòng chọn phường/xã' : undefined,
                }}
              />
            )} />
          )} />
        )} />
      </Grid>
      <Grid item xs={12} md={5}>
        <Stack spacing={2}>
          <Controller name="addressDetail" control={control}
            rules={{ required: 'Vui lòng nhập địa chỉ', minLength: { value: 5, message: 'Tối thiểu 5 ký tự' } }}
            render={({ field }) => (
              <TextField {...field} label="Số nhà, tên đường" fullWidth required multiline minRows={2}
                disabled={fieldsDisabled}
                error={!!errors.addressDetail} helperText={errors.addressDetail?.message || 'Ví dụ: 45/12 Đường D2'} />
            )} />
          <Controller name="unitLabel" control={control}
            rules={{ maxLength: { value: 50, message: 'Tối đa 50 ký tự' } }}
            render={({ field }) => (
              <TextField {...field} label="Số phòng (nếu có)" fullWidth disabled={fieldsDisabled}
                error={!!errors.unitLabel}
                helperText={errors.unitLabel?.message || 'Ví dụ: P101. Dùng để phân biệt các phòng cùng địa chỉ'} />
            )} />
          {creating && !usingExisting && canPickUnit && (
            <Controller name="unitName" control={control}
              rules={{ maxLength: { value: 150, message: 'Tối đa 150 ký tự' } }}
              render={({ field }) => (
                <TextField {...field} label="Tên phòng (tuỳ chọn)" fullWidth
                  error={!!errors.unitName}
                  helperText={errors.unitName?.message || 'Bỏ trống thì dùng tiêu đề tin'} />
              )} />
          )}
        </Stack>
      </Grid>

      {duplicate?.duplicate && duplicate.duplicateUnit && (
        <Grid item xs={12}>
          <Alert severity="error" action={(
            <Button color="inherit" size="small" onClick={() => onUseDuplicate(duplicate.duplicateUnit)}>
              Dùng phòng này
            </Button>
          )}>
            Bạn đã có phòng <b>{duplicate.duplicateUnit.name}</b> ở đúng địa chỉ và số phòng này.
            {duplicate.duplicateUnit.liveListing
              ? ' Phòng đó đang có tin đăng, hãy sửa hoặc gia hạn tin đó thay vì đăng tin mới.'
              : ' Hãy dùng phòng có sẵn để giữ các đánh giá cũ.'}
          </Alert>
        </Grid>
      )}
      {duplicate && !duplicate.duplicate && duplicate.sameAddressUnits?.length > 0 && (
        <Grid item xs={12}>
          <Alert severity="info">
            Bạn có {duplicate.sameAddressUnits.length} phòng khác ở cùng địa chỉ:{' '}
            {duplicate.sameAddressUnits.map((u) => u.unitLabel || u.name).join(', ')}. Nếu đây là một trong số đó,
            hãy chọn &quot;Chọn phòng có sẵn&quot;.
          </Alert>
        </Grid>
      )}
      {duplicate?.otherOwnerMatch && !duplicate.duplicate && (
        <Grid item xs={12}>
          <Alert severity="warning">
            Địa chỉ này trùng với phòng của một chủ trọ khác. Bạn vẫn đăng được, nhưng tin sẽ được kiểm duyệt
            viên kiểm tra kỹ hơn.
          </Alert>
        </Grid>
      )}
    </Grid>
  );
}
