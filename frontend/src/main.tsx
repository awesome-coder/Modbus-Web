import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigProvider, App as AntApp, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import App from './App';
import './styles.css';

dayjs.locale('zh-cn');

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#0f766e',
          colorInfo: '#0f766e',
          colorSuccess: '#15803d',
          colorWarning: '#d97706',
          colorError: '#dc2626',
          borderRadius: 10,
          colorBgLayout: '#f2f5f7',
          fontFamily:
            '"IBM Plex Sans", "Segoe UI", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif',
        },
        components: {
          Layout: {
            siderBg: '#0b1729',
            triggerBg: '#10243d',
          },
          Menu: {
            darkItemBg: '#0b1729',
            darkItemSelectedBg: '#0f766e',
            darkItemHoverBg: '#142d49',
            darkSubMenuItemBg: '#0b1729',
          },
          Table: {
            headerBg: '#eef3f5',
            headerColor: '#324b56',
            rowHoverBg: '#f2fbfa',
          },
          Card: {
            headerBg: 'transparent',
          },
        },
      }}
    >
      <AntApp>
        <App />
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
);
