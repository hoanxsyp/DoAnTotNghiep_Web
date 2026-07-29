import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import notificationApi from '@/api/notificationApi';

/**
 * State thông báo: số chưa đọc (hiển thị badge trên chuông) + danh sách gần đây trong popover.
 * Danh sách đầy đủ có trang riêng; ở đây chỉ giữ tối thiểu cho NotificationBell.
 */

export const fetchUnreadCount = createAsyncThunk('notification/unreadCount', async () => {
  return notificationApi.getUnreadCount();
});

export const fetchRecent = createAsyncThunk('notification/recent', async () => {
  return notificationApi.getRecent();
});

const notificationSlice = createSlice({
  name: 'notification',
  initialState: {
    unreadCount: 0,
    recent: [],
  },
  reducers: {
    decrementUnread(state) {
      state.unreadCount = Math.max(0, state.unreadCount - 1);
    },
    resetUnread(state) {
      state.unreadCount = 0;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchUnreadCount.fulfilled, (state, action) => {
        state.unreadCount = action.payload ?? 0;
      })
      .addCase(fetchRecent.fulfilled, (state, action) => {
        state.recent = action.payload?.items ?? action.payload ?? [];
      });
  },
});

export const { decrementUnread, resetUnread } = notificationSlice.actions;
export const selectUnreadCount = (state) => state.notification.unreadCount;
export const selectRecentNotifications = (state) => state.notification.recent;

export default notificationSlice.reducer;
