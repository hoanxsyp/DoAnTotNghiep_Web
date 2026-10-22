import { useEffect, useMemo, useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import {
  Box, Card, CardContent, Stack, Typography, Button, Divider, CircularProgress,
  Alert,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import paymentApi from '@/api/paymentApi';
import { formatCurrency, formatDateTime } from '@/utils/format';

/**
 * Trang kết quả thanh toán. Với gateway sandbox, URL chứa token đã ký và trang sẽ gửi callback
 * mô phỏng về backend trước khi đọc lại trạng thái giao dịch từ nguồn dữ liệu chính thức.
 */
const pick = (sp, keys) => { for (const k of keys) { const v = sp.get(k); if (v != null) return v; } return null; };

const PaymentResultPage = () => {
  const [sp] = useSearchParams();
  const paymentId = pick(sp, ['paymentId', 'id']);
  const txn = pick(sp, ['transactionCode', 'tc', 'txn', 'vnp_TxnRef']);
  const amountParam = pick(sp, ['amount', 'vnp_Amount']);
  const signature = pick(sp, ['sig', 'signature']);
  const statusParam = (pick(sp, ['status', 'resultCode', 'vnp_ResponseCode']) || '').toUpperCase();

  const isSandboxReturn = !paymentId && !!txn && !!amountParam && !!signature;
  const [loading, setLoading] = useState(!!paymentId || isSandboxReturn);
  const [payment, setPayment] = useState(null);
  const [processingError, setProcessingError] = useState('');

  useEffect(() => {
    let alive = true;

    const loadPayment = async () => {
      try {
        let resolvedPaymentId = paymentId;

        if (isSandboxReturn) {
          const now = new Date();
          const callback = await paymentApi.processCallback({
            transactionCode: txn,
            gatewayTransactionId: `SANDBOX-${txn}`,
            amount: amountParam,
            status: 'SUCCESS',
            responseCode: '00',
            responseMessage: 'Thanh toán Sandbox thành công',
            paidAt: now.toISOString(),
            timestamp: Math.floor(now.getTime() / 1000),
            nonce: window.crypto?.randomUUID?.()
              || `${Date.now()}-${Math.random().toString(16).slice(2)}`,
            signature,
          });
          resolvedPaymentId = callback?.paymentId;
        }

        if (!resolvedPaymentId) {
          throw new Error('Không xác định được giao dịch thanh toán');
        }

        const detail = await paymentApi.getPayment(resolvedPaymentId);
        if (alive) setPayment(detail);
      } catch (error) {
        if (alive) {
          const message = error?.response?.data?.message
            || error?.message
            || 'Không thể xác nhận kết quả thanh toán';
          setProcessingError(message);
        }
      } finally {
        if (alive) setLoading(false);
      }
    };

    if (paymentId || isSandboxReturn) {
      loadPayment();
    } else {
      setLoading(false);
    }

    return () => { alive = false; };
  }, [amountParam, isSandboxReturn, paymentId, signature, txn]);

  const success = useMemo(() => {
    if (payment) return payment.status === 'SUCCESS';
    return ['SUCCESS', 'SUCCEEDED', '00', 'OK', 'TRUE', '1'].includes(statusParam);
  }, [payment, statusParam]);

  const amount = payment?.amount ?? (amountParam ? Number(amountParam) : null);
  const code = payment?.transactionCode ?? txn;

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}><CircularProgress /></Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
      <Card sx={{ maxWidth: 480, width: '100%' }}>
        <CardContent sx={{ textAlign: 'center', p: 4 }}>
          {success ? (
            <CheckCircleIcon color="success" sx={{ fontSize: 72 }} />
          ) : (
            <CancelIcon color="error" sx={{ fontSize: 72 }} />
          )}
          <Typography variant="h5" sx={{ fontWeight: 700, mt: 2 }}>
            {success ? 'Thanh toán thành công' : 'Thanh toán chưa hoàn tất'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {success
              ? 'Gói dịch vụ đã được kích hoạt cho tin đăng của bạn.'
              : 'Giao dịch không thành công hoặc đã bị hủy. Bạn có thể thử lại.'}
          </Typography>

          {processingError && (
            <Alert severity="error" sx={{ mt: 2, textAlign: 'left' }}>
              {processingError}
            </Alert>
          )}

          <Divider sx={{ my: 3 }} />
          <Stack spacing={1} sx={{ textAlign: 'left' }}>
            {code && (
              <Row label="Mã giao dịch" value={<Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{code}</Typography>} />
            )}
            {payment?.packageName && <Row label="Gói dịch vụ" value={payment.packageName} />}
            {amount != null && <Row label="Số tiền" value={formatCurrency(amount)} />}
            {payment?.paidAt && <Row label="Thời gian" value={formatDateTime(payment.paidAt)} />}
            {payment?.subscription?.endAt && <Row label="Hiệu lực đến" value={formatDateTime(payment.subscription.endAt)} />}
          </Stack>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 4 }} justifyContent="center">
            <Button variant="outlined" component={RouterLink} to="/quan-ly/thanh-toan">Xem lịch sử thanh toán</Button>
            <Button variant="contained" component={RouterLink} to={success ? '/quan-ly/tin-dang' : '/quan-ly/goi-dich-vu'}>
              {success ? 'Về danh sách tin' : 'Thử lại'}
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};

const Row = ({ label, value }) => (
  <Stack direction="row" justifyContent="space-between" spacing={2}>
    <Typography variant="body2" color="text.secondary">{label}</Typography>
    <Box sx={{ fontWeight: 600, textAlign: 'right' }}>
      {typeof value === 'string' ? <Typography variant="body2" sx={{ fontWeight: 600 }}>{value}</Typography> : value}
    </Box>
  </Stack>
);

export default PaymentResultPage;
