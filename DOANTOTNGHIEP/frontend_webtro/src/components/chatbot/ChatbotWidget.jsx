import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Fab, Paper, Box, Typography, IconButton, TextField, InputAdornment,
  Stack, Avatar, CircularProgress, Chip, Card, CardActionArea, CardContent, Button, Tooltip,
} from '@mui/material';
import ChatIcon from '@mui/icons-material/ChatBubbleOutline';
import CloseIcon from '@mui/icons-material/Close';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import SendIcon from '@mui/icons-material/Send';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import chatbotApi from '@/api/chatbotApi';
import { formatPrice } from '@/utils/format';

const SESSION_KEY = 'webtro.chatbot.sessionId';

/** UUID v4 — crypto.randomUUID chỉ có trên HTTPS/localhost, nên có phương án dự phòng. */
const newUuid = () => {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID();
  const b = window.crypto?.getRandomValues ? window.crypto.getRandomValues(new Uint8Array(16))
    : Uint8Array.from({ length: 16 }, () => Math.floor(Math.random() * 256));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
};

/**
 * Định danh phiên chat của khách — backend BẮT BUỘC khi chưa đăng nhập (thiếu thì mọi tin
 * nhắn của khách bị từ chối). Giữ qua các lần tải trang; storage bị chặn thì dùng tạm trong bộ nhớ.
 */
let memorySessionId = null;
const getChatSessionId = () => {
  try {
    let id = window.localStorage.getItem(SESSION_KEY);
    if (!id) {
      id = newUuid();
      window.localStorage.setItem(SESSION_KEY, id);
    }
    return id;
  } catch {
    memorySessionId = memorySessionId || newUuid();
    return memorySessionId;
  }
};

/**
 * Widget chatbot nổi ở góc phải [§9.3]. Gửi câu hỏi tự nhiên, nhận trả lời + danh sách tin gợi ý
 * (chỉ tin Active — backend đảm bảo, không bịa dữ liệu). Giữ conversationId để duy trì ngữ cảnh.
 */
const GREETING = { sender: 'BOT', text: 'Xin chào! Mình có thể giúp bạn tìm phòng trọ. Bạn muốn thuê ở khu vực nào, tầm giá bao nhiêu?' };

/** Gợi ý bấm nhanh (quickReplies dạng {label, value}). */
const suggestionsOf = (res) => (res.quickReplies ?? []).map((q) => ({ label: q.label, value: q.value || q.label }));

/**
 * Gợi ý nới điều kiện khi không có kết quả chỉ hiện dạng chữ: chatbot AI không hiểu câu chung chung
 * như "Nới khoảng giá", người dùng cần nhập lại kèm con số cụ thể.
 */
const expansionHintOf = (res) => {
  const labels = (res.expansionSuggestions ?? []).map((e) => e.label.toLowerCase());
  return labels.length ? `Bạn thử ${labels.join(', ')} rồi nhập lại yêu cầu nhé (ví dụ "dưới 4 triệu").` : null;
};

