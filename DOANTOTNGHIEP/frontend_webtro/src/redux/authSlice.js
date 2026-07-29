import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import authApi from '@/api/authApi';
import tokenService from '@/services/tokenService';

/**
 * State xác thực toàn cục. Chỉ giữ thông tin cần nhiều trang (canonical luật F4). Access token
 * KHÔNG nằm trong Redux (nó ở tokenService — memory); ở đây chỉ mirror `user` + cờ trạng thái để
 * component render theo quyền.
 */

export const login = createAsyncThunk('auth/login', async (credentials, { rejectWithValue }) => {
  try {
    const data = await authApi.login(credentials);
    tokenService.set(data.accessToken);
    return data.user;
  } catch (err) {
    return rejectWithValue(err);
  }
});

/** Gọi lúc mở app: dùng cookie refresh để khôi phục phiên sau F5 (canonical mục 4.3). */
export const bootstrapAuth = createAsyncThunk('auth/bootstrap', async (_, { rejectWithValue }) => {
  try {
    const data = await authApi.refresh();
    tokenService.set(data.accessToken);
    const me = await authApi.me();
    return me;
  } catch (err) {
    return rejectWithValue(err);
  }
});

export const logout = createAsyncThunk('auth/logout', async () => {
  try {
    await authApi.logout();
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
export const selectRoles = (state) => state.auth.user?.roles ?? [];
export const selectPermissions = (state) => state.auth.user?.permissions ?? [];

export default authSlice.reducer;
