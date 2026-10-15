import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import notificationApi from '@/api/notificationApi';

/**
 * State chat dùng chung nhiều trang: tổng số tin nhắn chưa đọc (badge menu "Tin nhắn").
 * Giá trị đầu lấy từ API, sau đó cập nhật realtime qua sự kiện CONVERSATION_UPDATED (useChatRealtime).
 */

export const fetchUnreadMessages = createAsyncThunk('chat/unreadMessages', async () => {
  const data = await notificationApi.getUnreadCount();
  return Number(data?.unreadMessageCount || 0);
});

const chatSlice = createSlice({
  name: 'chat',
  initialState: {
    unreadMessages: 0,
  },
  reducers: {
    setUnreadMessages(state, action) {
      state.unreadMessages = Math.max(0, Number(action.payload) || 0);
    },
    resetChat(state) {
      state.unreadMessages = 0;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(fetchUnreadMessages.fulfilled, (state, action) => {
      state.unreadMessages = action.payload;
    });
  },
});

export const { setUnreadMessages, resetChat } = chatSlice.actions;
export const selectUnreadMessages = (state) => state.chat.unreadMessages;

export default chatSlice.reducer;
