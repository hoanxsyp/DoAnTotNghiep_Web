import { useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { selectCurrentUser, selectIsAuthenticated } from '@/redux/authSlice';
import { fetchUnreadMessages, resetChat, setUnreadMessages } from '@/redux/chatSlice';
import chatSocket from '@/services/chatSocketService';
import { notify } from '@/utils/toast';

// Đang mở đúng hội thoại này ở trang tin nhắn thì không cần toast (đã thấy tin trong khung chat).
const isViewingConversation = (conversationId) => {
  const { pathname, search } = window.location;
  return pathname.endsWith('/tin-nhan')
    && new URLSearchParams(search).get('conversationId') === String(conversationId);
};

/**
 * Giữ kết nối chat realtime suốt phiên đăng nhập (gắn một lần ở gốc ứng dụng):
 * - Đăng nhập -> mở kết nối + lấy số tin chưa đọc ban đầu; đăng xuất -> đóng kết nối.
 * - CONVERSATION_UPDATED -> cập nhật badge "Tin nhắn" (header + menu), toast khi có tin mới ở hội
 *   thoại không đang mở.
 * - Kết nối lại sau khi rớt mạng -> tải lại số chưa đọc (bù sự kiện bị lỡ).
 */
const useChatRealtime = () => {
  const dispatch = useDispatch();
  const isAuthenticated = useSelector(selectIsAuthenticated);
  const userId = useSelector(selectCurrentUser)?.id;
  const unreadByConversation = useRef(new Map());

  useEffect(() => {
    if (!isAuthenticated || !userId) {
      chatSocket.disconnect();
      dispatch(resetChat());
      unreadByConversation.current.clear();
      return undefined;
    }

    dispatch(fetchUnreadMessages());
    chatSocket.connect(userId);

    const offUpdate = chatSocket.onConversationUpdate((conversation, totalUnread) => {
      dispatch(setUnreadMessages(totalUnread));

      const prev = unreadByConversation.current.get(conversation.id) ?? 0;
      const now = conversation.unreadCount ?? 0;
      unreadByConversation.current.set(conversation.id, now);
      if (now > prev && conversation.lastMessage && !isViewingConversation(conversation.id)) {
        const who = conversation.partner?.fullName || 'Tin nhắn mới';
        notify.info(`${who}: ${conversation.lastMessage.content}`);
      }
    });
    const offReconnect = chatSocket.onReconnect(() => {
      dispatch(fetchUnreadMessages());
    });

    return () => {
      offUpdate();
      offReconnect();
    };
  }, [isAuthenticated, userId, dispatch]);
};

export default useChatRealtime;
