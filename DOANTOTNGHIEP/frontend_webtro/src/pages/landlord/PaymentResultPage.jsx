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
 * Trang kết quả thanh toán. Với gateway sandbox, người dùng chủ động chọn kết quả mô phỏng;
 * chỉ lựa chọn thành công mới gửi callback SUCCESS và kích hoạt gói.
 */
const pick = (sp, keys) => { for (const k of keys) { const v = sp.get(k); if (v != null) return v; } return null; };

const PaymentResultPage = () => {
  const [sp] = useSearchParams();
  const paymentId = pick(sp, ['paymentId', 'id']);
  const txn = pick(sp, ['transactionCode', 'tc', 'txn', 'vnp_TxnRef']);
  const amountParam = pick(sp, ['amount', 'vnp_Amount']);
  const signature = pick(sp, ['sig', 'signature']);
  const statusParam = (pick(sp, ['status', 'resultCode', 'vnp_ResponseCode']) || '').toUpperCase();

  const isSandboxReturn = !!txn && !!amountParam && !!signature;
  const [loading, setLoading] = useState(!!paymentId && !isSandboxReturn);
  const [submitting, setSubmitting] = useState(false);
  const [payment, setPayment] = useState(null);
  const [processingError, setProcessingError] = useState('');

  useEffect(() => {
    let alive = true;

    const loadPayment = async () => {
      try {
        if (!paymentId) {
          throw new Error('Không xác định được giao dịch thanh toán');
        }
        const detail = await paymentApi.getPayment(paymentId);
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

    if (paymentId && !isSandboxReturn) {
      loadPayment();
    } else {
      setLoading(false);
    }

    return () => { alive = false; };
  }, [amountParam, isSandboxReturn, paymentId, signature, txn]);

  const finishSandbox = async (status) => {
    setSubmitting(true);
    setProcessingError('');
    try {
      const now = new Date();
      const callback = await paymentApi.processCallback({
        transactionCode: txn,
        gatewayTransactionId: `SANDBOX-${txn}`,
        amount: amountParam,
        status,
        responseCode: status === 'SUCCESS' ? '00' : '99',
        responseMessage: status === 'SUCCESS'
          ? 'Thanh toán Sandbox thành công'
          : 'Thanh toán Sandbox thất bại theo lựa chọn mô phỏng',
        paidAt: status === 'SUCCESS' ? now.toISOString() : null,
        timestamp: Math.floor(now.getTime() / 1000),
        nonce: window.crypto?.randomUUID?.()
          || `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        signature,
      });
      const resolvedPaymentId = callback?.paymentId || paymentId;
      if (!resolvedPaymentId) throw new Error('Không xác định được giao dịch thanh toán');
      setPayment(await paymentApi.getPayment(resolvedPaymentId));
    } catch (error) {
      setProcessingError(error?.response?.data?.message
        || error?.message
        || 'Không thể xác nhận kết quả thanh toán');
    } finally {
      setSubmitting(false);
    }
  };

  const cancelSandbox = async () => {
    if (!paymentId) {
      setProcessingError('Không xác định được giao dịch để hủy');
      return;
    }
    setSubmitting(true);
    setProcessingError('');
    try {
      setPayment(await paymentApi.cancelPayment(paymentId));
    } catch (error) {
      setProcessingError(error?.response?.data?.message || 'Không thể hủy giao dịch');
    } finally {
      setSubmitting(false);
    }
  };

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

  if (isSandboxReturn && !payment) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <Card sx={{ maxWidth: 520, width: '100%' }}>
          <CardContent sx={{ p: 4 }}>
            <Typography variant="h5" sx={{ fontWeight: 700, textAlign: 'center' }}>
              Thanh toán Sandbox
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1, textAlign: 'center' }}>
              Đây là môi trường thử nghiệm. Hãy chọn kết quả muốn mô phỏng cho giao dịch này.
            </Typography>
            {processingError && <Alert severity="error" sx={{ mt: 2 }}>{processingError}</Alert>}
            <Divider sx={{ my: 3 }} />
            <Stack spacing={1}>
              <Row label="Mã giao dịch" value={<Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{txn}</Typography>} />
              <Row label="Số tiền" value={formatCurrency(Number(amountParam))} />
            </Stack>
            <Stack spacing={1.5} sx={{ mt: 4 }}>
              <Button variant="contained" color="success" disabled={submitting} onClick={() => finishSandbox('SUCCESS')}>
                Xác nhận thanh toán thành công
              </Button>
              <Button variant="outlined" color="error" disabled={submitting} onClick={() => finishSandbox('FAILED')}>
                Mô phỏng thanh toán thất bại
              </Button>
              <Button color="inherit" disabled={submitting || !paymentId} onClick={cancelSandbox}>
                Hủy giao dịch
              </Button>
            </Stack>
          </CardContent>
        </Card>
      </Box>
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
