import { useEffect, useMemo, useState } from 'react';
import {
  ApiOutlined,
  AppstoreOutlined,
  BgColorsOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  FileExcelOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MonitorOutlined,
  QuestionCircleOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App as AntApp,
  Badge,
  Button,
  Layout,
  Menu,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { api } from './api';
import { errorMessage } from './helpers';
import type { Device, DeviceStatus, Metadata } from './types';
import DevicesPage from './pages/DevicesPage';
import PointsPage from './pages/PointsPage';
import MonitorPage from './pages/MonitorPage';
import ExcelPage from './pages/ExcelPage';
import BacnetPage from './pages/BacnetPage';
import HelpPage from './pages/HelpPage';

const { Header, Sider, Content } = Layout;

type PageKey = 'devices' | 'points' | 'monitor' | 'excel' | 'bacnet' | 'help';

const validPages: PageKey[] = ['devices', 'points', 'monitor', 'excel', 'bacnet', 'help'];

function pageFromHash(): PageKey {
  const value = window.location.hash.replace(/^#\/?/, '') as PageKey;
  return validPages.includes(value) ? value : 'devices';
}

const menuItems = [
  { key: 'devices', icon: <CloudServerOutlined />, label: '设备配置' },
  { key: 'points', icon: <AppstoreOutlined />, label: '点位配置' },
  { key: 'monitor', icon: <MonitorOutlined />, label: '实时监视' },
  { key: 'excel', icon: <FileExcelOutlined />, label: 'Excel 导入导出' },
  { key: 'bacnet', icon: <ApiOutlined />, label: 'BACnet 映射' },
  { key: 'help', icon: <QuestionCircleOutlined />, label: '使用说明' },
];

export default function App() {
  const { message } = AntApp.useApp();
  const [page, setPage] = useState<PageKey>(pageFromHash);
  const [collapsed, setCollapsed] = useState(false);
  const [meta, setMeta] = useState<Metadata | null>(null);
  const [devices, setDevices] = useState<Device[]>([]);
  const [statuses, setStatuses] = useState<DeviceStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [fatalError, setFatalError] = useState<string | null>(null);

  const refreshStatuses = async (silent = true) => {
    try {
      const data = await api.get<DeviceStatus[]>('/api/devices/status');
      setStatuses(data);
      setFatalError(null);
    } catch (error) {
      if (!silent) message.error(errorMessage(error));
      else setFatalError(errorMessage(error));
    }
  };

  const refreshDevices = async (silent = false) => {
    try {
      const data = await api.get<Device[]>('/api/devices');
      setDevices(data);
      setFatalError(null);
      return data;
    } catch (error) {
      if (!silent) message.error(errorMessage(error));
      else setFatalError(errorMessage(error));
      return [];
    }
  };

  const refreshAll = async () => {
    setLoading(true);
    await Promise.all([refreshDevices(true), refreshStatuses(true)]);
    setLoading(false);
  };

  useEffect(() => {
    const onHashChange = () => setPage(pageFromHash());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const bootstrap = async () => {
      try {
        const [metaData] = await Promise.all([
          api.get<Metadata>('/api/meta'),
          refreshDevices(true),
          refreshStatuses(true),
        ]);
        if (!cancelled) setMeta(metaData);
      } catch (error) {
        if (!cancelled) setFatalError(errorMessage(error));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    bootstrap();
    const timer = window.setInterval(() => {
      if (!cancelled && document.visibilityState === 'visible') {
        refreshStatuses(true);
      }
    }, 10000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const connected = useMemo(
    () => statuses.filter((status) => status.connected).length,
    [statuses],
  );
  const secureCount = useMemo(
    () => devices.filter((device) => device.protocol === 'MODBUS_TCP_SECURE').length,
    [devices],
  );
  const pointCount = useMemo(
    () => devices.reduce((sum, device) => sum + device.pointCount, 0),
    [devices],
  );

  const selectPage = (key: string) => {
    setPage(key as PageKey);
    window.location.hash = key;
  };

  const activePage = (() => {
    switch (page) {
      case 'devices':
        return (
          <DevicesPage
            devices={devices}
            statuses={statuses}
            onChanged={refreshAll}
            onStatusesChanged={refreshStatuses}
          />
        );
      case 'points':
        return <PointsPage devices={devices} onDevicesChanged={refreshAll} />;
      case 'monitor':
        return <MonitorPage devices={devices} />;
      case 'excel':
        return <ExcelPage onImported={refreshAll} />;
      case 'bacnet':
        return <BacnetPage devices={devices} />;
      case 'help':
        return <HelpPage />;
      default:
        return null;
    }
  })();

  return (
    <Layout className="app-shell">
      <Sider
        className="app-sider"
        width={232}
        collapsedWidth={0}
        breakpoint="lg"
        trigger={null}
        collapsible
        collapsed={collapsed}
        onBreakpoint={(broken) => setCollapsed(broken)}
      >
        <div className="brand">
          <div className="brand-mark">
            <BgColorsOutlined />
          </div>
          <div>
            <div className="brand-title">Modbus Studio</div>
            <div className="brand-subtitle">Field Configuration Workbench</div>
          </div>
        </div>
        <div className="side-section-label">工作台</div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={[page]}
          items={menuItems}
          onClick={(event) => selectPage(event.key)}
          className="side-menu"
        />
        <div className="side-summary">
          <div className="side-summary-title">当前配置</div>
          <div className="side-summary-row">
            <Badge status="processing" text={`${connected} 在线`} />
            <span>{devices.length} 设备</span>
          </div>
          <div className="side-summary-row">
            <DatabaseOutlined />
            <span>{pointCount} 点位</span>
          </div>
          <div className="side-summary-row">
            <SafetyCertificateOutlined />
            <span>{secureCount} 个 TLS 设备</span>
          </div>
        </div>
        <div className="side-footer">v1.0.0 · digitalpetri modbus 2.1.6</div>
      </Sider>

      <Layout>
        <Header className="app-header">
          <Space size={12}>
            <Button
              type="text"
              className="menu-toggle"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((value) => !value)}
            />
            <div className="header-context">
              <span className="header-kicker">MODBUS / TCP · SECURITY · BACnet BRIDGE</span>
              <span className="header-title">
                {menuItems.find((item) => item.key === page)?.label}
              </span>
            </div>
          </Space>
          <Space size={10} wrap>
            <Tag className="status-tag" color={connected > 0 ? 'success' : 'default'}>
              <Badge status={connected > 0 ? 'success' : 'default'} />
              {connected}/{devices.length} 已连接
            </Tag>
            <Tooltip title="刷新设备与连接状态">
              <Button
                icon={<ReloadOutlined />}
                onClick={refreshAll}
                loading={loading}
                className="header-refresh"
              >
                刷新
              </Button>
            </Tooltip>
          </Space>
        </Header>

        <Content className="app-content">
          <div className="content-frame">
            {fatalError ? (
              <Alert
                className="backend-alert"
                type="error"
                showIcon
                message="后端服务不可用"
                description={
                  <Space direction="vertical" size={4}>
                    <Typography.Text>{fatalError}</Typography.Text>
                    <Typography.Text type="secondary">
                      请确认 Spring Boot 后端已启动（默认 http://127.0.0.1:8080），或检查 Vite
                      代理配置。生产包请直接运行打包后的 JAR。
                    </Typography.Text>
                  </Space>
                }
                action={
                  <Button size="small" onClick={refreshAll}>
                    重试
                  </Button>
                }
              />
            ) : null}
            {activePage}
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}
