import { useCallback, useEffect, useMemo, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import dayjs from 'dayjs';
import {
  Box, Card, CardContent, Stepper, Step, StepLabel, Grid, TextField, MenuItem, Button, Stack,
  Typography, Chip, FormControlLabel, Checkbox, Switch, Alert, InputAdornment, Divider, CircularProgress,
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import listingApi from '@/api/listingApi';
import aiApi from '@/api/aiApi';
import catalogApi from '@/api/catalogApi';
import AddressSelector from '@/components/filter/AddressSelector';
import ImageUploader from '@/components/listing/ImageUploader';
import notify from '@/utils/toast';
import { formatPrice, formatCurrency } from '@/utils/format';
import { CATEGORY_CODES, FURNITURE_STATUS, GENDER_REQUIREMENT } from '@/constants';

const STEPS = ['Loại tin', 'Thông tin cơ bản', 'Địa chỉ', 'Giá & tiện ích', 'Hình ảnh', 'Xem lại'];

const CURFEW = { FREE: 'Giờ giấc tự do', CURFEW: 'Có giờ giới nghiêm', UNKNOWN: 'Không rõ' };
const TOILET_TYPE = { PRIVATE: 'Vệ sinh riêng', SHARED: 'Vệ sinh chung' };
const ROOM_REQUIRED = ['WHOLE_HOUSE', 'APARTMENT', 'MINI_APARTMENT'];

const DEFAULTS = {
  categoryId: '', title: '', description: '', price: '', area: '', depositAmount: '',
  electricityPrice: '', waterPrice: '', provinceId: null, districtId: null, wardId: null,
  addressDetail: '', roomCount: '', toiletCount: 1, maxOccupants: 1, currentOccupants: '',
  genderRequirement: 'ANY', petAllowed: false, parkingAvailable: false, curfewType: 'UNKNOWN',
  furnitureStatus: 'NONE', toiletType: 'PRIVATE', availableFrom: null, amenityIds: [],
  contactName: '', contactPhone: '',
};

/**
 * Trình đăng/sửa tin nhiều bước (docs/04 §11.7, docs/03 mục 4.4.7/4.4.8).
 * mode: 'create' | 'edit'. Ở 'edit' truyền listingId + initialData (đã map từ chi tiết tin).
 */
export default function ListingWizard({ mode = 'create', listingId, initialData, onDone }) {
  const [activeStep, setActiveStep] = useState(0);
  const [categories, setCategories] = useState([]);
  const [amenities, setAmenities] = useState([]);
  const [configs, setConfigs] = useState({ 'listing.title.min': 10, 'listing.title.max': 150, 'listing.description.min': 30, 'listing.description.max': 3000, 'listing.image.min': 1, 'listing.image.max': 10, 'listing.image.max_size_mb': 5 });
  const [images, setImages] = useState(initialData?.images || []);
  const [primaryId, setPrimaryId] = useState(initialData?.primaryImageId || null);
  const [price, setPriceState] = useState(null);
  const [predicting, setPredicting] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const {
    control, handleSubmit, trigger, watch, getValues, formState: { errors },
  } = useForm({ defaultValues: { ...DEFAULTS, ...(initialData?.values || {}) }, mode: 'onTouched' });

  const categoryId = watch('categoryId');
  const selectedCategory = categories.find((c) => String(c.id) === String(categoryId));
  const isRoommate = selectedCategory?.code === 'ROOMMATE';
  const needRoomCount = ROOM_REQUIRED.includes(selectedCategory?.code);

  useEffect(() => {
    Promise.all([
      catalogApi.getCategories({ activeOnly: true }).catch(() => []),
      catalogApi.getAmenities({ activeOnly: true }).catch(() => []),
      catalogApi.getPublicConfigs().catch(() => null),
    ]).then(([cats, ams, cfg]) => {
      setCategories(cats || []);
      setAmenities(ams || []);
      if (cfg?.configs) setConfigs((prev) => ({ ...prev, ...cfg.configs }));
    });
  }, []);

  const amenityGroups = useMemo(() => {
    const groups = {};
    (amenities || []).forEach((a) => {
      const g = a.group || 'KHAC';
      (groups[g] = groups[g] || []).push(a);
    });
    return groups;
  }, [amenities]);

  const stepFields = useCallback((step) => {
    switch (step) {
      case 0: return ['categoryId'];
      case 1: {
        const f = ['title', 'description', 'maxOccupants'];
        if (needRoomCount) f.push('roomCount');
        if (isRoommate) f.push('currentOccupants', 'genderRequirement');
        return f;
      }
      case 2: return ['provinceId', 'districtId', 'wardId', 'addressDetail'];
      case 3: return ['price', 'area'];
      default: return [];
    }
  }, [needRoomCount, isRoommate]);

  const handleNext = async () => {
    const ok = await trigger(stepFields(activeStep));
    if (!ok) return;
    if (activeStep === 3) runPrediction();
    setActiveStep((s) => Math.min(s + 1, STEPS.length - 1));
  };
  const handleBack = () => setActiveStep((s) => Math.max(s - 1, 0));

  const runPrediction = async () => {
    const v = getValues();
    if (!v.categoryId || !v.wardId || !v.area) return;
    setPredicting(true);
    try {
      const res = await aiApi.predictPrice({
        categoryId: Number(v.categoryId), provinceId: v.provinceId, districtId: v.districtId, wardId: v.wardId,
        area: Number(v.area), roomCount: v.roomCount ? Number(v.roomCount) : undefined,
        toiletCount: v.toiletCount ? Number(v.toiletCount) : undefined,
        furnitureStatus: v.furnitureStatus, toiletType: v.toiletType,
        amenityIds: v.amenityIds, inputPrice: v.price ? Number(v.price) : undefined,
      });
      setPriceState(res);
    } catch (e) {
      setPriceState({ available: false, message: e?.message });
    } finally {
      setPredicting(false);
    }
  };

  const buildPayload = (submitImmediately) => {
    const v = getValues();
    return {
      categoryId: Number(v.categoryId),
      title: v.title.trim(),
      description: v.description.trim(),
      price: Number(v.price),
      area: Number(v.area),
      depositAmount: v.depositAmount ? Number(v.depositAmount) : 0,
      electricityPrice: v.electricityPrice ? Number(v.electricityPrice) : undefined,
      waterPrice: v.waterPrice ? Number(v.waterPrice) : undefined,
      provinceId: v.provinceId, districtId: v.districtId, wardId: v.wardId,
      addressDetail: v.addressDetail.trim(),
      roomCount: v.roomCount ? Number(v.roomCount) : undefined,
      toiletCount: v.toiletCount ? Number(v.toiletCount) : undefined,
      maxOccupants: Number(v.maxOccupants),
      currentOccupants: v.currentOccupants !== '' ? Number(v.currentOccupants) : undefined,
      genderRequirement: isRoommate ? v.genderRequirement : undefined,
      petAllowed: v.petAllowed, parkingAvailable: v.parkingAvailable,
      curfewType: v.curfewType, furnitureStatus: v.furnitureStatus, toiletType: v.toiletType,
      availableFrom: v.availableFrom ? dayjs(v.availableFrom).format('YYYY-MM-DD') : undefined,
      amenityIds: v.amenityIds,
      contactName: v.contactName.trim(), contactPhone: v.contactPhone.trim(),
      submitImmediately,
    };
  };

  const uploadNewImages = async (id) => {
    const newFiles = images.filter((i) => i.file);
    if (!newFiles.length) return;
    const formData = new FormData();
    newFiles.forEach((i) => formData.append('files', i.file));
    await listingApi.uploadImages(id, formData);
  };

  const doSubmit = async (submitImmediately) => {
    const ok = await trigger(['contactName', 'contactPhone']);
    if (!ok) { setActiveStep(5); return; }
    const imageCount = images.length;
    if (submitImmediately && imageCount < (configs['listing.image.min'] || 1)) {
      notify.warning(`Cần tối thiểu ${configs['listing.image.min'] || 1} ảnh trước khi gửi duyệt`);
      setActiveStep(4);
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'edit') {
        await listingApi.update(listingId, buildPayload(false));
        await uploadNewImages(listingId);
        if (submitImmediately) await listingApi.submit(listingId);
        notify.success('Đã cập nhật tin đăng');
        onDone?.(listingId);
      } else {
        const created = await listingApi.create(buildPayload(false));
        const id = created?.id;
        await uploadNewImages(id);
        if (submitImmediately) await listingApi.submit(id);
        notify.success(submitImmediately ? 'Đã gửi tin chờ duyệt' : 'Đã lưu tin nháp');
        onDone?.(id);
      }
    } catch (e) {
      notify.apiError(e, 'Lưu tin thất bại');
    } finally {
      setSubmitting(false);
    }
  };

  const titleMax = configs['listing.title.max'];
  const descMax = configs['listing.description.max'];

  return (
    <Box>
      <Stepper activeStep={activeStep} alternativeLabel sx={{ mb: 3, display: { xs: 'none', sm: 'flex' } }}>
        {STEPS.map((label) => <Step key={label}><StepLabel>{label}</StepLabel></Step>)}
      </Stepper>
      <Typography sx={{ display: { xs: 'block', sm: 'none' }, mb: 2, fontWeight: 700 }}>
        Bước {activeStep + 1}/{STEPS.length}: {STEPS[activeStep]}
      </Typography>

      <Card>
        <CardContent>
          {/* ---- Bước 1: Loại tin ---- */}
          {activeStep === 0 && (
            <Grid container spacing={2}>
              {categories.map((c) => (
                <Grid item xs={6} sm={4} md={3} key={c.id}>
                  <Controller name="categoryId" control={control} rules={{ required: 'Vui lòng chọn loại tin' }} render={({ field }) => (
                    <Card
                      variant="outlined"
                      onClick={() => field.onChange(String(c.id))}
                      sx={{
                        cursor: 'pointer', textAlign: 'center', p: 2,
                        borderColor: String(field.value) === String(c.id) ? 'primary.main' : 'divider',
                        borderWidth: 2, bgcolor: String(field.value) === String(c.id) ? 'action.selected' : 'transparent',
                      }}
                    >
                      <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>{c.name || CATEGORY_CODES[c.code]}</Typography>
                    </Card>
                  )} />
                </Grid>
              ))}
              {errors.categoryId && <Grid item xs={12}><Typography color="error" variant="caption">{errors.categoryId.message}</Typography></Grid>}
            </Grid>
          )}

          {/* ---- Bước 2: Thông tin cơ bản ---- */}
          {activeStep === 1 && (
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <Controller name="title" control={control}
                  rules={{ required: 'Vui lòng nhập tiêu đề', minLength: { value: configs['listing.title.min'], message: `Tối thiểu ${configs['listing.title.min']} ký tự` }, maxLength: { value: titleMax, message: `Tối đa ${titleMax} ký tự` } }}
                  render={({ field }) => (
                    <TextField {...field} label="Tiêu đề tin" fullWidth required error={!!errors.title}
                      helperText={errors.title?.message || `${field.value?.length || 0}/${titleMax}`} inputProps={{ maxLength: titleMax }} />
                  )} />
              </Grid>
              <Grid item xs={12}>
                <Controller name="description" control={control}
                  rules={{ required: 'Vui lòng nhập mô tả', minLength: { value: configs['listing.description.min'], message: `Tối thiểu ${configs['listing.description.min']} ký tự` }, maxLength: { value: descMax, message: `Tối đa ${descMax} ký tự` } }}
                  render={({ field }) => (
                    <TextField {...field} label="Mô tả chi tiết" fullWidth required multiline minRows={4} error={!!errors.description}
                      helperText={errors.description?.message || `${field.value?.length || 0}/${descMax}`} inputProps={{ maxLength: descMax }} />
                  )} />
              </Grid>
              <Grid item xs={6} sm={3}>
                <Controller name="maxOccupants" control={control} rules={{ required: 'Bắt buộc', min: { value: 1, message: '≥ 1' } }} render={({ field }) => (
                  <TextField {...field} type="number" label="Số người tối đa" fullWidth required error={!!errors.maxOccupants} helperText={errors.maxOccupants?.message} />
                )} />
              </Grid>
              {isRoommate && (
                <>
                  <Grid item xs={6} sm={3}>
                    <Controller name="currentOccupants" control={control} rules={{ required: 'Bắt buộc' }} render={({ field }) => (
                      <TextField {...field} type="number" label="Số người hiện tại" fullWidth required error={!!errors.currentOccupants} helperText={errors.currentOccupants?.message} />
                    )} />
                  </Grid>
                  <Grid item xs={12} sm={3}>
                    <Controller name="genderRequirement" control={control} rules={{ required: 'Bắt buộc' }} render={({ field }) => (
                      <TextField {...field} select label="Giới tính ghép" fullWidth required error={!!errors.genderRequirement} helperText={errors.genderRequirement?.message}>
                        {Object.entries(GENDER_REQUIREMENT).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
                      </TextField>
                    )} />
                  </Grid>
                </>
              )}
              {needRoomCount && (
                <Grid item xs={6} sm={3}>
                  <Controller name="roomCount" control={control} rules={{ required: 'Bắt buộc' }} render={({ field }) => (
                    <TextField {...field} type="number" label="Số phòng" fullWidth required error={!!errors.roomCount} helperText={errors.roomCount?.message} />
                  )} />
                </Grid>
              )}
              <Grid item xs={6} sm={3}>
                <Controller name="toiletCount" control={control} render={({ field }) => (
                  <TextField {...field} type="number" label="Số nhà vệ sinh" fullWidth />
                )} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Controller name="furnitureStatus" control={control} render={({ field }) => (
                  <TextField {...field} select label="Nội thất" fullWidth>
                    {Object.entries(FURNITURE_STATUS).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
                  </TextField>
                )} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Controller name="toiletType" control={control} render={({ field }) => (
                  <TextField {...field} select label="Loại vệ sinh" fullWidth>
                    {Object.entries(TOILET_TYPE).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
                  </TextField>
                )} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Controller name="curfewType" control={control} render={({ field }) => (
                  <TextField {...field} select label="Giờ giấc" fullWidth>
                    {Object.entries(CURFEW).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
                  </TextField>
                )} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Controller name="availableFrom" control={control} render={({ field }) => (
                  <DatePicker label="Cho thuê từ ngày" value={field.value} onChange={field.onChange} format="DD/MM/YYYY" disablePast
                    slotProps={{ textField: { fullWidth: true } }} />
                )} />
              </Grid>
              <Grid item xs={12} sm={8}>
                <Stack direction="row" spacing={2} sx={{ height: '100%' }} alignItems="center">
                  <Controller name="petAllowed" control={control} render={({ field }) => (
                    <FormControlLabel control={<Switch checked={field.value} onChange={field.onChange} />} label="Cho nuôi thú cưng" />
                  )} />
                  <Controller name="parkingAvailable" control={control} render={({ field }) => (
                    <FormControlLabel control={<Switch checked={field.value} onChange={field.onChange} />} label="Có chỗ để xe" />
                  )} />
                </Stack>
              </Grid>
            </Grid>
          )}

          {/* ---- Bước 3: Địa chỉ ---- */}
          {activeStep === 2 && (
            <Grid container spacing={2}>
              <Grid item xs={12} md={7}>
                <Controller name="provinceId" control={control} rules={{ required: true }} render={({ field: pField }) => (
                  <Controller name="districtId" control={control} rules={{ required: true }} render={({ field: dField }) => (
                    <Controller name="wardId" control={control} rules={{ required: true }} render={({ field: wField }) => (
                      <AddressSelector
                        required
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
                <Controller name="addressDetail" control={control}
                  rules={{ required: 'Vui lòng nhập địa chỉ', minLength: { value: 5, message: 'Tối thiểu 5 ký tự' } }}
                  render={({ field }) => (
                    <TextField {...field} label="Số nhà, tên đường" fullWidth required multiline minRows={2}
                      error={!!errors.addressDetail} helperText={errors.addressDetail?.message || 'Ví dụ: 45/12 Đường D2'} />
                  )} />
              </Grid>
            </Grid>
          )}

          {/* ---- Bước 4: Giá & tiện ích ---- */}
          {activeStep === 3 && (
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <Controller name="price" control={control} rules={{ required: 'Vui lòng nhập giá', min: { value: 1, message: 'Giá phải > 0' } }} render={({ field }) => (
                  <TextField {...field} type="number" label="Giá thuê / tháng" fullWidth required error={!!errors.price}
                    helperText={errors.price?.message} InputProps={{ endAdornment: <InputAdornment position="end">đ</InputAdornment> }} />
                )} />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller name="area" control={control} rules={{ required: 'Vui lòng nhập diện tích', min: { value: 1, message: 'Diện tích phải > 0' } }} render={({ field }) => (
                  <TextField {...field} type="number" label="Diện tích" fullWidth required error={!!errors.area}
                    helperText={errors.area?.message} InputProps={{ endAdornment: <InputAdornment position="end">m²</InputAdornment> }} />
                )} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Controller name="depositAmount" control={control} render={({ field }) => (
                  <TextField {...field} type="number" label="Tiền cọc" fullWidth InputProps={{ endAdornment: <InputAdornment position="end">đ</InputAdornment> }} />
                )} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Controller name="electricityPrice" control={control} render={({ field }) => (
                  <TextField {...field} type="number" label="Giá điện" fullWidth InputProps={{ endAdornment: <InputAdornment position="end">đ/kWh</InputAdornment> }} />
                )} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Controller name="waterPrice" control={control} render={({ field }) => (
                  <TextField {...field} type="number" label="Giá nước" fullWidth InputProps={{ endAdornment: <InputAdornment position="end">đ</InputAdornment> }} />
                )} />
              </Grid>

              {/* AI gợi ý giá */}
              <Grid item xs={12}>
                <Card variant="outlined" sx={{ bgcolor: 'action.hover' }}>
                  <CardContent>
                    <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <AutoAwesomeIcon color="primary" fontSize="small" />
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Giá AI đề xuất</Typography>
                      </Stack>
                      <Button size="small" onClick={runPrediction} disabled={predicting}>
                        {predicting ? <CircularProgress size={18} /> : 'Tính lại'}
                      </Button>
                    </Stack>
                    {predicting ? (
                      <Typography variant="body2" color="text.secondary">Đang phân tích thị trường…</Typography>
                    ) : !price ? (
                      <Typography variant="body2" color="text.secondary">Nhập giá & diện tích rồi bấm "Tính lại" để xem gợi ý.</Typography>
                    ) : !price.available ? (
                      <Alert severity="info">{price.message || 'Chưa đủ dữ liệu thị trường để dự đoán giá cho khu vực này.'}</Alert>
                    ) : (
                      <Stack spacing={1}>
                        <Typography variant="h6" color="primary" sx={{ fontWeight: 700 }}>
                          {formatPrice(price.suggestedPrice)}/tháng
                          <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                            (khoảng {formatPrice(price.priceRange?.low)} – {formatPrice(price.priceRange?.high)})
                          </Typography>
                        </Typography>
                        {price.comparison?.deviationFlagged && (
                          <Alert severity="warning">
                            {price.comparison?.verdictMessage || 'Giá bạn nhập lệch khá lớn so với mặt bằng khu vực. Bạn vẫn có thể đăng, nhưng tin có thể ít người liên hệ hơn.'}
                          </Alert>
                        )}
                        {price.disclaimer && <Typography variant="caption" color="text.disabled">{price.disclaimer}</Typography>}
                      </Stack>
                    )}
                  </CardContent>
                </Card>
              </Grid>

              {/* Tiện ích */}
              <Grid item xs={12}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>Tiện ích</Typography>
                <Controller name="amenityIds" control={control} render={({ field }) => (
                  <Stack spacing={1}>
                    {Object.entries(amenityGroups).map(([group, list]) => (
                      <Box key={group}>
                        <Typography variant="caption" color="text.secondary">{group}</Typography>
                        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1, mt: 0.5 }}>
                          {list.map((a) => {
                            const selected = field.value?.includes(a.id);
                            return (
                              <Chip
                                key={a.id} label={a.name} clickable color={selected ? 'primary' : 'default'}
                                variant={selected ? 'filled' : 'outlined'}
                                onClick={() => field.onChange(selected ? field.value.filter((x) => x !== a.id) : [...(field.value || []), a.id])}
                              />
                            );
                          })}
                        </Stack>
                      </Box>
                    ))}
                  </Stack>
                )} />
              </Grid>
            </Grid>
          )}

          {/* ---- Bước 5: Ảnh ---- */}
          {activeStep === 4 && (
            <Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Tải lên {configs['listing.image.min']}–{configs['listing.image.max']} ảnh, mỗi ảnh ≤ {configs['listing.image.max_size_mb']}MB.
              </Typography>
              <ImageUploader
                value={images}
                onChange={setImages}
                maxFiles={configs['listing.image.max']}
                maxSizeMb={configs['listing.image.max_size_mb']}
                primaryId={primaryId}
                onPrimaryChange={setPrimaryId}
              />
            </Box>
          )}

          {/* ---- Bước 6: Xem lại ---- */}
          {activeStep === 5 && (
            <Box>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Controller name="contactName" control={control} rules={{ required: 'Vui lòng nhập tên liên hệ' }} render={({ field }) => (
                    <TextField {...field} label="Tên liên hệ" fullWidth required error={!!errors.contactName} helperText={errors.contactName?.message} />
                  )} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Controller name="contactPhone" control={control} rules={{ required: 'Vui lòng nhập số điện thoại', pattern: { value: /^0\d{9}$/, message: 'Số điện thoại không hợp lệ' } }} render={({ field }) => (
                    <TextField {...field} label="Số điện thoại" fullWidth required error={!!errors.contactPhone} helperText={errors.contactPhone?.message} />
                  )} />
                </Grid>
              </Grid>
              <Divider sx={{ my: 2 }} />
              <ReviewSummary values={getValues()} category={selectedCategory} imageCount={images.length} amenities={amenities} />
            </Box>
          )}

          {/* ---- Điều hướng ---- */}
          <Stack direction="row" justifyContent="space-between" sx={{ mt: 3 }} spacing={1}>
            <Button onClick={handleBack} disabled={activeStep === 0 || submitting}>Quay lại</Button>
            <Stack direction="row" spacing={1}>
              {activeStep === STEPS.length - 1 ? (
                <>
                  <Button variant="outlined" onClick={() => doSubmit(false)} disabled={submitting}>Lưu nháp</Button>
                  <Button variant="contained" onClick={() => doSubmit(true)} disabled={submitting}>
                    {submitting ? 'Đang xử lý…' : 'Gửi duyệt'}
                  </Button>
                </>
              ) : (
                <Button variant="contained" onClick={handleNext}>Tiếp tục</Button>
              )}
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}

const ReviewSummary = ({ values, category, imageCount, amenities }) => {
  const amenityNames = (amenities || []).filter((a) => values.amenityIds?.includes(a.id)).map((a) => a.name);
  const rows = [
    ['Loại tin', category?.name || '—'],
    ['Tiêu đề', values.title],
    ['Giá thuê', values.price ? `${formatCurrency(values.price)}/tháng` : '—'],
    ['Diện tích', values.area ? `${values.area} m²` : '—'],
    ['Tiền cọc', values.depositAmount ? formatCurrency(values.depositAmount) : 'Không'],
    ['Số ảnh', imageCount],
    ['Tiện ích', amenityNames.length ? amenityNames.join(', ') : 'Không'],
    ['Liên hệ', `${values.contactName || '—'} · ${values.contactPhone || '—'}`],
  ];
  return (
    <Stack spacing={1}>
      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Xem lại thông tin</Typography>
      {rows.map(([k, v]) => (
        <Stack key={k} direction="row" justifyContent="space-between" spacing={2}>
          <Typography variant="body2" color="text.secondary">{k}</Typography>
          <Typography variant="body2" sx={{ fontWeight: 600, textAlign: 'right', maxWidth: '70%' }}>{v}</Typography>
        </Stack>
      ))}
    </Stack>
  );
};
