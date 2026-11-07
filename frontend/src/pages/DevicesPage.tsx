import { useEffect, useMemo, useState } from 'react';
import {
  ApiOutlined,
  CheckCircleOutlined,
  CloudServerOutlined,
  DeleteOutlined,
  DisconnectOutlined,
  EditOutlined,
  LinkOutlined,
  LockOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App as AntApp,
  Badge,
  Button,
  Card,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../api';
import PageHeading from '../components/PageHeading';
import { errorMessage, formatTime, protocolLabel, statusByDevice } from '../helpers';
import type {
  ConnectionTestResult,
  Device,
  DevicePayload,
  DeviceStatus,
  ProtocolType,
} from '../types';

interface Props {
  devices: Device[];
  statuses: DeviceStatus[];
  onChanged: () => Promise<void> | void;
  onStatusesChanged: (silent?: boolean) => Promise<void> | void;
}

type DeviceFormValues = DevicePayload & { tlsKeyPassword?: string };

const protocolOptions = [
  { value: 'MODBUS_TCP', label: 'Modbus TCP', description: '标准 Modbus TCP，默认端口 502' },
  {
    value: 'MODBUS_TCP_SECURE',
    label: 'Modbus TCP Security (TLS)',
    description: 'Modbus Security，双向 TLS，默认端口 802',
  },
];

export default function DevicesPage({ devices, statuses, onChanged, onStatusesChanged }: Props) {
  const { message, notification } = AntApp.useApp();
  const [form] = Form.useForm<DeviceFormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Device | null>(null);
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const protocol = Form.useWatch('protocol', form) as ProtocolType | undefined;
  const secure = protocol === 'MODBUS_TCP_SECURE';

  const connected = useMemo(() => statuses.filter((item) => item.connected).length, [statuses]);
  const pointTotal = useMemo(() => devices.reduce((sum, item) => sum + item.pointCount, 0), [devices]);
  const secureTotal = useMemo(
    () => devices.filter((item) => item.protocol === 'MODBUS_TCP_SECURE').length,
    [devices],
  );

  useEffect(() => {
    if (!modalOpen) return;
    if (editing) {
      form.setFieldsValue({
        name: editing.name,
        protocol: editing.protocol,
        host: editing.host,
        port: editing.port,
        unitId: editing.unitId,
        connectTimeoutMs: editing.connectTimeoutMs,
        requestTimeoutMs: editing.requestTimeoutMs,
        enabled: editing.enabled,
        tlsClientCertPath: editing.tlsClientCertPath,
        tlsClientKeyPath: editing.tlsClientKeyPath,
        tlsCaCertPath: editing.tlsCaCertPath,
        tlsVerifyHostname: editing.tlsVerifyHostname,
        tlsKeyPassword: '',
        description: editing.description,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        protocol: 'MODBUS_TCP',
        host: '127.0.0.1',
        port: 502,
        unitId: 1,
        connectTimeoutMs: 3000,
        requestTimeoutMs: 3000,
        enabled: true,
        tlsVerifyHostname: true,
      });
    }
  }, [editing, form, modalOpen]);

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (device: Device) => {
    setEditing(device);
    setModalOpen(true);
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const payload: DevicePayload = {
        ...values,
        tlsKeyPassword: values.tlsKeyPassword?.trim() || undefined,
      };
      if (editing) {
        await api.put(`/api/devices/${editing.id}`, payload);
        message.success('设备配置已更新');
      } else {
        await api.post('/api/devices', payload);
        message.success('设备已创建');
      }
      setModalOpen(false);
      await onChanged();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (device: Device) => {
    setBusyId(device.id);
    try {
      await api.delete(`/api/devices/${device.id}`);
      message.success(`设备 ${device.name} 已删除`);
      await onChanged();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setBusyId(null);
    }
  };

  const test = async (device: Device) => {
    setBusyId(device.id);
    try {
      const result = await api.post<ConnectionTestResult>(`/api/devices/${device.id}/test`);
      if (result.success) {
        notification.success({
          message: '连接测试成功',
          description: `${device.host}:${device.port} · ${result.latencyMs} ms${
            result.secure ? ' · TLS 握手完成' : ''
          }`,
        });
      } else {
        notification.error({
          message: '连接测试失败',
          description: result.message,
          duration: 8,
        });
      }
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setBusyId(null);
      await onStatusesChanged();
    }
  };

  const toggleConnection = async (device: Device, connect: boolean) => {
    setBusyId(device.id);
    try {
      await api.post(`/api/devices/${device.id}/${connect ? 'connect' : 'disconnect'}`);
      message.success(connect ? `已连接 ${device.name}` : `已断开 ${device.name}`);
      await onStatusesChanged();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setBusyId(null);
    }
  };

  const columns: ColumnsType<Device> = [
    {
      title: '设备',
      key: 'device',
      width: 220,
      render: (_, device) => (
        <Space direction="vertical" size={1}>
          <Space size={7}>
            <span className="device-name">{device.name}</span>
            {device.protocol === 'MODBUS_TCP_SECURE' ? (
              <Tooltip title="Modbus TCP Security / 双向 TLS">
                <LockOutlined className="secure-icon" />
              </Tooltip>
            ) : null}
          </Space>
          <Typography.Text type="secondary" className="small-muted">
            {device.description || '未填写描述'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '协议',
      dataIndex: 'protocol',
      width: 180,
      render: (value: ProtocolType) => (
        <Tag color={value === 'MODBUS_TCP_SECURE' ? 'gold' : 'blue'}>
          {protocolLabel(value)}
        </Tag>
      ),
    },
    {
      title: '端点',
      key: 'endpoint',
      width: 190,
      render: (_, device) => (
        <span className="mono">
          {device.host}:{device.port}
        </span>
      ),
    },
    {
      title: '从站',
      dataIndex: 'unitId',
      width: 80,
      align: 'center',
      render: (value: number) => <span className="mono">{value}</span>,
    },
    {
      title: '点位',
      dataIndex: 'pointCount',
      width: 70,
      align: 'center',
    },
    {
      title: '状态',
      key: 'status',
      width: 145,
      render: (_, device) => {
        const status = statusByDevice(statuses, device.id);
        if (!device.enabled) return <Badge status="default" text="已禁用" />;
        if (status?.connected) {
          return (
            <Tooltip title={status.connectedAt ? `连接于 ${formatTime(status.connectedAt)}` : ''}>
              <Badge status="success" text="已连接" />
            </Tooltip>
          );
        }
        if (status?.lastError) {
          return (
            <Tooltip title={status.lastError}>
              <Badge status="error" text="连接异常" />
            </Tooltip>
          );
        }
        return <Badge status="default" text="未连接" />;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 290,
      fixed: 'right',
      render: (_, device) => {
        const status = statusByDevice(statuses, device.id);
        return (
          <Space size={0} wrap>
            <Button
              type="link"
              size="small"
              icon={<ThunderboltOutlined />}
              loading={busyId === device.id}
              onClick={() => test(device)}
            >
              测试
            </Button>
            <Button
              type="link"
              size="small"
              icon={status?.connected ? <DisconnectOutlined /> : <LinkOutlined />}
              loading={busyId === device.id}
              onClick={() => toggleConnection(device, !status?.connected)}
            >
              {status?.connected ? '断开' : '连接'}
            </Button>
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(device)}>
              编辑
            </Button>
            <Popconfirm
              title="删除设备？"
              description="设备下的点位和 BACnet 映射也会删除。"
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={() => remove(device)}
            >
              <Button type="link" danger size="small" icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <>
      <PageHeading
        eyebrow="01 · DEVICE INVENTORY"
        title="设备配置"
        description="维护 Modbus TCP 与 Modbus TCP Security 设备的网络、超时和证书参数。连接按需建立并保持。"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增设备
          </Button>
        }
      />

      <Row gutter={[14, 14]} className="stat-row">
        <Col xs={12} md={6}>
          <Card className="stat-card">
            <div className="stat-icon"><CloudServerOutlined /></div>
            <div className="stat-value">{devices.length}</div>
            <div className="stat-label">设备总数</div>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="stat-card">
            <div className="stat-icon success"><CheckCircleOutlined /></div>
            <div className="stat-value">{connected}</div>
            <div className="stat-label">已建立连接</div>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="stat-card">
            <div className="stat-icon violet"><ApiOutlined /></div>
            <div className="stat-value">{pointTotal}</div>
            <div className="stat-label">已配置点位</div>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="stat-card">
            <div className="stat-icon amber"><SafetyCertificateOutlined /></div>
            <div className="stat-value">{secureTotal}</div>
            <div className="stat-label">TLS 安全连接</div>
          </Card>
        </Col>
      </Row>

      <Card
        className="content-card"
        title="设备清单"
        extra={<span className="card-hint">连接状态每 10 秒刷新</span>}
      >
        {devices.length === 0 ? (
          <div className="empty-panel">
            <CloudServerOutlined className="empty-icon" />
            <Typography.Title level={4}>还没有设备</Typography.Title>
            <Typography.Paragraph type="secondary">
              添加第一台 Modbus TCP 设备，然后创建点位并读取实时值。
            </Typography.Paragraph>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              添加设备
            </Button>
          </div>
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={devices}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            scroll={{ x: 1140 }}
            size="middle"
          />
        )}
      </Card>

      <Modal
        title={editing ? `编辑设备 · ${editing.name}` : '新增 Modbus 设备'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        confirmLoading={saving}
        width={760}
        forceRender
        className="form-modal"
      >
        <Form form={form} layout="vertical">
          <Divider titlePlacement="start" plain>基础信息</Divider>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item
                name="name"
                label="设备名称"
                rules={[{ required: true, message: '请输入设备名称' }]}
              >
                <Input placeholder="例如：1# 冷水机组 PLC" maxLength={120} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="protocol" label="协议" rules={[{ required: true }]}>
                <Select
                  options={protocolOptions}
                  optionRender={(option) => (
                    <Space direction="vertical" size={0}>
                      <span>{option.data.label}</span>
                      <span className="select-option-hint">{option.data.description}</span>
                    </Space>
                  )}
                  onChange={(value: ProtocolType) => {
                    const port = form.getFieldValue('port');
                    if (!port || port === 502 || port === 802) {
                      form.setFieldValue('port', value === 'MODBUS_TCP_SECURE' ? 802 : 502);
                    }
                  }}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={14}>
              <Form.Item
                name="host"
                label="IP 地址 / 主机名"
                rules={[{ required: true, message: '请输入 IP 或主机名' }]}
              >
                <Input placeholder="192.168.1.10" className="mono" />
              </Form.Item>
            </Col>
            <Col xs={12} md={5}>
              <Form.Item name="port" label="端口" rules={[{ required: true, message: '端口' }]}>
                <InputNumber min={1} max={65535} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={5}>
              <Form.Item name="unitId" label="从站地址" rules={[{ required: true, message: '从站地址' }]}>
                <InputNumber min={0} max={255} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider titlePlacement="start" plain>连接与超时</Divider>
          <Row gutter={16}>
            <Col xs={12} md={6}>
              <Form.Item name="connectTimeoutMs" label="连接超时 (ms)">
                <InputNumber min={100} max={60000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="requestTimeoutMs" label="请求超时 (ms)">
                <InputNumber min={100} max={120000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="enabled" label="启用" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <div className="form-hint compact">
                修改配置会自动断开旧连接，下一次读取时重新建链。
              </div>
            </Col>
          </Row>

          {secure ? (
            <>
              <Divider titlePlacement="start" plain>Modbus TCP Security · 双向 TLS</Divider>
              <Alert
                type="info"
                showIcon
                className="form-alert"
                message="服务端必须要求客户端证书"
                description="请填写 PEM 格式的客户端证书、私钥和 CA 证书绝对路径。私钥支持 PKCS#8、PKCS#1 RSA/EC，可加密。"
              />
              <Form.Item
                name="tlsClientCertPath"
                label="客户端证书路径 (PEM)"
                rules={[{ required: true, message: 'TLS 模式必须填写客户端证书' }]}
              >
                <Input placeholder="D:\certs\client.crt" className="mono" />
              </Form.Item>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="tlsClientKeyPath"
                    label="客户端私钥路径 (PEM)"
                    rules={[{ required: true, message: 'TLS 模式必须填写客户端私钥' }]}
                  >
                    <Input placeholder="D:\certs\client.key" className="mono" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="tlsKeyPassword"
                    label="私钥密码"
                    extra={editing?.tlsKeyPasswordSet ? '已保存密码；留空表示保持不变' : '未加密私钥可留空'}
                  >
                    <Input.Password placeholder={editing?.tlsKeyPasswordSet ? '••••••••' : '可选'} />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item
                name="tlsCaCertPath"
                label="CA 证书路径 (PEM)"
                rules={[{ required: true, message: 'TLS 模式必须填写 CA 证书' }]}
              >
                <Input placeholder="D:\certs\ca.crt" className="mono" />
              </Form.Item>
              <Form.Item name="tlsVerifyHostname" label="校验证书主机名" valuePropName="checked">
                <Switch checkedChildren="校验" unCheckedChildren="不校验" />
              </Form.Item>
            </>
          ) : null}

          <Divider titlePlacement="start" plain>备注</Divider>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} maxLength={1000} showCount placeholder="设备位置、用途、维护信息..." />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
