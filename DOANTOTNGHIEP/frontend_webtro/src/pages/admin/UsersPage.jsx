import { useState } from 'react';
import {
  Box, Stack, TextField, MenuItem, Chip, Avatar, Typography, IconButton,
  InputAdornment, Menu, ListItemIcon, ListItemText, Dialog,
  DialogTitle, DialogContent, DialogActions, Button, RadioGroup, Radio, FormControlLabel,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import LockIcon from '@mui/icons-material/Lock';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import adminApi from '@/api/adminApi';
import { ROLES } from '@/constants';
import { ROLE_META, USER_STATUS_META, metaChip } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import { formatDateTime } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

const STATUS_OPTIONS = ['ACTIVE', 'PENDING_VERIFY', 'LOCKED', 'DELETED'];
const ROLE_LIST = [ROLES.TENANT, ROLES.LANDLORD, ROLES.MODERATOR, ROLES.ADMIN];

/**
 * Quản lý người dùng (docs/04 §10.2, API 4.13.x). Tìm/lọc theo tên-email-SĐT, vai trò, trạng thái;
 * khóa/mở khóa (USER_MANAGE); đổi vai trò (USER_ROLE_ASSIGN) — backend kiểm tra quyền thật.
 *
 * Mỗi người dùng có ĐÚNG MỘT vai trò nên hộp thoại đổi vai trò dùng RadioGroup (chọn một), không
 * phải Checkbox (chọn nhiều).
 */
const UsersPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getUsers, { initialSort: 'createdAt,desc' });

  const [keyword, setKeyword] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');

  const [menuAnchor, setMenuAnchor] = useState(null);
  const [activeUser, setActiveUser] = useState(null);
  const [lockDialog, setLockDialog] = useState(false);
  const [roleDialog, setRoleDialog] = useState(false);
  const [roleSelection, setRoleSelection] = useState('');
  const [roleReason, setRoleReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const applyFilters = (next = {}) => {
    setParams({
      keyword: keyword.trim() || undefined,
      role: role || undefined,
      status: status || undefined,
      ...next,
    });
  };

  const openMenu = (e, user) => { setMenuAnchor(e.currentTarget); setActiveUser(user); };
  const closeMenu = () => setMenuAnchor(null);

  const handleLock = async ({ reason }) => {
    setSubmitting(true);
    try {
      await adminApi.lockUser(activeUser.id, { reason });
      notify.success('Đã khóa tài khoản');
      setLockDialog(false);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleUnlock = async (user) => {
    try {
      await adminApi.unlockUser(user.id);
      notify.success('Đã mở khóa tài khoản');
      reload();
    } catch (err) {
      notify.apiError(err);
    }
  };

  const handleSaveRole = async () => {
    setSubmitting(true);
    try {
      await adminApi.updateUserRole(activeUser.id, { role: roleSelection, reason: roleReason.trim() });
      notify.success('Đã cập nhật vai trò. Người dùng cần đăng nhập lại.');
      setRoleDialog(false);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      key: 'user', label: 'Người dùng', render: (r) => (
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Avatar src={r.avatarUrl} sx={{ width: 36, height: 36 }}>{r.fullName?.charAt(0)}</Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" fontWeight={600} noWrap>{r.fullName}</Typography>
            <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{r.email}</Typography>
          </Box>
        </Stack>
      ),
    },
    { key: 'phone', label: 'Điện thoại', render: (r) => r.phone || '—' },
    {
      key: 'role', label: 'Vai trò', render: (r) => {
        if (!r.role) return '—';
        const m = metaChip(ROLE_META, r.role);
        return <Chip size="small" label={m.label} color={m.color} variant="outlined" />;
      },
    },
    { key: 'trustScore', label: 'Uy tín', align: 'center', render: (r) => r.trustScore ?? '—' },
    {
      key: 'status', label: 'Trạng thái', render: (r) => {
        const m = metaChip(USER_STATUS_META, r.status);
        return <Chip size="small" label={r.statusLabel || m.label} color={m.color} />;
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
      <AdminPageHeader title="Quản lý người dùng" subtitle={`${total} tài khoản`} />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          size="small"
          placeholder="Tìm theo tên, email, số điện thoại"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && applyFilters()}
          sx={{ flexGrow: 1, minWidth: 240 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        />
        <TextField select size="small" label="Vai trò" value={role} onChange={(e) => { setRole(e.target.value); applyFilters({ role: e.target.value || undefined }); }} sx={{ minWidth: 160 }}>
          <MenuItem value="">Tất cả vai trò</MenuItem>
          {ROLE_LIST.map((rl) => <MenuItem key={rl} value={rl}>{ROLE_META[rl].label}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Trạng thái" value={status} onChange={(e) => { setStatus(e.target.value); applyFilters({ status: e.target.value || undefined }); }} sx={{ minWidth: 160 }}>
          <MenuItem value="">Tất cả trạng thái</MenuItem>
          {STATUS_OPTIONS.map((s) => <MenuItem key={s} value={s}>{USER_STATUS_META[s].label}</MenuItem>)}
        </TextField>
        <Button variant="contained" onClick={() => applyFilters()}>Tìm</Button>
      </Stack>

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Không tìm thấy người dùng phù hợp"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={closeMenu}>
        {activeUser?.status === 'LOCKED' ? (
          <MenuItem onClick={() => { closeMenu(); handleUnlock(activeUser); }}>
            <ListItemIcon><LockOpenIcon fontSize="small" /></ListItemIcon>
            <ListItemText>Mở khóa</ListItemText>
          </MenuItem>
        ) : (
          <MenuItem onClick={() => { closeMenu(); setLockDialog(true); }}>
            <ListItemIcon><LockIcon fontSize="small" color="error" /></ListItemIcon>
            <ListItemText>Khóa tài khoản</ListItemText>
          </MenuItem>
        )}
        <MenuItem onClick={() => {
          closeMenu();
          setRoleSelection(activeUser?.role || '');
          setRoleReason('');
          setRoleDialog(true);
        }}>
          <ListItemIcon><ManageAccountsIcon fontSize="small" /></ListItemIcon>
          <ListItemText>Đổi vai trò</ListItemText>
        </MenuItem>
      </Menu>

      <ConfirmDialog
        open={lockDialog}
        title="Khóa tài khoản"
        message={`Khóa tài khoản "${activeUser?.fullName}"? Người dùng sẽ không đăng nhập được.`}
        confirmText="Khóa"
        confirmColor="error"
        requireReason
        reasonLabel="Lý do khóa"
        loading={submitting}
        onClose={() => setLockDialog(false)}
        onConfirm={handleLock}
      />

      <Dialog open={roleDialog} onClose={() => setRoleDialog(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Đổi vai trò</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {activeUser?.fullName}. Mỗi người dùng chỉ có một vai trò; đổi vai trò sẽ thu hồi toàn bộ
            phiên đăng nhập của người dùng.
          </Typography>
          <RadioGroup value={roleSelection} onChange={(e) => setRoleSelection(e.target.value)}>
            {ROLE_LIST.map((rl) => (
              <FormControlLabel key={rl} value={rl} control={<Radio />} label={ROLE_META[rl].label} />
            ))}
          </RadioGroup>
          <TextField
            fullWidth
            multiline
            minRows={2}
            size="small"
            sx={{ mt: 1 }}
            label="Lý do thay đổi"
            placeholder="Tối thiểu 10 ký tự — sẽ được ghi vào nhật ký kiểm toán"
            value={roleReason}
            onChange={(e) => setRoleReason(e.target.value)}
            error={roleReason.length > 0 && roleReason.trim().length < 10}
            helperText={roleReason.length > 0 && roleReason.trim().length < 10
              ? 'Lý do phải từ 10 đến 500 ký tự'
              : ' '}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRoleDialog(false)} disabled={submitting}>Hủy</Button>
          <Button
            variant="contained"
            onClick={handleSaveRole}
            disabled={submitting
              || !roleSelection
              || roleReason.trim().length < 10
              || roleReason.trim().length > 500}
          >
            Lưu
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default UsersPage;