const ChatbotWidget = () => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([GREETING]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId, setConversationId] = useState(null);
  // Bấm "Bắt đầu lại" → tin kế tiếp gửi resetContext để backend (và AI) bỏ điều kiện cũ.
  const [pendingReset, setPendingReset] = useState(false);
  const endRef = useRef(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, open]);

  const restart = () => {
    setMessages([GREETING]);
    setInput('');
    setPendingReset(true);
  };

  const openSearch = (url) => {
    setOpen(false);
    navigate(url);
  };

  const send = async (override) => {
    const text = (override ?? input).trim();
    if (!text || loading) return;
    setInput('');
    setMessages((m) => [...m, { sender: 'USER', text }]);
    setLoading(true);
    try {
      const res = await chatbotApi.sendMessage({
        conversationId, sessionId: getChatSessionId(), message: text,
        ...(pendingReset ? { resetContext: true } : {}),
      });
      setPendingReset(false);
      setConversationId(res.conversationId ?? conversationId);
      setMessages((m) => [
        ...m,
        {
          sender: 'BOT',
          text: res.reply,
          listings: res.listings ?? [],
          searchUrl: res.searchUrl,
          disclaimer: res.disclaimer,
          expansionHint: expansionHintOf(res),
          suggestions: suggestionsOf(res),
        },
      ]);
    } catch {
      setMessages((m) => [...m, { sender: 'BOT', text: 'Xin lỗi, mình đang gặp sự cố. Bạn thử lại sau nhé.' }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      {!open && (
        <Fab
          color="primary"
          onClick={() => setOpen(true)}
          sx={{ position: 'fixed', bottom: 24, right: 24, zIndex: 1200 }}
          aria-label="Mở chatbot tìm trọ"
        >
          <ChatIcon />
        </Fab>
      )}

      {open && (
        <Paper
          elevation={8}
          sx={{
            position: 'fixed', bottom: 24, right: 24, zIndex: 1200,
            width: { xs: 'calc(100vw - 32px)', sm: 380 }, height: 520,
            display: 'flex', flexDirection: 'column', borderRadius: 3, overflow: 'hidden',
          }}
        >
          <Box sx={{ bgcolor: 'primary.main', color: '#fff', px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
            <SmartToyIcon />
            <Typography fontWeight={700} sx={{ flexGrow: 1 }}>Trợ lý tìm trọ</Typography>
            <Tooltip title="Bắt đầu lại">
              <span>
                <IconButton size="small" onClick={restart} disabled={loading} sx={{ color: '#fff' }} aria-label="Bắt đầu lại">
                  <RestartAltIcon />
                </IconButton>
              </span>
            </Tooltip>
            <IconButton size="small" onClick={() => setOpen(false)} sx={{ color: '#fff' }}>
              <CloseIcon />
            </IconButton>
          </Box>

          <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 1.5, bgcolor: 'background.default' }}>
            <Stack spacing={1.5}>
              {messages.map((msg, i) => (
                <Box key={i} sx={{ display: 'flex', justifyContent: msg.sender === 'USER' ? 'flex-end' : 'flex-start' }}>
                  <Box sx={{ maxWidth: '85%' }}>
                    <Box
                      sx={{
                        px: 1.5, py: 1, borderRadius: 2,
                        bgcolor: msg.sender === 'USER' ? 'primary.main' : 'background.paper',
                        color: msg.sender === 'USER' ? '#fff' : 'text.primary',
                        border: msg.sender === 'USER' ? 'none' : 1, borderColor: 'divider',
                      }}
                    >
                      <Typography variant="body2">{msg.text}</Typography>
                    </Box>
                    {msg.listings?.length > 0 && (
                      <Stack spacing={1} sx={{ mt: 1 }}>
                        {msg.listings.slice(0, 3).map((l) => (
                          <Card key={l.id} variant="outlined">
                            <CardActionArea onClick={() => navigate(`/tin/${l.slug}-${l.id}`)}>
                              <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
                                <Typography variant="body2" fontWeight={700} noWrap>{l.title}</Typography>
                                <Typography variant="caption" color="primary.main" fontWeight={700}>
                                  {formatPrice(l.price)}/tháng · {l.area}m²
                                </Typography>
                              </CardContent>
                            </CardActionArea>
                          </Card>
                        ))}
                      </Stack>
                    )}
                    {msg.searchUrl && (
                      <Button size="small" fullWidth variant="outlined" sx={{ mt: 1 }} onClick={() => openSearch(msg.searchUrl)}>
                        Xem tất cả kết quả trên trang Tìm kiếm
                      </Button>
                    )}
                    {msg.disclaimer && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', gap: 0.5, mt: 1 }}>
                        <InfoOutlinedIcon sx={{ fontSize: 14, mt: '2px' }} />
                        {msg.disclaimer}
                      </Typography>
                    )}
                    {msg.expansionHint && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                        {msg.expansionHint}
                      </Typography>
                    )}
                    {msg.suggestions?.length > 0 && (
                      <Stack direction="row" spacing={0.5} sx={{ mt: 1, flexWrap: 'wrap', gap: 0.5 }}>
                        {msg.suggestions.map((s, si) => (
                          <Chip key={si} label={s.label} size="small" onClick={() => send(s.value)} disabled={loading} clickable />
                        ))}
                      </Stack>
                    )}
                  </Box>
                </Box>
              ))}
              {loading && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Avatar sx={{ width: 24, height: 24, bgcolor: 'primary.main' }}><SmartToyIcon sx={{ fontSize: 14 }} /></Avatar>
                  <CircularProgress size={16} />
                </Box>
              )}
              <div ref={endRef} />
            </Stack>
          </Box>

          <Box sx={{ p: 1, borderTop: 1, borderColor: 'divider' }}>
            <TextField
              size="small"
              fullWidth
              placeholder="Nhập nhu cầu của bạn..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && send()}
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton color="primary" onClick={() => send()} disabled={loading || !input.trim()}>
                      <SendIcon />
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />
          </Box>
        </Paper>
      )}
    </>
  );
};

export default ChatbotWidget;
