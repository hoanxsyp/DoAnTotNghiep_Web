import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import uiReducer from './uiSlice';
import notificationReducer from './notificationSlice';

/**
 * Redux store. Chỉ chứa state DÙNG CHUNG nhiều trang (canonical luật F4): auth, ui, notification.
 * State riêng của một trang để useState trong trang đó — không nhét vào đây.
 */
export const store = configureStore({
  reducer: {
    auth: authReducer,
    ui: uiReducer,
    notification: notificationReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      // Cho phép object lỗi đã chuẩn hóa nằm trong action mà không cảnh báo serializable.
      serializableCheck: {
        ignoredActionPaths: ['payload.fieldErrors', 'meta.arg', 'payload'],
      },
    }),
});

export default store;
