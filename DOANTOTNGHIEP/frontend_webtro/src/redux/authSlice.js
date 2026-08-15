import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import authApi from '@/api/authApi';
import tokenService from '@/services/tokenService';

/**
 * State xác thực toàn cục. Chỉ giữ thông tin cần nhiều trang (canonical luật F4). Token KHÔNG nằm
 * trong Redux (chúng ở tokenService — localStorage); ở đây chỉ mirror `user` + cờ trạng thái để
 * component render theo quyền.
 *
 * `user.role` là MỘT chuỗi (mỗi người dùng đúng một vai trò). Frontend chỉ ẩn/hiện theo role;
 * quyền thực thi thật luôn kiểm lại ở backend.
 */

export const login = createAsyncThunk('auth/login', async (credentials, { rejectWithValue }) => {
  try {
    const data = await authApi.login(credentials);
    tokenService.setTokens(data);
    return data.user;
  } catch (err) {
    return rejectWithValue(err);
  }
});

/**
 * Gọi lúc mở app để khôi phục phiên. Access token đã nằm sẵn trong localStorage nên thường chỉ cần
 * gọi /users/me; chỉ khi access token hết hạn mới phải làm mới (interceptor tự lo, hoặc làm mới
 * tường minh khi không còn access token).
 */
export const bootstrapAuth = createAsyncThunk('auth/bootstrap', async (_, { rejectWithValue }) => {
  try {
    tokenService.syncFromStorage();
    if (!tokenService.has() && !tokenService.hasRefresh()) {
      // Chưa từng đăng nhập — không gọi API, tránh một request 401 vô nghĩa mỗi lần mở trang.
      return rejectWithValue(null);
    }
    if (!tokenService.has()) {
      const data = await authApi.refresh({ refreshToken: tokenService.getRefresh() });
      tokenService.setTokens(data);
    }
    return await authApi.me();
  } catch (err) {
    tokenService.clear();
    return rejectWithValue(err);
  }
});

export const logout = createAsyncThunk('auth/logout', async () => {
  try {
    await authApi.logout({ refreshToken: tokenService.getRefresh() });
  } finally {
    tokenService.clear();
  }
});

const initialState = {
  user: null,
  isAuthenticated: false,
  // 'idle' trước khi bootstrap xong; guard chờ trạng thái này để tránh nháy trang đăng nhập.
  status: 'idle',
  bootstrapped: false,
  error: null,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    clearSession(state) {
      state.user = null;
      state.isAuthenticated = false;
      state.bootstrapped = true;
      tokenService.clear();
    },
    setUser(state, action) {
      state.user = action.payload;
      state.isAuthenticated = Boolean(action.payload);
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(login.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(login.fulfilled, (state, action) => {
        state.status = 'succeeded';
        state.user = action.payload;
        state.isAuthenticated = true;
        state.bootstrapped = true;
      })
      .addCase(login.rejected, (state, action) => {
        state.status = 'failed';
        state.error = action.payload;
      })
      .addCase(bootstrapAuth.fulfilled, (state, action) => {
        state.user = action.payload;
        state.isAuthenticated = true;
        state.bootstrapped = true;
      })
      .addCase(bootstrapAuth.rejected, (state) => {
        state.user = null;
        state.isAuthenticated = false;
        state.bootstrapped = true;
      })
      .addCase(logout.fulfilled, (state) => {
        state.user = null;
        state.isAuthenticated = false;
      });
  },
});

export const { clearSession, setUser } = authSlice.actions;

// Selectors
export const selectCurrentUser = (state) => state.auth.user;
export const selectIsAuthenticated = (state) => state.auth.isAuthenticated;
export const selectAuthBootstrapped = (state) => state.auth.bootstrapped;
/** Vai trò DUY NHẤT của người dùng (chuỗi) hoặc null khi chưa đăng nhập. */
export const selectRole = (state) => state.auth.user?.role ?? null;

export default authSlice.reducer;
