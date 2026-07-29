import { useState } from 'react';
import {
  Box, Stack, TextField, MenuItem, Chip, Avatar, Typography, IconButton,
  InputAdornment, Menu, ListItemIcon, ListItemText, Checkbox, Dialog,
  DialogTitle, DialogContent, DialogActions, Button, FormGroup, FormControlLabel,
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
 * khóa/mở khóa (USER_MANAGE); phân quyền vai trò (USER_ROLE_ASSIGN). PermissionRoute chặn ở route.
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
  const [roleSelection, setRoleSelection] = useState([]);
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

  const handleSaveRoles = async () => {
    setSubmitting(true);
    try {
      await adminApi.updateUserRoles(activeUser.id, { roles: roleSelection });
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
      key: 'roles', label: 'Vai trò', render: (r) => (
        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
          {(r.roles || []).map((role) => {
            const m = metaChip(ROLE_META, role);
            return <Chip key={role} size="small" label={m.label} color={m.color} variant="outlined" />;
          })}
        </Stack>
      ),
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
        <MenuItem onClick={() => { closeMenu(); setRoleSelection(activeUser?.roles || []); setRoleDialog(true); }}>
          <ListItemIcon><ManageAccountsIcon fontSize="small" /></ListItemIcon>
          <ListItemText>Phân quyền vai trò</ListItemText>
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
        <DialogTitle>Phân quyền vai trò</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {activeUser?.fullName}. Đổi vai trò sẽ thu hồi toàn bộ phiên đăng nhập của người dùng.
          </Typography>
          <FormGroup>
            {ROLE_LIST.map((rl) => (
              <FormControlLabel
                key={rl}
                control={
                  <Checkbox
                    checked={roleSelection.includes(rl)}
                    onChange={(e) => setRoleSelection((prev) => e.target.checked ? [...prev, rl] : prev.filter((x) => x !== rl))}
                  />
                }
                label={ROLE_META[rl].label}
              />
            ))}
          </FormGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRoleDialog(false)} disabled={submitting}>Hủy</Button>
          <Button variant="contained" onClick={handleSaveRoles} disabled={submitting || !roleSelection.length}>Lưu</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default UsersPage;
