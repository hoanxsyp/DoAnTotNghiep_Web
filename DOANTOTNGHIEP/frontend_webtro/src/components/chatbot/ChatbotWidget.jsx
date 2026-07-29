import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Fab, Paper, Box, Typography, IconButton, TextField, InputAdornment,
  Stack, Avatar, CircularProgress, Chip, Card, CardActionArea, CardContent,
} from '@mui/material';
import ChatIcon from '@mui/icons-material/ChatBubbleOutline';
import CloseIcon from '@mui/icons-material/Close';
import SendIcon from '@mui/icons-material/Send';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import chatbotApi from '@/api/chatbotApi';
import { formatPrice } from '@/utils/format';

/**
 * Widget chatbot nổi ở góc phải [§9.3]. Gửi câu hỏi tự nhiên, nhận trả lời + danh sách tin gợi ý
 * (chỉ tin Active — backend đảm bảo, không bịa dữ liệu). Giữ conversationId để duy trì ngữ cảnh.
 */
const ChatbotWidget = () => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([
    { sender: 'BOT', text: 'Xin chào! Mình có thể giúp bạn tìm phòng trọ. Bạn muốn thuê ở khu vực nào, tầm giá bao nhiêu?' },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId, setConversationId] = useState(null);
  const endRef = useRef(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, open]);

  const send = async () => {
    const text = input.trim();
    if (!text || loading) return;
    setInput('');
    setMessages((m) => [...m, { sender: 'USER', text }]);
    setLoading(true);
    try {
      const res = await chatbotApi.sendMessage({ conversationId, message: text });
      setConversationId(res.conversationId ?? conversationId);
      setMessages((m) => [
        ...m,
        { sender: 'BOT', text: res.reply, listings: res.listings ?? [], suggestions: res.quickReplies ?? [] },
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
                    {msg.suggestions?.length > 0 && (
                      <Stack direction="row" spacing={0.5} sx={{ mt: 1, flexWrap: 'wrap', gap: 0.5 }}>
                        {msg.suggestions.map((s, si) => (
                          <Chip key={si} label={s} size="small" onClick={() => setInput(s)} clickable />
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
                    <IconButton color="primary" onClick={send} disabled={loading || !input.trim()}>
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
