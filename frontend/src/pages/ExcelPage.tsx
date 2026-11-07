import { useState } from 'react';
import {
  CheckCircleOutlined,
  CloudDownloadOutlined,
  FileExcelOutlined,
  ImportOutlined,
  InboxOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Col,
  Row,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { UploadProps } from 'antd';
import { api, downloadFile } from '../api';
import PageHeading from '../components/PageHeading';
import { errorMessage, formatTime } from '../helpers';
import type { ImportIssue, ImportReport } from '../types';

interface Props {
  onImported: () => Promise<void> | void;
}

export default function ExcelPage({ onImported }: Props) {
  const { message } = AntApp.useApp();
  const [importing, setImporting] = useState(false);
  const [report, setReport] = useState<ImportReport | null>(null);

  const handleImport = async (file: File) => {
    if (!/\.(xlsx|xls)$/i.test(file.name)) {
      message.error('请选择 .xlsx 或 .xls 文件');
      return;
    }
    setImporting(true);
    setReport(null);
    try {
      const result = await api.upload<ImportReport>('/api/excel/import', file);
      setReport(result);
      if (result.success) {
        message.success(result.message);
        await onImported();
      } else {
        message.warning(result.message);
      }
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setImporting(false);
    }
  };

  const uploadProps: UploadProps = {
    name: 'file',
    multiple: false,
    accept: '.xlsx,.xls',
    showUploadList: false,
    customRequest: (options) => {
      handleImport(options.file as File).finally(() => {
        options.onSuccess?.({});
      });
    },
  };

  const download = async (path: string, name: string) => {
    try {
      await downloadFile(path, name);
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const issueColumns = [
    {
      title: '级别',
      dataIndex: 'severity',
      width: 90,
      render: (value: string) =>
        value === 'ERROR' ? (
          <Tag color="error">错误</Tag>
        ) : value === 'WARNING' ? (
          <Tag color="warning">警告</Tag>
        ) : (
          <Tag>信息</Tag>
        ),
    },
    { title: '工作表', dataIndex: 'sheet', width: 110 },
    { title: '行号', dataIndex: 'row', width: 70, render: (value: number) => value || '—' },
    { title: '字段', dataIndex: 'field', width: 130 },
    { title: '说明', dataIndex: 'message' },
  ];

  return (
    <>
      <PageHeading
        eyebrow="04 · BULK CONFIGURATION"
        title="Excel 导入导出"
        description="用常见 Modbus 点位表批量维护设备与点位。导入采用先全量校验、后一次性写入，错误不会留下半成品配置。"
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card className="workflow-card">
            <div className="workflow-icon"><CloudDownloadOutlined /></div>
            <Typography.Title level={4}>1. 下载模板</Typography.Title>
            <Typography.Paragraph type="secondary">
              模板包含“设备”“点位”和“说明”三个工作表，黄色示例行展示浮点 CDAB、线圈、缩放等常见写法。
            </Typography.Paragraph>
            <Button
              type="primary"
              ghost
              icon={<FileExcelOutlined />}
              onClick={() => download('/api/excel/template', 'modbus-template.xlsx')}
            >
              下载导入模板
            </Button>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card className="workflow-card">
            <div className="workflow-icon export"><FileExcelOutlined /></div>
            <Typography.Title level={4}>2. 导出当前配置</Typography.Title>
            <Typography.Paragraph type="secondary">
              将当前所有设备、点位和端序/缩放参数导出为 Excel。设备修改后可再次导入，按名称和编码更新。
            </Typography.Paragraph>
            <Button
              icon={<CloudDownloadOutlined />}
              onClick={() => download('/api/excel/export', 'modbus-config.xlsx')}
            >
              导出全部配置
            </Button>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card className="workflow-card accent">
            <div className="workflow-icon import"><ImportOutlined /></div>
            <Typography.Title level={4}>3. 导入并校验</Typography.Title>
            <Typography.Paragraph type="secondary">
              支持 .xlsx / .xls、公式单元格和常见中文表头；地址支持 0-based 与 40001 风格参考地址。
            </Typography.Paragraph>
            <Upload {...uploadProps}>
              <Button type="primary" icon={<ImportOutlined />} loading={importing}>
                选择 Excel 文件
              </Button>
            </Upload>
          </Card>
        </Col>
      </Row>

      <Card className="content-card upload-card">
        <Upload.Dragger {...uploadProps} disabled={importing}>
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">拖拽 Excel 文件到这里，或点击选择</p>
          <p className="ant-upload-hint">
            支持设备表 + 点位表，也支持带设备列的平铺点位表。导入前会校验重复名称、地址范围、协议字段、TLS 文件和数据类型。
          </p>
        </Upload.Dragger>
      </Card>

      {report ? (
        <Card
          className="content-card"
          title={
            <Space>
              {report.success ? (
                <CheckCircleOutlined className="success-icon" />
              ) : (
                <WarningOutlined className="warning-icon" />
              )}
              <span>导入结果</span>
              <Tag color={report.success ? 'success' : 'error'}>
                {report.success ? '成功' : '未写入'}
              </Tag>
            </Space>
          }
          extra={<span className="card-hint">{formatTime(report.importedAt)}</span>}
        >
          <Alert
            type={report.success ? 'success' : 'error'}
            showIcon
            message={report.message}
            className="import-summary"
          />
          {report.success ? (
            <Row gutter={[14, 14]} className="import-stats">
              <Col xs={12} md={6}>
                <div className="import-stat">
                  <span>设备新增</span>
                  <b>{report.devicesCreated}</b>
                </div>
              </Col>
              <Col xs={12} md={6}>
                <div className="import-stat">
                  <span>设备更新</span>
                  <b>{report.devicesUpdated}</b>
                </div>
              </Col>
              <Col xs={12} md={6}>
                <div className="import-stat">
                  <span>点位新增</span>
                  <b>{report.pointsCreated}</b>
                </div>
              </Col>
              <Col xs={12} md={6}>
                <div className="import-stat">
                  <span>点位更新</span>
                  <b>{report.pointsUpdated}</b>
                </div>
              </Col>
            </Row>
          ) : null}
          {report.issues.length > 0 ? (
            <Table
              rowKey={(record, index) => `${record.sheet}-${record.row}-${index}`}
              dataSource={report.issues}
              columns={issueColumns}
              pagination={{ pageSize: 10, showSizeChanger: false }}
              size="small"
              className="issue-table"
            />
          ) : null}
        </Card>
      ) : null}

      <Card className="content-card" title="字段与兼容性说明">
        <Row gutter={[24, 16]}>
          <Col xs={24} md={12}>
            <Typography.Title level={5}>地址表示</Typography.Title>
            <Typography.Paragraph type="secondary">
              <code>MODBUS_REFERENCE</code>：40001 → 保持寄存器协议地址 0，30001 → 输入寄存器 0，
              00001 → 线圈 0，10001 → 离散输入 0。
              <br />
              <code>ZERO_BASED</code>：直接使用 0..65535 协议地址。地址格式留空时，保持寄存器 ≥
              40001、输入寄存器 ≥ 30001、离散输入 ≥ 10001 会按参考地址自动识别。
            </Typography.Paragraph>
          </Col>
          <Col xs={24} md={12}>
            <Typography.Title level={5}>端序写法</Typography.Title>
            <Typography.Paragraph type="secondary">
              <code>ABCD</code> 大端、<code>BADC</code> 字节交换、<code>CDAB</code> 字交换、
              <code>DCBA</code> 全交换。也可分别填写“字节序”和“字序”。缩放公式为 工程值 =
              原始值 × 缩放系数 + 偏移量。
            </Typography.Paragraph>
          </Col>
          <Col xs={24} md={12}>
            <Typography.Title level={5}>数据类型</Typography.Title>
            <Typography.Paragraph type="secondary">
              BOOL、INT16/UINT16、INT32/UINT32、INT64/UINT64、FLOAT32/FLOAT64、STRING、HEX。
              寄存器内 BOOL 使用 0..15 位索引；STRING/HEX 需填写寄存器数量。
            </Typography.Paragraph>
          </Col>
          <Col xs={24} md={12}>
            <Typography.Title level={5}>安全</Typography.Title>
            <Typography.Paragraph type="secondary">
              导出文件不包含私钥密码。若设备使用加密私钥，请重新导入后编辑设备并补填密码。
              导入会校验 TLS 证书文件在当前后端主机上存在且可读。
            </Typography.Paragraph>
          </Col>
        </Row>
      </Card>
    </>
  );
}
