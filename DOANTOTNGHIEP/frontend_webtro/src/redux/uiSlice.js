import { createSlice } from '@reduxjs/toolkit';

/**
 * State giao diện toàn cục: chế độ sáng/tối và trạng thái sidebar mobile.
 * Chế độ theme được nhớ trong localStorage (không nhạy cảm — canonical mục 8 chỉ cấm cache dữ
 * liệu cá nhân nhạy cảm).
 */
const savedMode =
  typeof window !== 'undefined' ? window.localStorage.getItem('webtro-theme') : null;

const uiSlice = createSlice({
  name: 'ui',
  initialState: {
    themeMode: savedMode === 'dark' ? 'dark' : 'light',
    mobileSidebarOpen: false,
  },
  reducers: {
    toggleTheme(state) {
      state.themeMode = state.themeMode === 'light' ? 'dark' : 'light';
      if (typeof window !== 'undefined') {
        window.localStorage.setItem('webtro-theme', state.themeMode);
      }
    },
    setMobileSidebar(state, action) {
      state.mobileSidebarOpen = action.payload;
    },
    toggleMobileSidebar(state) {
      state.mobileSidebarOpen = !state.mobileSidebarOpen;
    },
  },
});

export const { toggleTheme, setMobileSidebar, toggleMobileSidebar } = uiSlice.actions;
export const selectThemeMode = (state) => state.ui.themeMode;
export const selectMobileSidebarOpen = (state) => state.ui.mobileSidebarOpen;

export default uiSlice.reducer;
