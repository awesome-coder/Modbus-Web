import { useEffect, useMemo, useState } from 'react';
import {
  ApiOutlined,
  DeleteOutlined,
  EditOutlined,
  ExperimentOutlined,
  PlusOutlined,
  ReadOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import {
  App as AntApp,
  Button,
  Card,
  Col,
  Divider,
  Empty,
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
import {
  accessFallback,
  areaColor,
  areaFallback,
  areaFunctionCode,
  dataTypeFallback,
  errorMessage,
  layoutOf,
  protocolLabel,
} from '../helpers';
import type {
  ByteOrder,
  DataType,
  Device,
  Point,
  PointAccess,
  PointArea,
  PointPayload,
  PointReadResult,
  WordOrder,
} from '../types';

interface Props {
  devices: Device[];
  onDevicesChanged: () => Promise<void> | void;
}

type PointFormValues = PointPayload;

const areaOptions = (Object.keys(areaFallback) as PointArea[]).map((value) => ({
  value,
  label: `${String(areaFunctionCode[value]).padStart(2, '0')} · ${areaFallback[value]}`,
}));

const dataTypeOptions = (Object.keys(dataTypeFallback) as DataType[]).map((value) => ({
  value,
  label: value,
}));

const accessOptions = (Object.keys(accessFallback) as PointAccess[]).map((value) => ({
  value,
  label: accessFallback[value],
}));

const endiannessPresets: Array<{
  value: string;
  label: string;
  byteOrder: ByteOrder;
  wordOrder: WordOrder;
  example: string;
}> = [
  { value: 'ABCD', label: 'ABCD · 大端', byteOrder: 'BIG_ENDIAN', wordOrder: 'BIG_ENDIAN', example: '3F 80 00 00 = 1.0' },
  { value: 'BADC', label: 'BADC · 字节交换', byteOrder: 'LITTLE_ENDIAN', wordOrder: 'BIG_ENDIAN', example: '80 3F 00 00 = 1.0' },
  { value: 'CDAB', label: 'CDAB · 字交换', byteOrder: 'BIG_ENDIAN', wordOrder: 'LITTLE_ENDIAN', example: '00 00 3F 80 = 1.0' },
  { value: 'DCBA', label: 'DCBA · 全交换', byteOrder: 'LITTLE_ENDIAN', wordOrder: 'LITTLE_ENDIAN', example: '00 00 80 3F = 1.0' },
];

const referenceBases: Record<PointArea, number> = {
  COIL: 1,
  DISCRETE_INPUT: 10001,
  HOLDING_REGISTER: 40001,
  INPUT_REGISTER: 30001,
};

const fixedRegisterCounts: Record<DataType, number> = {
  BOOL: 1,
  INT16: 1,
  UINT16: 1,
  INT32: 2,
  UINT32: 2,
  INT64: 4,
  UINT64: 4,
  FLOAT32: 2,
  FLOAT64: 4,
  STRING: 0,
  HEX: 0,
};

function referenceOf(area: PointArea, address: number | null | undefined): string {
  if (address === null || address === undefined) return '-';
  return String(referenceBases[area] + address).padStart(5, '0');
}

export default function PointsPage({ devices, onDevicesChanged }: Props) {
  const { message, notification } = AntApp.useApp();
  const [form] = Form.useForm<PointFormValues>();
  const [deviceId, setDeviceId] = useState<number | null>(null);
  const [points, setPoints] = useState<Point[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Point | null>(null);
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const area = Form.useWatch('area', form) as PointArea | undefined;
  const dataType = Form.useWatch('dataType', form) as DataType | undefined;
  const address = Form.useWatch('address', form) as number | undefined;
  const byteOrder = (Form.useWatch('byteOrder', form) as ByteOrder | undefined) ?? 'BIG_ENDIAN';
  const wordOrder = (Form.useWatch('wordOrder', form) as WordOrder | undefined) ?? 'BIG_ENDIAN';
  const registerLength = Form.useWatch('registerLength', form) as number | null | undefined;
  const selectedDevice = devices.find((device) => device.id === deviceId) ?? null;
  const layout = layoutOf(byteOrder, wordOrder);
  const variableLength = dataType === 'STRING' || dataType === 'HEX';
  const registerCount = variableLength
    ? Math.max(1, registerLength ?? 1)
    : dataType
      ? fixedRegisterCounts[dataType]
      : 1;
  const readOnlyArea = area === 'DISCRETE_INPUT' || area === 'INPUT_REGISTER';

  useEffect(() => {
    if (devices.length === 0) {
      setDeviceId(null);
      return;
    }
    if (!deviceId || !devices.some((device) => device.id === deviceId)) {
      setDeviceId(devices[0].id);
    }
  }, [deviceId, devices]);

  const loadPoints = async (id: number | null) => {
    if (!id) {
      setPoints([]);
      return;
    }
    setLoading(true);
    try {
      setPoints(await api.get<Point[]>(`/api/devices/${id}/points`));
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPoints(deviceId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deviceId]);

  useEffect(() => {
    if (!modalOpen) return;
    if (editing) {
      form.setFieldsValue({
        code: editing.code,
        name: editing.name,
        area: editing.area,
        address: editing.address,
        unitId: editing.unitId,
        dataType: editing.dataType,
        byteOrder: editing.byteOrder,
        wordOrder: editing.wordOrder,
        bitIndex: editing.bitIndex,
        registerLength: editing.registerLength,
        stringCharset: editing.stringCharset,
        scale: editing.scale,
        offset: editing.offset,
        decimals: editing.decimals,
        unit: editing.unit,
        access: editing.access,
        enabled: editing.enabled,
        description: editing.description,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        area: 'HOLDING_REGISTER',
        address: 0,
        dataType: 'UINT16',
        byteOrder: 'BIG_ENDIAN',
        wordOrder: 'BIG_ENDIAN',
        stringCharset: 'UTF_8',
        scale: 1,
        offset: 0,
        access: 'READ_ONLY',
        enabled: true,
      });
    }
  }, [editing, form, modalOpen]);

  const handleAreaChange = (value: PointArea) => {
    if (value === 'COIL' || value === 'DISCRETE_INPUT') {
      form.setFieldsValue({
        dataType: 'BOOL',
        bitIndex: null,
        registerLength: null,
        access: value === 'DISCRETE_INPUT' ? 'READ_ONLY' : form.getFieldValue('access') ?? 'READ_ONLY',
      });
    } else {
      if (value === 'INPUT_REGISTER') form.setFieldValue('access', 'READ_ONLY');
      if (form.getFieldValue('dataType') === 'BOOL') form.setFieldValue('bitIndex', 0);
    }
  };

  const handleDataTypeChange = (value: DataType) => {
    if (value === 'STRING' || value === 'HEX') {
      if (!form.getFieldValue('registerLength')) {
        form.setFieldValue('registerLength', value === 'STRING' ? 8 : 4);
      }
      form.setFieldValue('bitIndex', null);
    } else {
      form.setFieldValue('registerLength', null);
      const registerArea = form.getFieldValue('area') === 'HOLDING_REGISTER' || form.getFieldValue('area') === 'INPUT_REGISTER';
      form.setFieldValue('bitIndex', value === 'BOOL' && registerArea ? 0 : null);
    }
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const payload: PointPayload = {
        ...values,
        unit: values.unit?.trim() || null,
        description: values.description?.trim() || null,
        unitId: values.unitId ?? null,
        registerLength: values.registerLength ?? null,
        bitIndex: values.bitIndex ?? null,
        decimals: values.decimals ?? null,
      };
      setSaving(true);
      if (editing) {
        await api.put(`/api/points/${editing.id}`, payload);
        message.success('点位已更新');
      } else if (deviceId) {
        await api.post(`/api/devices/${deviceId}/points`, payload);
        message.success('点位已创建');
      }
      setModalOpen(false);
      await loadPoints(deviceId);
      await onDevicesChanged();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (point: Point) => {
    setBusyId(point.id);
    try {
      await api.delete(`/api/points/${point.id}`);
      message.success(`点位 ${point.code} 已删除`);
      await loadPoints(deviceId);
      await onDevicesChanged();
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setBusyId(null);
    }
  };

  const quickRead = async (point: Point) => {
    setBusyId(point.id);
    try {
      const result = await api.post<PointReadResult>(`/api/points/${point.id}/read`);
      if (result.success) {
        notification.success({
          message: `${point.name} = ${result.value}${point.unit ? ` ${point.unit}` : ''}`,
          description: `${point.addressReference} · ${result.rawHex} · ${result.elapsedMs} ms`,
        });
      } else {
        notification.error({
          message: `读取 ${point.code} 失败`,
          description: result.error,
          duration: 7,
        });
      }
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setBusyId(null);
    }
  };

  const columns: ColumnsType<Point> = [
    {
      title: '点位',
      key: 'point',
      width: 190,
      fixed: 'left',
      render: (_, point) => (
        <Space direction="vertical" size={0}>
          <Space size={6}>
            <span className="device-name">{point.name}</span>
            {!point.enabled ? <Tag>禁用</Tag> : null}
          </Space>
          <span className="mono small-muted">{point.code}</span>
        </Space>
      ),
    },
    {
      title: '区域',
      dataIndex: 'area',
      width: 145,
      render: (value: PointArea) => (
        <Tag color={areaColor[value]}>
          {String(areaFunctionCode[value]).padStart(2, '0')} {areaFallback[value]}
        </Tag>
      ),
    },
    {
      title: '地址',
      key: 'address',
      width: 110,
      render: (_, point) => (
        <Space direction="vertical" size={0}>
          <span className="mono strong">{point.addressReference}</span>
          <span className="small-muted">#{point.address}</span>
        </Space>
      ),
    },
    {
      title: '类型 / 端序',
      key: 'type',
      width: 155,
      render: (_, point) => (
        <Space direction="vertical" size={2}>
          <Tag color="geekblue">{point.dataType}</Tag>
          <span className="mono small-muted">
            {layoutOf(point.byteOrder, point.wordOrder)}
            {point.bitIndex !== null ? ` · bit ${point.bitIndex}` : ''}
            {` · ${point.registerCount} reg`}
          </span>
        </Space>
      ),
    },
    {
      title: '缩放',
      key: 'scale',
      width: 110,
      render: (_, point) =>
        point.scale === 1 && point.offset === 0 ? (
          <span className="small-muted">原始值</span>
        ) : (
          <span className="mono small">
            x{point.scale} {point.offset >= 0 ? '+' : ''}
            {point.offset}
          </span>
        ),
    },
    {
      title: '权限',
      dataIndex: 'access',
      width: 80,
      render: (value: PointAccess) => (
        <Tag color={value === 'READ_ONLY' ? 'default' : value === 'READ_WRITE' ? 'green' : 'orange'}>
          {accessFallback[value]}
        </Tag>
      ),
    },
    {
      title: 'BACnet',
      key: 'bacnet',
      width: 190,
      render: (_, point) =>
        point.bacnetMapping ? (
          <Space size={4}>
            <ApiOutlined className="bacnet-icon" />
            <span className="mono small">{point.bacnetMapping.objectId}</span>
          </Space>
        ) : (
          <span className="small-muted">未映射</span>
        ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 230,
      fixed: 'right',
      render: (_, point) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={<ReadOutlined />}
            loading={busyId === point.id}
            onClick={() => quickRead(point)}
          >
            读取
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => { setEditing(point); setModalOpen(true); }}>
            编辑
          </Button>
          <Popconfirm
            title="删除点位？"
            description="关联的 BACnet 映射也会删除。"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => remove(point)}
          >
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageHeading
        eyebrow="02 · POINT MODEL"
        title="点位配置"
        description="按区域、数据类型、字节序和字序建模。地址输入为 0-based 协议地址，右侧同步显示传统参考地址。"
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={!selectedDevice}
            onClick={() => { setEditing(null); setModalOpen(true); }}
          >
            新增点位
          </Button>
        }
      />

      <Card className="content-card device-switcher">
        <div className="device-switcher-inner">
          <div>
            <div className="eyebrow">当前设备</div>
            <Select
              className="device-select"
              value={deviceId ?? undefined}
              onChange={(value) => setDeviceId(value)}
              options={devices.map((device) => ({
                value: device.id,
                label: `${device.name} · ${protocolLabel(device.protocol)} · ${device.host}:${device.port}`,
              }))}
              placeholder="选择设备"
              notFoundContent={<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无设备" />}
            />
          </div>
          {selectedDevice ? (
            <Space size={8} wrap>
              <Tag color={selectedDevice.protocol === 'MODBUS_TCP_SECURE' ? 'gold' : 'blue'}>
                {protocolLabel(selectedDevice.protocol)}
              </Tag>
              <Tag>{selectedDevice.host}:{selectedDevice.port}</Tag>
              <Tag>Unit {selectedDevice.unitId}</Tag>
              <Tag color="cyan">{points.length} 点位</Tag>
            </Space>
          ) : null}
        </div>
      </Card>

      <Card
        className="content-card"
        title={<Space><TagsOutlined /><span>点位清单</span></Space>}
        extra={<Button size="small" onClick={() => loadPoints(deviceId)} loading={loading}>刷新</Button>}
      >
        {!selectedDevice ? (
          <Empty description="请先在“设备配置”中创建并选择设备" />
        ) : points.length === 0 ? (
          <div className="empty-panel">
            <ExperimentOutlined className="empty-icon" />
            <Typography.Title level={4}>还没有点位</Typography.Title>
            <Typography.Paragraph type="secondary">
              支持 BOOL、16/32/64 位整数、单双精度浮点、字符串和原始十六进制，并可设置四种端序布局。
            </Typography.Paragraph>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); setModalOpen(true); }}>
              创建第一个点位
            </Button>
          </div>
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={points}
            loading={loading}
            size="middle"
            scroll={{ x: 1240 }}
            pagination={{ pageSize: 12, showSizeChanger: false }}
          />
        )}
      </Card>

      <Modal
        title={editing ? `编辑点位 · ${editing.code}` : `新增点位 · ${selectedDevice?.name ?? ''}`}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        confirmLoading={saving}
        width={880}
        forceRender
        className="form-modal"
      >
        <Form form={form} layout="vertical">
          <Divider titlePlacement="start" plain>标识与位置</Divider>
          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Form.Item
                name="code"
                label="点位编码"
                rules={[
                  { required: true, message: '请输入点位编码' },
                  { pattern: /^[A-Za-z0-9_.:-]+$/, message: '仅支持字母、数字、下划线、点、冒号、横线' },
                ]}
              >
                <Input placeholder="TEMP_01" className="mono" maxLength={64} />
              </Form.Item>
            </Col>
            <Col xs={24} md={10}>
              <Form.Item name="name" label="点位名称" rules={[{ required: true, message: '请输入点位名称' }]}>
                <Input placeholder="1# 机组供水温度" maxLength={160} />
              </Form.Item>
            </Col>
            <Col xs={12} md={3}>
              <Form.Item name="enabled" label="启用" valuePropName="checked">
                <Switch size="small" />
              </Form.Item>
            </Col>
            <Col xs={12} md={3}>
              <Form.Item name="access" label="权限">
                <Select options={readOnlyArea ? [accessOptions[0]] : accessOptions} disabled={readOnlyArea} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="area" label="区域 / 功能码" rules={[{ required: true }]}>
                <Select options={areaOptions} onChange={handleAreaChange} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="address" label="协议地址 (0-based)" rules={[{ required: true, message: '请输入地址' }]}>
                <InputNumber
                  min={0}
                  max={65535}
                  style={{ width: '100%' }}
                  addonAfter={<span className="address-reference">参考 {referenceOf(area ?? 'HOLDING_REGISTER', address)}</span>}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="unitId" label="从站地址覆盖">
                <InputNumber
                  min={0}
                  max={255}
                  style={{ width: '100%' }}
                  placeholder={`默认 ${selectedDevice?.unitId ?? 1}`}
                />
              </Form.Item>
            </Col>
          </Row>

          <Divider titlePlacement="start" plain>数据类型与转换</Divider>
          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Form.Item name="dataType" label="数据类型" rules={[{ required: true }]}>
                <Select
                  options={dataTypeOptions}
                  onChange={handleDataTypeChange}
                  disabled={area === 'COIL' || area === 'DISCRETE_INPUT'}
                />
              </Form.Item>
            </Col>
            {dataType === 'BOOL' && (area === 'HOLDING_REGISTER' || area === 'INPUT_REGISTER') ? (
              <Col xs={12} md={5}>
                <Form.Item name="bitIndex" label="位索引" rules={[{ required: true }]}>
                  <InputNumber min={0} max={15} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            ) : null}
            {variableLength ? (
              <>
                <Col xs={12} md={5}>
                  <Form.Item name="registerLength" label="寄存器数量" rules={[{ required: true, message: '必填' }]}>
                    <InputNumber min={1} max={125} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={12} md={6}>
                  <Form.Item name="stringCharset" label="字符串编码">
                    <Select
                      disabled={dataType !== 'STRING'}
                      options={[
                        { value: 'UTF_8', label: 'UTF-8' },
                        { value: 'ASCII', label: 'ASCII' },
                        { value: 'UTF_16BE', label: 'UTF-16BE' },
                        { value: 'UTF_16LE', label: 'UTF-16LE' },
                      ]}
                    />
                  </Form.Item>
                </Col>
              </>
            ) : null}
            <Col xs={24} md={6}>
              <Form.Item label="端序预设">
                <Select
                  value={layout}
                  options={endiannessPresets.map(({ value, label }) => ({ value, label }))}
                  onChange={(value) => {
                    const preset = endiannessPresets.find((item) => item.value === value);
                    if (preset) form.setFieldsValue({ byteOrder: preset.byteOrder, wordOrder: preset.wordOrder });
                  }}
                />
              </Form.Item>
            </Col>
            <Col xs={12} md={5}>
              <Form.Item name="byteOrder" label="字节序">
                <Select
                  options={[
                    { value: 'BIG_ENDIAN', label: '大端 AB' },
                    { value: 'LITTLE_ENDIAN', label: '小端 BA' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={12} md={5}>
              <Form.Item name="wordOrder" label="字序">
                <Select
                  options={[
                    { value: 'BIG_ENDIAN', label: '高字在前' },
                    { value: 'LITTLE_ENDIAN', label: '低字在前' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={14}>
              <div className="endian-preview">
                <span className="mono layout-badge">{layout}</span>
                <span>
                  {endiannessPresets.find((item) => item.value === layout)?.example} · 占用 <b>{registerCount}</b> 个寄存器
                </span>
              </div>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={12} md={5}>
              <Form.Item name="scale" label="缩放系数" rules={[{ required: true }]}>
                <InputNumber step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={5}>
              <Form.Item name="offset" label="偏移量" rules={[{ required: true }]}>
                <InputNumber step={0.1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={4}>
              <Form.Item name="decimals" label="小数位">
                <InputNumber min={0} max={12} style={{ width: '100%' }} placeholder="自动" />
              </Form.Item>
            </Col>
            <Col xs={12} md={4}>
              <Form.Item name="unit" label="工程单位">
                <Input placeholder="°C" maxLength={32} />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <div className="form-hint">工程值 = 原始值 x 缩放系数 + 偏移量。写入时自动逆变换。</div>
            </Col>
          </Row>

          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} maxLength={1000} showCount placeholder="点位用途、报警说明等" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
