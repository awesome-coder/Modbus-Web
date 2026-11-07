import { useEffect, useMemo, useState } from 'react';
import {
  ApiOutlined,
  DeleteOutlined,
  EditOutlined,
  ExportOutlined,
  EyeOutlined,
  PlusOutlined,
  RobotOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import {
  Alert,
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
import { api, downloadFile } from '../api';
import PageHeading from '../components/PageHeading';
import { bacnetFallback, errorMessage, protocolLabel } from '../helpers';
import type {
  BacnetGenerateResult,
  BacnetMapping,
  BacnetObjectType,
  BacnetObjectView,
  BacnetPreview,
  Device,
  Point,
} from '../types';

interface Props {
  devices: Device[];
}

interface MappingFormValues {
  pointId: number;
  enabled: boolean;
  objectType: BacnetObjectType;
  objectInstance: number;
  objectName: string;
  units?: string | null;
  covIncrement?: number | null;
  description?: string | null;
}

const objectTypeOptions = (Object.keys(bacnetFallback) as BacnetObjectType[]).map((value) => ({
  value,
  label: `${value} · ${bacnetFallback[value]}`,
}));

function isBinary(type: BacnetObjectType) {
  return type.startsWith('BINARY');
}

function compatibleTypes(point: Point | undefined): BacnetObjectType[] {
  if (!point) return [];
  if (point.dataType === 'BOOL') return ['BINARY_INPUT', 'BINARY_OUTPUT', 'BINARY_VALUE'];
  if (point.dataType === 'STRING' || point.dataType === 'HEX') return [];
  return [
    'ANALOG_INPUT',
    'ANALOG_OUTPUT',
    'ANALOG_VALUE',
    'MULTI_STATE_INPUT',
    'MULTI_STATE_OUTPUT',
    'MULTI_STATE_VALUE',
  ];
}

function suggestedObjectType(point: Point | undefined): BacnetObjectType {
  if (!point) return 'ANALOG_INPUT';
  if (point.dataType === 'BOOL') return point.writable ? 'BINARY_VALUE' : 'BINARY_INPUT';
  return point.writable ? 'ANALOG_VALUE' : 'ANALOG_INPUT';
}

export default function BacnetPage({ devices }: Props) {
  const { message, notification } = AntApp.useApp();
  const [form] = Form.useForm<MappingFormValues>();
  const [deviceId, setDeviceId] = useState<number | null>(null);
  const [mappings, setMappings] = useState<BacnetMapping[]>([]);
  const [points, setPoints] = useState<Point[]>([]);
  const [preview, setPreview] = useState<BacnetPreview | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [includeValues, setIncludeValues] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<BacnetMapping | null>(null);
  const [saving, setSaving] = useState(false);

  const selectedDevice = devices.find((device) => device.id === deviceId) ?? null;
  const pointById = useMemo(() => new Map(points.map((point) => [point.id, point])), [points]);
  const selectedPointId = Form.useWatch('pointId', form);
  const selectedPoint = selectedPointId ? pointById.get(selectedPointId) : undefined;

  useEffect(() => {
    if (devices.length === 0) {
      setDeviceId(null);
      return;
    }
    if (!deviceId || !devices.some((device) => device.id === deviceId)) {
      setDeviceId(devices[0].id);
    }
  }, [deviceId, devices]);

  const load = async (id: number | null) => {
    if (!id) {
      setMappings([]);
      setPoints([]);
      return;
    }
    setLoading(true);
    try {
      const [mappingData, pointData] = await Promise.all([
        api.get<BacnetMapping[]>(`/api/devices/${id}/bacnet/mappings`),
        api.get<Point[]>(`/api/devices/${id}/points`),
      ]);
      setMappings(mappingData);
      setPoints(pointData);
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setPreview(null);
    setWarnings([]);
    load(deviceId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deviceId]);

  const generate = async () => {
    if (!deviceId) return;
    setGenerating(true);
    try {
      const result = await api.post<BacnetGenerateResult>(`/api/devices/${deviceId}/bacnet/generate`);
      setWarnings(result.warnings);
      setMappings(result.mappings);
      message.success(`已生成 ${result.created} 个 BACnet 映射，跳过 ${result.skipped} 个`);
      if (result.warnings.length > 0) {
        notification.warning({
          message: '部分点位未映射',
          description: result.warnings.join('；'),
          duration: 8,
        });
      }
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setGenerating(false);
    }
  };

  const loadPreview = async () => {
    if (!deviceId) return;
    setPreviewing(true);
    try {
      const result = await api.get<BacnetPreview>(
        `/api/devices/${deviceId}/bacnet/preview?includeValues=${includeValues}`,
      );
      setPreview(result);
      setWarnings(result.warnings);
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setPreviewing(false);
    }
  };

  const exportJson = async () => {
    if (!deviceId) return;
    try {
      await downloadFile(
        `/api/devices/${deviceId}/bacnet/export?includeValues=${includeValues}`,
        `bacnet-device-${deviceId}.json`,
      );
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  useEffect(() => {
    if (!modalOpen) return;
    if (editing) {
      form.setFieldsValue({
        pointId: editing.pointId,
        enabled: editing.enabled,
        objectType: editing.objectType,
        objectInstance: editing.objectInstance,
        objectName: editing.objectName,
        units: editing.units,
        covIncrement: editing.covIncrement,
        description: editing.description,
      });
    } else {
      const point = points.find(
        (item) => !item.bacnetMapping && item.dataType !== 'STRING' && item.dataType !== 'HEX',
      );
      form.resetFields();
      form.setFieldsValue({
        pointId: point?.id as number,
        enabled: true,
        objectType: suggestedObjectType(point),
        objectInstance: 1,
        objectName: point && deviceId ? `D${deviceId}_${point.code}` : '',
        units: point?.unit ?? undefined,
      });
    }
  }, [editing, form, modalOpen, points, deviceId]);

  useEffect(() => {
    if (!modalOpen || !selectedPoint || editing) return;
    form.setFieldValue('units', selectedPoint.unit ?? undefined);
    form.setFieldValue('objectName', deviceId ? `D${deviceId}_${selectedPoint.code}` : '');
  }, [selectedPoint, modalOpen, editing, form, deviceId]);

  const nextInstance = (type: BacnetObjectType): number => {
    const max = mappings
      .filter((mapping) => mapping.objectType === type)
      .reduce((value, mapping) => Math.max(value, mapping.objectInstance), 0);
    return max + 1;
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      if (editing) {
        await api.put(`/api/bacnet/mappings/${editing.id}`, values);
        message.success('BACnet 映射已更新');
      } else {
        await api.post('/api/bacnet/mappings', values);
        message.success('BACnet 映射已创建');
      }
      setModalOpen(false);
      await load(deviceId);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (mapping: BacnetMapping) => {
    try {
      await api.delete(`/api/bacnet/mappings/${mapping.id}`);
      message.success(`映射 ${mapping.objectId} 已删除`);
      await load(deviceId);
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const mappingColumns: ColumnsType<BacnetMapping> = [
    {
      title: 'BACnet 对象',
      key: 'object',
      width: 240,
      render: (_, mapping) => (
        <Space direction="vertical" size={0}>
          <Space size={6}>
            <ApiOutlined className="bacnet-icon" />
            <span className="mono strong">{mapping.objectId}</span>
            {!mapping.enabled ? <Tag>禁用</Tag> : null}
          </Space>
          <span className="small-muted">{mapping.objectName}</span>
        </Space>
      ),
    },
    {
      title: '对象类型',
      dataIndex: 'objectType',
      width: 170,
      render: (value: BacnetObjectType) => (
        <Tag color={isBinary(value) ? 'green' : 'geekblue'}>{bacnetFallback[value]}</Tag>
      ),
    },
    {
      title: '来源点位',
      key: 'point',
      width: 190,
      render: (_, mapping) => (
        <Space direction="vertical" size={0}>
          <span>{mapping.pointName}</span>
          <span className="mono small-muted">{mapping.pointCode}</span>
        </Space>
      ),
    },
    {
      title: '单位',
      dataIndex: 'units',
      width: 90,
      render: (value: string | null) => value || '-',
    },
    {
      title: 'COV 增量',
      dataIndex: 'covIncrement',
      width: 100,
      render: (value: number | null) => value ?? '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, mapping) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => { setEditing(mapping); setModalOpen(true); }}
          >
            编辑
          </Button>
          <Popconfirm
            title="删除此 BACnet 映射？"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => remove(mapping)}
          >
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const previewColumns: ColumnsType<BacnetObjectView> = [
    {
      title: '对象标识',
      dataIndex: 'objectId',
      width: 190,
      render: (value: string) => <span className="mono strong">{value}</span>,
    },
    { title: '对象名称', dataIndex: 'objectName', width: 220 },
    {
      title: '来源点位',
      key: 'point',
      width: 160,
      render: (_, object) => (
        <Space direction="vertical" size={0}>
          <span>{object.pointName}</span>
          <span className="mono small-muted">{object.pointCode}</span>
        </Space>
      ),
    },
    {
      title: 'Present Value',
      key: 'presentValue',
      width: 190,
      render: (_, object) =>
        object.presentValue === null || object.presentValue === undefined ? (
          <span className="small-muted">未读取</span>
        ) : (
          <span className="mono live-value small">{String(object.presentValue)}</span>
        ),
    },
    {
      title: '数据类型',
      dataIndex: 'presentValueType',
      width: 140,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: '单位',
      dataIndex: 'units',
      width: 80,
      render: (value: string | null) => value || '-',
    },
  ];

  const availablePoints = points.filter(
    (point) =>
      (!point.bacnetMapping || (editing && point.id === editing.pointId)) &&
      point.dataType !== 'STRING' &&
      point.dataType !== 'HEX',
  );

  return (
    <>
      <PageHeading
        eyebrow="05 · BACnet BRIDGE"
        title="BACnet 映射"
        description="把 Modbus 点位映射为标准 BACnet 对象。默认按数据类型和读写权限建议对象类型，并通过 JSON 契约对接外部 BACnet 网关。"
        extra={
          <Space wrap>
            <Button
              icon={<RobotOutlined />}
              loading={generating}
              disabled={!deviceId || points.length === 0}
              onClick={generate}
            >
              自动生成建议
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={!deviceId || availablePoints.length === 0}
              onClick={() => { setEditing(null); setModalOpen(true); }}
            >
              新增映射
            </Button>
          </Space>
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
          <Space wrap>
            {selectedDevice ? (
              <>
                <Tag color="geekblue">{mappings.length} 个对象</Tag>
                <Tag>{points.length} 个点位</Tag>
              </>
            ) : null}
            <Button icon={<EyeOutlined />} loading={previewing} disabled={!deviceId} onClick={loadPreview}>
              预览对象
            </Button>
            <Button icon={<ExportOutlined />} disabled={!deviceId} onClick={exportJson}>
              导出 JSON
            </Button>
          </Space>
        </div>
      </Card>

      {warnings.length > 0 ? (
        <Alert
          className="inline-alert"
          type="warning"
          showIcon
          closable
          onClose={() => setWarnings([])}
          message="映射提示"
          description={<ul className="warning-list">{warnings.map((item, index) => <li key={index}>{item}</li>)}</ul>}
        />
      ) : null}

      <Card
        className="content-card"
        title={<Space><ApiOutlined /><span>BACnet 对象清单</span></Space>}
        extra={<span className="card-hint">对象实例号在全局范围内唯一</span>}
      >
        {!selectedDevice ? (
          <Empty description="请先创建设备" />
        ) : mappings.length === 0 ? (
          <div className="empty-panel">
            <RobotOutlined className="empty-icon" />
            <Typography.Title level={4}>尚未建立映射</Typography.Title>
            <Typography.Paragraph type="secondary">
              点击“自动生成建议”，布尔点映射为 Binary 对象，数值点映射为 Analog 对象。STRING/HEX 需通过自定义 BacnetValueAdapter 扩展。
            </Typography.Paragraph>
            <Space>
              <Button type="primary" icon={<RobotOutlined />} onClick={generate} loading={generating}>
                自动生成
              </Button>
              <Button icon={<PlusOutlined />} onClick={() => { setEditing(null); setModalOpen(true); }}>
                手动添加
              </Button>
            </Space>
          </div>
        ) : (
          <Table
            rowKey="id"
            columns={mappingColumns}
            dataSource={mappings}
            loading={loading}
            size="middle"
            scroll={{ x: 980 }}
            pagination={{ pageSize: 12, showSizeChanger: false }}
          />
        )}
      </Card>

      {preview ? (
        <Card
          className="content-card"
          title={
            <Space>
              <EyeOutlined />
              <span>对象预览</span>
              <Tag color={preview.valuesIncluded ? 'success' : 'default'}>
                {preview.valuesIncluded ? '含实时值' : '仅模型'}
              </Tag>
            </Space>
          }
          extra={
            <Space>
              <span className="auto-refresh-label">读取实时值</span>
              <Switch size="small" checked={includeValues} onChange={setIncludeValues} />
              <Button size="small" onClick={loadPreview} loading={previewing}>刷新</Button>
            </Space>
          }
        >
          {preview.warnings.length > 0 ? (
            <Alert
              type="warning"
              showIcon
              className="inline-alert"
              message={`${preview.warnings.length} 个点位读取失败`}
              description={preview.warnings.join('；')}
            />
          ) : null}
          <Table
            rowKey="mappingId"
            columns={previewColumns}
            dataSource={preview.objects}
            size="small"
            pagination={false}
            scroll={{ x: 980 }}
          />
          <Divider titlePlacement="start" plain>外部网关 JSON 契约</Divider>
          <pre className="json-preview">{JSON.stringify(preview, null, 2)}</pre>
        </Card>
      ) : null}

      <Card className="content-card" title="对接说明">
        <Row gutter={[24, 16]}>
          <Col xs={24} md={8}>
            <Typography.Title level={5}>自动映射规则</Typography.Title>
            <Typography.Paragraph type="secondary">
              只读 BOOL 映射 BINARY_INPUT；可写 BOOL 映射 BINARY_VALUE；只读数值映射 ANALOG_INPUT；可写数值映射 ANALOG_VALUE。对象实例按类型自动分配。
            </Typography.Paragraph>
          </Col>
          <Col xs={24} md={8}>
            <Typography.Title level={5}>扩展点</Typography.Title>
            <Typography.Paragraph type="secondary">
              后端 BacnetValueAdapter 是 Spring 扩展接口，可替换对象建议和 Present Value 转换，例如自定义 STRING/多态对象策略。
            </Typography.Paragraph>
          </Col>
          <Col xs={24} md={8}>
            <Typography.Title level={5}>JSON 契约</Typography.Title>
            <Typography.Paragraph type="secondary">
              GET /api/devices/&#123;id&#125;/bacnet/export 返回对象标识、类型、实例、来源 Modbus 地址、端序和缩放信息，可供独立 BACnet 服务消费。
            </Typography.Paragraph>
          </Col>
        </Row>
      </Card>

      <Modal
        title={editing ? `编辑映射 · ${editing.objectId}` : '新增 BACnet 映射'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        confirmLoading={saving}
        width={720}
        forceRender
        className="form-modal"
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item name="pointId" label="来源点位" rules={[{ required: true, message: '请选择点位' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  disabled={!!editing}
                  options={availablePoints.map((point) => ({
                    value: point.id,
                    label: `${point.code} · ${point.name} (${point.dataType})`,
                  }))}
                  onChange={(value: number) => {
                    const point = pointById.get(value);
                    if (!point) return;
                    const objectType = suggestedObjectType(point);
                    form.setFieldsValue({
                      objectType,
                      objectInstance: nextInstance(objectType),
                      units: point.unit ?? undefined,
                      objectName: deviceId ? `D${deviceId}_${point.code}` : '',
                    });
                  }}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="enabled" label="启用" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="objectType" label="对象类型" rules={[{ required: true, message: '请选择对象类型' }]}>
                <Select
                  options={objectTypeOptions.filter((option) => compatibleTypes(selectedPoint).includes(option.value))}
                  onChange={(value: BacnetObjectType) => form.setFieldValue('objectInstance', nextInstance(value))}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="objectInstance" label="对象实例号" rules={[{ required: true, message: '请输入实例号' }]}>
                <InputNumber min={0} max={4194302} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24}>
              <Form.Item
                name="objectName"
                label="对象名称"
                rules={[
                  { required: true, message: '请输入对象名称' },
                  { max: 64, message: '最多 64 个字符' },
                ]}
              >
                <Input className="mono" placeholder="D1_TEMP_01" maxLength={64} />
              </Form.Item>
            </Col>
            <Col xs={12} md={8}>
              <Form.Item name="units" label="工程单位">
                <Input placeholder="°C" maxLength={64} />
              </Form.Item>
            </Col>
            <Col xs={12} md={8}>
              <Form.Item name="covIncrement" label="COV 增量">
                <InputNumber min={0} step={0.1} style={{ width: '100%' }} placeholder="可选" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <div className="form-hint compact">修改对象类型后，实例号自动建议为当前类型最大值 + 1。</div>
            </Col>
            <Col xs={24}>
              <Form.Item name="description" label="描述">
                <Input.TextArea rows={2} maxLength={1000} showCount />
              </Form.Item>
            </Col>
          </Row>
          {selectedPoint && (selectedPoint.dataType === 'STRING' || selectedPoint.dataType === 'HEX') ? (
            <Alert
              type="warning"
              showIcon
              icon={<WarningOutlined />}
              message="该点位类型没有默认的标准 BACnet 对象映射"
              description="请实现 BacnetValueAdapter 扩展以定义 STRING / HEX 的厂商对象策略。"
            />
          ) : null}
        </Form>
      </Modal>
    </>
  );
}
