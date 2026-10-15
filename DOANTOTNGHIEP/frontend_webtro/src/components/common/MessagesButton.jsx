import { useSelector } from 'react-redux';
import { Link as RouterLink } from 'react-router-dom';
import { IconButton, Badge, Tooltip } from '@mui/material';
import ChatIcon from '@mui/icons-material/Chat';
import useAuth from '@/hooks/useAuth';
import { selectUnreadMessages } from '@/redux/chatSlice';
import { ROLES } from '@/constants';

/**
 * Nút tin nhắn trên header (cạnh chuông thông báo): số đỏ = tổng tin nhắn chưa đọc, cập nhật realtime
 * qua useChatRealtime. Bấm mở trang tin nhắn theo vai trò. Kiểm duyệt viên không dùng chat -> ẩn.
 */
const MessagesButton = () => {
  const { role } = useAuth();
  const unread = useSelector(selectUnreadMessages);

  if (![ROLES.TENANT, ROLES.LANDLORD, ROLES.ADMIN].includes(role)) return null;
  const to = role === ROLES.TENANT ? '/tai-khoan/tin-nhan' : '/quan-ly/tin-nhan';

  return (
    <Tooltip title="Tin nhắn">
      <IconButton color="inherit" component={RouterLink} to={to} aria-label="Tin nhắn">
        <Badge badgeContent={unread} color="error" max={99}>
          <ChatIcon />
        </Badge>
      </IconButton>
    </Tooltip>
  );
};

export default MessagesButton;
