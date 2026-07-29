import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Box, Grid, Card, CardContent, Stack, Typography, Button, Chip, List, ListItem, ListItemIcon,
  ListItemText, Divider, Dialog, DialogTitle, DialogContent, DialogActions, TextField, MenuItem,
  Alert, InputAdornment,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import CampaignIcon from '@mui/icons-material/Campaign';
import paymentApi from '@/api/paymentApi';
import listingApi from '@/api/listingApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import notify from '@/utils/toast';
import { formatCurrency, formatDate } from '@/utils/format';

const RETURN_URL = `${window.location.origin}/quan-ly/thanh-toan/ket-qua`;
const PAYMENT_METHODS = [
  { value: 'SANDBOX', label: 'Thanh toán thử nghiệm (Sandbox)' },
  { value: 'VNPAY', label: 'VNPay' },
  { value: 'MOMO', label: 'MoMo' },
  { value: 'BANK_TRANSFER', label: 'Chuyển khoản ngân hàng' },
];

const newIdempotencyKey = () =>
  (window.crypto?.randomUUID?.() || `key-${Date.now()}-${Math.random().toString(16).slice(2)}`);

const PackagesPage = () => {
  const [searchParams] = useSearchParams();
  const [packages, setPackages] = useState([]);
  const [subs, setSubs] = useState([]);
  const [listings, setListings] = useState([]);
  const [loading, setLoading] = useState(true);

  const [buyPkg, setBuyPkg] = useState(null);
  const [form, setForm] = useState({ listingId: '', paymentMethod: 'SANDBOX', couponCode: '' });
  const [coupon, setCoupon] = useState(null);
  const [validating, setValidating] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [pkgs, subscriptions, mine] = await Promise.all([
        paymentApi.getPackages({ activeOnly: true }).catch(() => []),
        paymentApi.getMySubscriptions({ status: 'ACTIVE', page: 0, size: 20 }).catch(() => ({ items: [] })),
        listingApi.getMyListings({ status: 'ACTIVE', page: 0, size: 100 }).catch(() => ({ items: [] })),
      ]);
      setPackages(Array.isArray(pkgs) ? pkgs : pkgs?.items || []);
      setSubs(subscriptions?.items || []);
      setListings(mine?.items || []);
    } catch (e) {
      notify.apiError(e, 'Không tải được gói dịch vụ');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const openBuy = (pkg) => {
    setBuyPkg(pkg);
    setCoupon(null);
    setForm({ listingId: searchParams.get('listingId') || '', paymentMethod: 'SANDBOX', couponCode: '' });
  };

  const validateCoupon = async () => {
    if (!form.couponCode.trim()) return;
    setValidating(true);
    try {
      const res = await paymentApi.validateCoupon({ code: form.couponCode.trim().toUpperCase(), packageId: buyPkg.id });
      setCoupon(res);
      notify.success('Áp dụng mã khuyến mãi thành công');
    } catch (e) {
      setCoupon(null);
      notify.apiError(e, 'Mã khuyến mãi không hợp lệ');
    } finally {
      setValidating(false);
    }
  };

  const handleBuy = async () => {
    if (!form.listingId) { notify.warning('Vui lòng chọn tin cần đẩy'); return; }
    setSubmitting(true);
    try {
      const payload = {
        listingId: Number(form.listingId),
        packageId: buyPkg.id,
        paymentMethod: form.paymentMethod,
        returnUrl: RETURN_URL,
      };
      if (coupon?.valid || form.couponCode) payload.couponCode = form.couponCode.trim().toUpperCase();
      const res = await paymentApi.createPayment(payload, { headers: { 'Idempotency-Key': newIdempotencyKey() } });
      if (res?.paymentUrl) {
        window.location.href = res.paymentUrl;
      } else {
        notify.success('Đã tạo đơn thanh toán');
        setBuyPkg(null);
        load();
      }
    } catch (e) {
      notify.apiError(e, 'Tạo đơn thanh toán thất bại');
    } finally {
      setSubmitting(false);
    }
  };

  const finalAmount = coupon?.valid ? coupon.finalAmount : buyPkg?.price;

  return (
    <Box>
      <PageHeader title="Gói dịch vụ" subtitle="Đẩy tin lên đầu kết quả tìm kiếm để tiếp cận nhiều người thuê hơn" />

      {/* Gói đang hiệu lực */}
      {subs.length > 0 && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Gói đang hiệu lực</Typography>
            <Stack spacing={1}>
              {subs.map((s) => (
                <Stack key={s.id} direction="row" justifyContent="space-between" alignItems="center" sx={{ flexWrap: 'wrap' }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip size="small" color="secondary" icon={<CampaignIcon />} label={s.badgeLabel || s.packageName} />
                    <Typography variant="body2" noWrap sx={{ maxWidth: 280 }}>{s.listingTitle}</Typography>
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    Hết hạn {formatDate(s.endAt)} · còn {s.daysRemaining} ngày
                  </Typography>
                </Stack>
              ))}
            </Stack>
          </CardContent>
        </Card>
      )}

      {loading ? (
        <Grid container spacing={2}>{Array.from({ length: 3 }).map((_, i) => <Grid item xs={12} md={4} key={i}><Card sx={{ p: 2 }}><LoadingSkeleton variant="form" /></Card></Grid>)}</Grid>
      ) : packages.length === 0 ? (
        <EmptyState title="Chưa có gói dịch vụ" description="Hiện chưa có gói dịch vụ nào khả dụng." />
      ) : (
        <Grid container spacing={2}>
          {packages.map((p) => (
            <Grid item xs={12} sm={6} md={4} key={p.id}>
              <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column', border: p.highlighted ? '2px solid' : undefined, borderColor: p.highlighted ? 'primary.main' : undefined }}>
                <CardContent sx={{ flex: 1 }}>
                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>{p.name}</Typography>
                    {p.badgeLabel && <Chip size="small" color="secondary" label={p.badgeLabel} />}
                  </Stack>
                  <Typography variant="h5" color="primary" sx={{ fontWeight: 700, mt: 1 }}>{formatCurrency(p.price)}</Typography>
                  <Typography variant="caption" color="text.secondary">/ {p.durationDays} ngày</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{p.description}</Typography>
                  <Divider sx={{ my: 1.5 }} />
                  <List dense disablePadding>
                    {(p.features || []).map((f) => (
                      <ListItem key={f} disableGutters sx={{ py: 0.25 }}>
                        <ListItemIcon sx={{ minWidth: 30 }}><CheckCircleIcon color="success" fontSize="small" /></ListItemIcon>
                        <ListItemText primaryTypographyProps={{ variant: 'body2' }} primary={f} />
                      </ListItem>
                    ))}
                  </List>
                </CardContent>
                <Box sx={{ p: 2, pt: 0 }}>
                  <Button fullWidth variant="contained" startIcon={<CampaignIcon />} onClick={() => openBuy(p)}>Mua gói</Button>
                </Box>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {/* Dialog mua gói */}
      <Dialog open={!!buyPkg} onClose={() => !submitting && setBuyPkg(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Mua gói: {buyPkg?.name}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {listings.length === 0 ? (
              <Alert severity="info">Bạn chưa có tin đang hiển thị để đẩy. Hãy đăng tin và chờ được duyệt trước.</Alert>
            ) : (
              <TextField select label="Chọn tin cần đẩy" fullWidth value={form.listingId}
                onChange={(e) => setForm((f) => ({ ...f, listingId: e.target.value }))}>
                {listings.map((l) => <MenuItem key={l.id} value={l.id}>{l.title}</MenuItem>)}
              </TextField>
            )}
            <TextField select label="Phương thức thanh toán" fullWidth value={form.paymentMethod}
              onChange={(e) => setForm((f) => ({ ...f, paymentMethod: e.target.value }))}>
              {PAYMENT_METHODS.map((m) => <MenuItem key={m.value} value={m.value}>{m.label}</MenuItem>)}
            </TextField>
            <Stack direction="row" spacing={1}>
              <TextField label="Mã khuyến mãi" fullWidth value={form.couponCode}
                onChange={(e) => { setForm((f) => ({ ...f, couponCode: e.target.value })); setCoupon(null); }}
                InputProps={{ startAdornment: <InputAdornment position="start"><LocalOfferIcon fontSize="small" /></InputAdornment> }} />
              <Button onClick={validateCoupon} disabled={validating || !form.couponCode.trim()}>Áp dụng</Button>
            </Stack>
            {coupon?.valid && <Alert severity="success">{coupon.description} — giảm {formatCurrency(coupon.discountAmount)}</Alert>}

            <Divider />
            <Stack direction="row" justifyContent="space-between">
              <Typography variant="subtitle1">Tổng thanh toán</Typography>
              <Typography variant="h6" color="primary" sx={{ fontWeight: 700 }}>{formatCurrency(finalAmount)}</Typography>
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setBuyPkg(null)} disabled={submitting} color="inherit">Hủy</Button>
          <Button variant="contained" onClick={handleBuy} disabled={submitting || listings.length === 0}>
            {submitting ? 'Đang xử lý…' : 'Thanh toán'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PackagesPage;
