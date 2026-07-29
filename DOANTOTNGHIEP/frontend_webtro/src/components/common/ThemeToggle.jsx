import { IconButton, Tooltip } from '@mui/material';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import { useDispatch, useSelector } from 'react-redux';
import { toggleTheme, selectThemeMode } from '@/redux/uiSlice';

/** Nút chuyển chế độ sáng/tối. Ghi nhớ lựa chọn trong localStorage (qua uiSlice). */
const ThemeToggle = () => {
  const dispatch = useDispatch();
  const mode = useSelector(selectThemeMode);

  return (
    <Tooltip title={mode === 'dark' ? 'Chế độ sáng' : 'Chế độ tối'}>
      <IconButton onClick={() => dispatch(toggleTheme())} color="inherit">
        {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
      </IconButton>
    </Tooltip>
  );
};

export default ThemeToggle;
