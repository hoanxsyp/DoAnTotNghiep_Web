import { useState } from 'react';
import {
  Box, Stack, TextField, MenuItem, Chip, Avatar, Typography, IconButton,
  InputAdornment, Menu, ListItemIcon, ListItemText, Button,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import VerifiedIcon from '@mui/icons-material/Verified';
import GppBadIcon from '@mui/icons-material/GppBad';
import CancelIcon from '@mui/icons-material/Cancel';
import BlockIcon from '@mui/icons-material/Block';
import adminApi from '@/api/adminApi';
import { VERIFICATION_STATUS_META, metaChip } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { formatDateTime } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

const VERIFY_STATUS_OPTIONS = ['PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED', 'NONE'];

/**
 * Quản lý chủ trọ + xác thực hồ sơ (docs/04 §10.3, API 4.13.6–4.13.10). Quyền LANDLORD_VERIFY:
 * xác thực / hủy xác thực / từ chối yêu cầu / hạn chế đăng tin. Xác thực là thủ công.
 */
const LandlordsPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getLandlords, { initialSort: 'createdAt,desc' });

  const [keyword, setKeyword] = useState('');
  const [vStatus, setVStatus] = useState('');
  const [menuAnchor, setMenuAnchor] = useState(null);
  const [active, setActive] = useState(null);
  const [dialog, setDialog] = useState(null); // 'verify' | 'unverify' | 'reject' | 'restrict'
  const [submitting, setSubmitting] = useState(false);

  const applyFilters = (next = {}) => {
    setParams({
      keyword: keyword.trim() || undefined,
      verificationStatus: vStatus || undefined,
      ...next,
    });
  };

  const openMenu = (e, row) => { setMenuAnchor(e.currentTarget); setActive(row); };

  const run = async (fn, successMsg, payload) => {
    const id = active?.userId ?? active?.id;
    if (!id) {
      notify.error('Không xác định được ID chủ trọ. Vui lòng tải lại danh sách.');
      return;
    }

    setSubmitting(true);
    try {
      await fn(id, payload);
      notify.success(successMsg);
      setDialog(null);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      key: 'landlord', label: 'Chủ trọ', render: (r) => (
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Avatar src={r.avatarUrl} sx={{ width: 36, height: 36 }}>{r.fullName?.charAt(0)}</Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" fontWeight={600} noWrap>{r.businessName || r.fullName}</Typography>
            <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{r.email}</Typography>
          </Box>
        </Stack>
      ),
    },
    { key: 'phone', label: 'Điện thoại', render: (r) => r.phone || '—' },
    { key: 'listingCount', label: 'Số tin', align: 'center', render: (r) => r.listingCount ?? 0 },
    { key: 'trustScore', label: 'Uy tín', align: 'center', render: (r) => r.trustScore ?? '—' },
    {
      key: 'verificationStatus', label: 'Xác thực', render: (r) => {
        const m = metaChip(VERIFICATION_STATUS_META, r.verificationStatus || (r.verified ? 'VERIFIED' : 'NONE'));
        return <Chip size="small" label={m.label} color={m.color} icon={r.verified ? <VerifiedIcon /> : undefined} />;
      },
    },
    { key: 'createdAt', label: 'Ngày tạo', render: (r) => formatDateTime(r.createdAt, 'DD/MM/YYYY') },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <IconButton size="small" onClick={(e) => openMenu(e, r)}><MoreVertIcon fontSize="small" /></IconButton>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader title="Quản lý chủ trọ" subtitle={`${total} chủ trọ`} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          size="small"
          placeholder="Tìm theo tên, email, tên doanh nghiệp"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && applyFilters()}
          sx={{ flexGrow: 1, minWidth: 240 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        />
        <TextField select size="small" label="Trạng thái xác thực" value={vStatus} onChange={(e) => { setVStatus(e.target.value); applyFilters({ verificationStatus: e.target.value || undefined }); }} sx={{ minWidth: 200 }}>
          <MenuItem value="">Tất cả</MenuItem>
          {VERIFY_STATUS_OPTIONS.map((s) => <MenuItem key={s} value={s}>{VERIFICATION_STATUS_META[s].label}</MenuItem>)}
        </TextField>
        <Button variant="contained" onClick={() => applyFilters()}>Tìm</Button>
      </Stack>

      <AdminDataTable
        columns={columns}
        rows={items}
        rowKey={(row) => row.userId}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Không có chủ trọ phù hợp"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={() => setMenuAnchor(null)}>
        {!active?.verified && (
          <MenuItem onClick={() => { setMenuAnchor(null); setDialog('verify'); }}>
            <ListItemIcon><VerifiedIcon fontSize="small" color="success" /></ListItemIcon>
            <ListItemText>Xác thực chủ trọ</ListItemText>
          </MenuItem>
        )}
        {active?.verified && (
          <MenuItem onClick={() => { setMenuAnchor(null); setDialog('unverify'); }}>
            <ListItemIcon><GppBadIcon fontSize="small" color="warning" /></ListItemIcon>
            <ListItemText>Hủy xác thực</ListItemText>
          </MenuItem>
        )}
        <MenuItem onClick={() => { setMenuAnchor(null); setDialog('reject'); }}>
          <ListItemIcon><CancelIcon fontSize="small" color="error" /></ListItemIcon>
          <ListItemText>Từ chối yêu cầu xác thực</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { setMenuAnchor(null); setDialog('restrict'); }}>
          <ListItemIcon><BlockIcon fontSize="small" color="error" /></ListItemIcon>
          <ListItemText>Hạn chế đăng tin</ListItemText>
        </MenuItem>
      </Menu>

      <ConfirmDialog
        open={dialog === 'verify'}
        title="Xác thực chủ trọ"
        message={`Xác nhận hồ sơ của "${active?.businessName || active?.fullName}" là hợp lệ?`}
        confirmText="Xác thực"
        confirmColor="success"
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={() => run(adminApi.verifyLandlord, 'Đã xác thực chủ trọ', {})}
      />
      <ConfirmDialog
        open={dialog === 'unverify'}
        title="Hủy xác thực"
        message="Gỡ trạng thái đã xác thực của chủ trọ này?"
        confirmText="Hủy xác thực"
        confirmColor="warning"
        requireReason
        reasonLabel="Lý do"
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={({ reason }) => run(adminApi.unverifyLandlord, 'Đã hủy xác thực', { reason })}
      />
      <ConfirmDialog
        open={dialog === 'reject'}
        title="Từ chối yêu cầu xác thực"
        message="Từ chối yêu cầu xác thực. Chủ trọ có thể nộp lại hồ sơ."
        confirmText="Từ chối"
        confirmColor="error"
        requireReason
        reasonLabel="Lý do từ chối"
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={({ reason }) => run(adminApi.rejectLandlordVerification, 'Đã từ chối yêu cầu', { reason })}
      />
      <ConfirmDialog
        open={dialog === 'restrict'}
        title="Hạn chế đăng tin"
        message="Tạm thời không cho chủ trọ này đăng tin mới."
        confirmText="Hạn chế"
        confirmColor="error"
        requireReason
        reasonLabel="Lý do hạn chế"
        loading={submitting}
        onClose={() => setDialog(null)}
        onConfirm={({ reason }) => run(adminApi.restrictLandlordPosting, 'Đã hạn chế đăng tin', { reason })}
      />
    </Box>
  );
};

export default LandlordsPage;
