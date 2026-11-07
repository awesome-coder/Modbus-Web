import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ClockCircleOutlined,
  EditOutlined,
  HistoryOutlined,
  MonitorOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import {
  App as AntApp,
  Badge,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
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
  areaFallback,
  errorMessage,
  formatShortTime,
  formatTime,
  protocolLabel,
  qualityColor,
} from '../helpers';
import type {
  Device,
  Point,
  PointReadResult,
  ReadAllResult,
  WriteResult,
} from '../types';

interface Props {
  devices: Device[];
}

export default function MonitorPage({ devices }: Props) {
  const { message, notification } = AntApp.useApp();
  const [writeForm] = Form.useForm<{ value: string }>();
  const [deviceId, setDeviceId] = useState<number | null>(null);
  const [points, setPoints] = useState<Point[]>([]);
  const [data, setData] = useState<ReadAllResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [writeTarget, setWriteTarget] = useState<PointReadResult | null>(null);
  const [writing, setWriting] = useState(false);
  const selectedDevice = devices.find((device) => device.id === deviceId) ?? null;
  const pointById = useMemo(
    () => new Map(points.map((point) => [point.id, point])),
    [points],
  );

  useEffect(() => {
    if (devices.length === 0) {
      setDeviceId(null);
      return;
    }
    if (!deviceId || !devices.some((device) => device.id === deviceId)) {
      setDeviceId(devices[0].id);
    }
  }, [deviceId, devices]);

  useEffect(() => {
    if (!deviceId) {
      setPoints([]);
      setData(null);
      return;
    }
    let cancelled = false;
    api
      .get<Point[]>(`/api/devices/${deviceId}/points`)
      .then((items) => {
        if (!cancelled) setPoints(items);
      })
      .catch((error) => message.error(errorMessage(error)));
    setData(null);
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deviceId]);

  const readAll = useCallback(
    async (silent = false) => {
      if (!deviceId) return;
      setLoading(true);
      try {
        const result = await api.post<ReadAllResult>(`/api/devices/${deviceId}/read-all`);
        setData(result);
        if (!silent) {
          if (result.errorCount > 0) {
            message.warning(
              `读取完成：${result.successCount} 成功，${result.errorCount} 失败`,
            );
          } else {
            message.success(`${result.successCount} 个点位读取成功 · ${result.durationMs} ms`);
          }
        }
      } catch (error) {
        if (!silent) message.error(errorMessage(error));
      } finally {
        setLoading(false);
      }
    },
    [deviceId, message],
  );

  useEffect(() => {
    if (!autoRefresh || !deviceId) return;
    readAll(true);
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') readAll(true);
    }, 5000);
    return () => window.clearInterval(timer);
  }, [autoRefresh, deviceId, readAll]);

  const openWrite = (result: PointReadResult) => {
    const point = pointById.get(result.pointId);
    if (!point?.writable) {
      message.warning('该点位不可写');
      return;
    }
    setWriteTarget(result);
    writeForm.setFieldsValue({
      value:
        result.dataType === 'BOOL'
          ? result.booleanValue
            ? 'ON'
            : 'OFF'
          : result.value ?? '',
    });
  };

  const submitWrite = async () => {
    if (!writeTarget) return;
    try {
      const { value } = await writeForm.validateFields();
      setWriting(true);
      const result = await api.post<WriteResult>(
        `/api/points/${writeTarget.pointId}/write`,
        { value },
      );
      message.success(result.message);
      setWriteTarget(null);
      await readAll(true);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      notification.error({
        message: '写入失败',
        description: errorMessage(error),
        duration: 7,
      });
    } finally {
      setWriting(false);
    }
  };

  const columns: ColumnsType<PointReadResult> = [
    {
      title: '点位',
      key: 'point',
      width: 210,
      fixed: 'left',
      render: (_, result) => (
        <Space direction="vertical" size={0}>
          <span className="device-name">{result.pointName}</span>
          <span className="mono small-muted">{result.pointCode}</span>
        </Space>
      ),
    },
    {
      title: '区域 / 地址',
      key: 'address',
      width: 165,
      render: (_, result) => (
        <Space direction="vertical" size={0}>
          <Tag>{areaFallback[result.area]}</Tag>
          <span className="mono small-muted">
            {result.addressReference} <span className="dim">/ #{result.address}</span>
          </span>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'dataType',
      width: 100,
      render: (value: string) => <Tag color="geekblue">{value}</Tag>,
    },
    {
      title: '实时值',
      key: 'value',
      width: 220,
      render: (_, result) => {
        if (!result.success) {
          return (
            <Tooltip title={result.error}>
              <Tag icon={<WarningOutlined />} color="error">
                读取失败
              </Tag>
            </Tooltip>
          );
        }
        const display =
          result.booleanValue !== null
            ? result.booleanValue
              ? 'ON'
              : 'OFF'
            : result.value;
        return (
          <Space size={8} align="baseline">
            <span className={`live-value ${result.booleanValue ? 'boolean-true' : ''}`}>
              {display}
            </span>
            {result.unit ? <span className="unit-label">{result.unit}</span> : null}
            <Tag color={qualityColor[result.quality]}>{result.quality}</Tag>
          </Space>
        );
      },
    },
    {
      title: '原始数据',
      dataIndex: 'rawHex',
      width: 170,
      render: (value: string | null) => <span className="mono raw-hex">{value ?? '—'}</span>,
    },
    {
      title: '耗时',
      dataIndex: 'elapsedMs',
      width: 80,
      align: 'right',
      render: (value: number) => <span className="small-muted">{value} ms</span>,
    },
    {
      title: '时间',
      dataIndex: 'timestamp',
      width: 110,
      render: (value: string) => (
        <Tooltip title={formatTime(value)}>
          <span className="small-muted">{formatShortTime(value)}</span>
        </Tooltip>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      fixed: 'right',
      render: (_, result) => (
        <Button
          type="link"
          size="small"
          icon={<EditOutlined />}
          disabled={!pointById.get(result.pointId)?.writable}
          onClick={() => openWrite(result)}
        >
          写入
        </Button>
      ),
    },
  ];

  const selectedPoint = writeTarget ? pointById.get(writeTarget.pointId) : undefined;

  return (
    <>
      <PageHeading
        eyebrow="03 · LIVE DIAGNOSTICS"
        title="实时监视"
        description="逐点读取当前设备数据，检查原始寄存器、解码值、缩放结果和响应时间；可对可写点位执行受控写入。"
        extra={
          <Space>
            <span className="auto-refresh-label">
              <ClockCircleOutlined /> 5s 自动刷新
            </span>
            <Switch
              checked={autoRefresh}
              onChange={setAutoRefresh}
              disabled={!deviceId}
              checkedChildren="开"
              unCheckedChildren="关"
            />
          </Space>
        }
      />

      <Card className="content-card device-switcher">
        <div className="device-switcher-inner">
          <div>
            <div className="eyebrow">目标设备</div>
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
                <Tag color={selectedDevice.protocol === 'MODBUS_TCP_SECURE' ? 'gold' : 'blue'}>
                  {protocolLabel(selectedDevice.protocol)}
                </Tag>
                <Tag>{selectedDevice.host}:{selectedDevice.port}</Tag>
                <Tag>Unit {selectedDevice.unitId}</Tag>
              </>
            ) : null}
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              loading={loading}
              disabled={!deviceId}
              onClick={() => readAll(false)}
            >
              读取全部
            </Button>
            <Button icon={<ReloadOutlined />} disabled={!deviceId} onClick={() => readAll(false)}>
              刷新
            </Button>
          </Space>
        </div>
      </Card>

      {data ? (
        <Row gutter={[14, 14]} className="stat-row">
          <Col xs={12} md={6}>
            <Card className="stat-card compact-stat">
              <div className="stat-label">点位总数</div>
              <div className="stat-value small-value">{data.total}</div>
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card className="stat-card compact-stat">
              <div className="stat-label">读取成功</div>
              <div className="stat-value small-value success-text">{data.successCount}</div>
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card className="stat-card compact-stat">
              <div className="stat-label">读取失败</div>
              <div className="stat-value small-value error-text">{data.errorCount}</div>
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card className="stat-card compact-stat">
              <div className="stat-label">总耗时</div>
              <div className="stat-value small-value">{data.durationMs} ms</div>
            </Card>
          </Col>
        </Row>
      ) : null}

      <Card
        className="content-card"
        title={
          <Space>
            <MonitorOutlined />
            <span>点位实时值</span>
            {data ? (
              <Tag icon={<HistoryOutlined />} color="default">
                {formatTime(data.finishedAt)}
              </Tag>
            ) : null}
          </Space>
        }
        extra={
          data ? (
            <Space>
              <Badge status={data.errorCount > 0 ? 'warning' : 'success'} />
              <span className="card-hint">
                {data.successCount}/{data.total} 正常
              </span>
            </Space>
          ) : null
        }
      >
        {!selectedDevice ? (
          <Empty description="请先在“设备配置”中创建设备" />
        ) : points.length === 0 ? (
          <Empty description="该设备还没有点位，请先在“点位配置”中添加" />
        ) : !data ? (
          <div className="empty-panel">
            <ThunderboltOutlined className="empty-icon" />
            <Typography.Title level={4}>准备读取</Typography.Title>
            <Typography.Paragraph type="secondary">
              点击“读取全部”获取当前设备所有启用点位的实时值；也可开启 5 秒自动刷新。
            </Typography.Paragraph>
            <Button type="primary" icon={<PlayCircleOutlined />} loading={loading} onClick={() => readAll(false)}>
              立即读取
            </Button>
          </div>
        ) : (
          <Table
            rowKey="pointId"
            columns={columns}
            dataSource={data.results}
            rowClassName={(record) => (record.success ? '' : 'row-error')}
            scroll={{ x: 1130 }}
            pagination={{ pageSize: 15, showSizeChanger: false }}
            size="middle"
          />
        )}
      </Card>

      <Modal
        title={`写入点位 · ${writeTarget?.pointName ?? ''}`}
        open={!!writeTarget}
        onCancel={() => setWriteTarget(null)}
        onOk={submitWrite}
        okText="执行写入"
        cancelText="取消"
        confirmLoading={writing}
        okButtonProps={{ danger: true }}
        width={520}
      >
        {writeTarget ? (
          <>
            <div className="write-summary">
              <div>
                <span className="small-muted">当前值</span>
                <div className="write-current">
                  {writeTarget.value} {writeTarget.unit ?? ''}
                </div>
              </div>
              <div>
                <span className="small-muted">寄存器</span>
                <div className="mono">{writeTarget.rawHex ?? '—'}</div>
              </div>
              <div>
                <span className="small-muted">类型 / 地址</span>
                <div>
                  {writeTarget.dataType} · {writeTarget.addressReference}
                </div>
              </div>
              <div>
                <span className="small-muted">缩放 / 偏移</span>
                <div>
                  ×{selectedPoint?.scale ?? 1} {selectedPoint && selectedPoint.offset >= 0 ? '+' : ''}
                  {selectedPoint?.offset ?? 0}
                </div>
              </div>
            </div>
            <Form form={writeForm} layout="vertical">
              <Form.Item
                name="value"
                label="写入值（工程值）"
                rules={[{ required: true, message: '请输入写入值' }]}
                extra={
                  writeTarget.dataType === 'BOOL'
                    ? 'BOOL 可输入 ON/OFF、1/0、开/关'
                    : writeTarget.dataType === 'HEX'
                      ? 'HEX 支持 AA BB 或 AABB 格式'
                      : '将按点位数据类型、端序和缩放自动编码'
                }
              >
                {writeTarget.dataType === 'BOOL' ? (
                  <Select
                    options={[
                      { value: 'ON', label: 'ON / 1 / 开' },
                      { value: 'OFF', label: 'OFF / 0 / 关' },
                    ]}
                  />
                ) : (
                  <Input className="mono" placeholder="输入工程值" autoFocus />
                )}
              </Form.Item>
            </Form>
          </>
        ) : null}
      </Modal>
    </>
  );
}
