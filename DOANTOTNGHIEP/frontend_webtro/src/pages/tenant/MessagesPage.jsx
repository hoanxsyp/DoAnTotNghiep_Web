import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useSelector } from 'react-redux';
import {
  Box, Card, Stack, List, ListItemButton, ListItemAvatar, ListItemText, Avatar, Badge,
  Typography, Divider, TextField, IconButton, Tooltip, useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import SendIcon from '@mui/icons-material/Send';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import contactApi from '@/api/contactApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import notify from '@/utils/toast';
import { fromNow, formatDateTime } from '@/utils/format';
import { selectCurrentUser } from '@/redux/authSlice';
import { subscribeToConversationMessages } from '@/services/chatSocketService';

const MessagesPage = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [searchParams, setSearchParams] = useSearchParams();
  const currentUser = useSelector(selectCurrentUser);

  const [conversations, setConversations] = useState([]);
  const [loadingList, setLoadingList] = useState(true);
  const [activeId, setActiveId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [loadingMsg, setLoadingMsg] = useState(false);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const endRef = useRef(null);

  const upsertMessage = useCallback((incoming) => {
    if (!incoming?.id) return;
    const normalized = {
      ...incoming,
      sentByMe: String(incoming.senderId) === String(currentUser?.id),
    };
    setMessages((prev) => {
      const exists = prev.some((m) => String(m.id) === String(normalized.id));
      const next = exists
        ? prev.map((m) => (String(m.id) === String(normalized.id) ? { ...m, ...normalized } : m))
        : [...prev, normalized];
      return next.sort((a, b) => new Date(a.sentAt || 0) - new Date(b.sentAt || 0));
    });
    setConversations((prev) => prev.map((c) => (
      c.id === normalized.conversationId ? {
        ...c,
        unreadCount: normalized.sentByMe || c.id === activeId ? 0 : c.unreadCount,
        lastMessage: {
          content: normalized.content,
          sentByMe: normalized.sentByMe,
          sentAt: normalized.sentAt,
        },
        lastMessageAt: normalized.sentAt,
      } : c
    )));
  }, [activeId, currentUser?.id]);

  const loadConversations = useCallback(async () => {
    setLoadingList(true);
    try {
      const data = await contactApi.getConversations({ page: 0, size: 50, sort: 'lastMessageAt,desc' });
      setConversations(data?.items || []);
      return data?.items || [];
    } catch (e) {
      notify.apiError(e, 'Không tải được cuộc trò chuyện');
      return [];
    } finally {
      setLoadingList(false);
    }
  }, []);

  useEffect(() => {
    loadConversations().then((list) => {
      const qId = searchParams.get('conversationId');
      if (qId) setActiveId(Number(qId));
      else if (!isMobile && list.length) setActiveId(list[0].id);
    });
    // eslint-disable-next-line
  }, []);

  const loadMessages = useCallback(async (id) => {
    if (!id) return;
    setLoadingMsg(true);
    try {
      const data = await contactApi.getMessages(id, { page: 0, size: 50, sort: 'sentAt,desc' });
      // API trả mới -> cũ; đảo lại để render từ cũ -> mới.
      setMessages([...(data?.items || [])].reverse());
      // Đánh dấu đã đọc.
      contactApi.markRead(id).then(() => {
        setConversations((prev) => prev.map((c) => (c.id === id ? { ...c, unreadCount: 0 } : c)));
      }).catch(() => {});
    } catch (e) {
      notify.apiError(e, 'Không tải được tin nhắn');
    } finally {
      setLoadingMsg(false);
    }
  }, []);

  useEffect(() => {
    if (activeId) {
      loadMessages(activeId);
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.set('conversationId', String(activeId));
        return next;
      }, { replace: true });
    }
    // eslint-disable-next-line
  }, [activeId]);

  useEffect(() => {
    if (!activeId || !currentUser?.id) return undefined;
    return subscribeToConversationMessages({
      conversationId: activeId,
      currentUserId: currentUser.id,
      onMessage: (message) => {
        upsertMessage(message);
        if (!message.sentByMe) {
          contactApi.markRead(activeId).then(() => {
            setConversations((prev) => prev.map((c) => (
              c.id === activeId ? { ...c, unreadCount: 0 } : c
            )));
          }).catch(() => {});
        }
      },
      onError: () => {},
    });
  }, [activeId, currentUser?.id, upsertMessage]);

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  const handleSend = async () => {
    const content = input.trim();
    if (!content || !activeId) return;
    setSending(true);
    try {
      const msg = await contactApi.sendMessage(activeId, { content });
      upsertMessage(msg);
      setInput('');
      setConversations((prev) => prev.map((c) => (
        c.id === activeId ? { ...c, lastMessage: { content, sentByMe: true, sentAt: msg?.sentAt }, lastMessageAt: msg?.sentAt } : c
      )));
    } catch (e) {
      notify.apiError(e, 'Gửi tin nhắn thất bại');
    } finally {
      setSending(false);
    }
  };

  const active = conversations.find((c) => c.id === activeId);
  const showList = !isMobile || !activeId;
  const showThread = !isMobile || !!activeId;

  const listPanel = (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, p: 2, pb: 1 }}>Cuộc trò chuyện</Typography>
      <Divider />
      <Box sx={{ overflowY: 'auto', flex: 1 }}>
        {loadingList ? (
          <Box sx={{ p: 2 }}><LoadingSkeleton variant="conversation" count={5} /></Box>
        ) : conversations.length === 0 ? (
          <EmptyState size="sm" title="Chưa có cuộc trò chuyện" description="Liên hệ chủ trọ từ trang tin đăng để bắt đầu." />
        ) : (
          <List disablePadding>
            {conversations.map((c) => (
              <ListItemButton key={c.id} selected={c.id === activeId} onClick={() => setActiveId(c.id)} alignItems="flex-start">
                <ListItemAvatar>
                  <Badge color="error" badgeContent={c.unreadCount || 0} overlap="circular">
                    <Avatar src={c.partner?.avatarUrl}>{c.partner?.fullName?.charAt(0)}</Avatar>
                  </Badge>
                </ListItemAvatar>
                <ListItemText
                  primary={(
                    <Stack direction="row" justifyContent="space-between" spacing={1}>
                      <Typography variant="subtitle2" noWrap sx={{ fontWeight: c.unreadCount ? 700 : 600 }}>{c.partner?.fullName}</Typography>
                      <Typography variant="caption" color="text.disabled" sx={{ flexShrink: 0 }}>{fromNow(c.lastMessageAt)}</Typography>
                    </Stack>
                  )}
                  secondary={(
                    <>
                      <Typography variant="caption" color="primary" noWrap sx={{ display: 'block' }}>{c.listingTitle}</Typography>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {c.lastMessage?.sentByMe ? 'Bạn: ' : ''}{c.lastMessage?.content}
                      </Typography>
                    </>
                  )}
                />
              </ListItemButton>
            ))}
          </List>
        )}
      </Box>
    </Card>
  );

  const threadPanel = (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {!active ? (
        <EmptyState icon={<ChatBubbleOutlineIcon />} title="Chọn một cuộc trò chuyện" description="Chọn cuộc trò chuyện bên trái để xem tin nhắn." />
      ) : (
        <>
          <Stack direction="row" spacing={1.5} alignItems="center" sx={{ p: 2, pb: 1.5 }}>
            {isMobile && <IconButton edge="start" onClick={() => setActiveId(null)}><ArrowBackIcon /></IconButton>}
            <Avatar src={active.partner?.avatarUrl}>{active.partner?.fullName?.charAt(0)}</Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }} noWrap>{active.partner?.fullName}</Typography>
              <Typography variant="caption" color="text.secondary" noWrap>{active.listingTitle}</Typography>
            </Box>
          </Stack>
          <Divider />

          <Box sx={{ flex: 1, overflowY: 'auto', p: 2, bgcolor: 'action.hover' }}>
            {loadingMsg ? (
              <LoadingSkeleton variant="comment" count={4} />
            ) : messages.length === 0 ? (
              <EmptyState size="sm" title="Chưa có tin nhắn" />
            ) : (
              <Stack spacing={1}>
                {messages.map((m) => (
                  <Stack key={m.id} direction="row" justifyContent={m.sentByMe ? 'flex-end' : 'flex-start'}>
                    <Tooltip title={formatDateTime(m.sentAt)} placement={m.sentByMe ? 'left' : 'right'}>
                      <Box
                        sx={{
                          maxWidth: '75%', px: 1.5, py: 1, borderRadius: 2,
                          bgcolor: m.sentByMe ? 'primary.main' : 'background.paper',
                          color: m.sentByMe ? 'primary.contrastText' : 'text.primary',
                          boxShadow: 1,
                        }}
                      >
                        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{m.content}</Typography>
                      </Box>
                    </Tooltip>
                  </Stack>
                ))}
                <div ref={endRef} />
              </Stack>
            )}
          </Box>
          <Divider />

          <Stack direction="row" spacing={1} sx={{ p: 1.5 }} alignItems="flex-end">
            <TextField
              fullWidth size="small" multiline maxRows={4} placeholder="Nhập tin nhắn…"
              value={input} onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
              disabled={sending}
            />
            <IconButton color="primary" onClick={handleSend} disabled={sending || !input.trim()}>
              <SendIcon />
            </IconButton>
          </Stack>
        </>
      )}
    </Card>
  );

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 128px)' }}>
      <PageHeader title="Tin nhắn" />
      <Box sx={{ flex: 1, display: 'flex', gap: 2, minHeight: 0 }}>
        {showList && <Box sx={{ width: { xs: '100%', md: 340 }, flexShrink: 0, minHeight: 0 }}>{listPanel}</Box>}
        {showThread && <Box sx={{ flex: 1, minWidth: 0, minHeight: 0 }}>{threadPanel}</Box>}
      </Box>
    </Box>
  );
};

export default MessagesPage;
