import {
  ApiOutlined,
  BookOutlined,
  FileExcelOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { Alert, Card, Col, Divider, Row, Space, Table, Tag, Typography } from 'antd';
import PageHeading from '../components/PageHeading';

const endianRows = [
  { key: 'ABCD', bytes: '3F 80 00 00', meaning: '大端，高字在前', byteOrder: 'BIG_ENDIAN', wordOrder: 'BIG_ENDIAN' },
  { key: 'BADC', bytes: '80 3F 00 00', meaning: '大端字序，字节交换', byteOrder: 'LITTLE_ENDIAN', wordOrder: 'BIG_ENDIAN' },
  { key: 'CDAB', bytes: '00 00 3F 80', meaning: '小端字序，字交换', byteOrder: 'BIG_ENDIAN', wordOrder: 'LITTLE_ENDIAN' },
  { key: 'DCBA', bytes: '00 00 80 3F', meaning: '小端，全交换', byteOrder: 'LITTLE_ENDIAN', wordOrder: 'LITTLE_ENDIAN' },
];

const typeRows = [
  { key: 'BOOL', registers: '1 bit / 1 reg', notes: '线圈、离散输入或寄存器位（0..15）' },
  { key: 'INT16 / UINT16', registers: '1', notes: '16 位有符号 / 无符号整数' },
  { key: 'INT32 / UINT32', registers: '2', notes: '32 位整数' },
  { key: 'INT64 / UINT64', registers: '4', notes: '64 位整数，UINT64 以精确十进制文本返回' },
  { key: 'FLOAT32 / FLOAT64', registers: '2 / 4', notes: 'IEEE 754 单精度 / 双精度' },
  { key: 'STRING', registers: '自定义', notes: 'UTF-8 / ASCII / UTF-16BE / UTF-16LE，尾部 NUL 自动裁剪' },
  { key: 'HEX', registers: '自定义', notes: '原始寄存器内容，读写均为十六进制' },
];

export default function HelpPage() {
  return (
    <>
      <PageHeading
        eyebrow="06 · FIELD NOTES"
        title="使用说明"
        description="从设备接入、点位建模到 Excel 批量导入和 BACnet 网关对接的完整参考。"
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card className="help-card" title={<Space><ApiOutlined />系统架构</Space>}>
            <div className="architecture-flow">
              <div className="arch-node browser">React + Ant Design</div>
              <div className="arch-arrow">REST / JSON</div>
              <div className="arch-node backend">Spring Boot</div>
              <div className="arch-arrow">digitalpetri modbus 2.1.6</div>
              <div className="arch-node device">Modbus TCP / TLS</div>
              <div className="arch-arrow dashed">映射契约</div>
              <div className="arch-node bacnet">BACnet Gateway</div>
            </div>
            <Typography.Paragraph type="secondary">
              后端使用 H2 文件数据库持久化设备、点位和 BACnet 映射。每个设备维护一个长连接 digitalpetri 客户端，Web 请求共享该连接。
            </Typography.Paragraph>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card className="help-card" title={<Space><SwapOutlined />地址与端序</Space>}>
            <Typography.Paragraph>
              点位地址存储为 <Tag>0-based</Tag> 协议地址。界面中的参考地址按区域换算：线圈 00001、离散输入 10001、输入寄存器 30001、保持寄存器 40001 均对应协议地址 0。
            </Typography.Paragraph>
            <Typography.Paragraph type="secondary">
              读写寄存器时先按字节序交换每个 16 位字内部字节，再按字序调整寄存器顺序，最后按大端解析。写入执行完全对称的逆变换。
            </Typography.Paragraph>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card className="help-card" title={<Space><SafetyCertificateOutlined />Modbus TCP Security</Space>}>
            <Typography.Paragraph>
              <Tag color="gold">双向 TLS</Tag> 规范要求服务端验证客户端证书。配置页面需要三类 PEM 文件：
            </Typography.Paragraph>
            <ul className="help-list">
              <li>客户端证书链（可包含多张证书）</li>
              <li>客户端私钥（PKCS#8 / PKCS#1 / EC，可加密）</li>
              <li>签发服务端证书的 CA 证书</li>
            </ul>
            <Typography.Paragraph type="secondary">
              TLS 1.2 / 1.3 由 Netty 和 digitalpetri transport 协商；可启用证书主机名校验。
            </Typography.Paragraph>
          </Card>
        </Col>
      </Row>

      <Card className="content-card" title={<Space><BookOutlined />端序布局对照</Space>}>
        <Table
          rowKey="key"
          pagination={false}
          size="middle"
          dataSource={endianRows}
          columns={[
            { title: '布局', dataIndex: 'key', width: 90, render: (value: string) => <Tag color="geekblue">{value}</Tag> },
            { title: 'FLOAT32 = 1.0 的字节', dataIndex: 'bytes', width: 220, render: (value: string) => <span className="mono">{value}</span> },
            { title: '含义', dataIndex: 'meaning' },
            { title: '字节序', dataIndex: 'byteOrder', render: (value: string) => <span className="mono small">{value}</span> },
            { title: '字序', dataIndex: 'wordOrder', render: (value: string) => <span className="mono small">{value}</span> },
          ]}
        />
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card className="content-card" title="数据类型">
            <Table
              rowKey="key"
              pagination={false}
              size="small"
              dataSource={typeRows}
              columns={[
                { title: '类型', dataIndex: 'key', width: 150, render: (value: string) => <span className="mono strong">{value}</span> },
                { title: '寄存器', dataIndex: 'registers', width: 130 },
                { title: '说明', dataIndex: 'notes' },
              ]}
            />
            <Alert
              className="inline-alert"
              type="info"
              showIcon
              message="缩放和偏移仅作用于数值类型"
              description="工程值 = 原始值 x scale + offset。写入整数类型时，逆运算结果按 HALF_UP 四舍五入到整数；写入前会校验目标数据类型范围，防止截断。"
            />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card className="content-card" title="协议与地址限制">
            <ul className="help-list spacious">
              <li>FC01 读线圈 / FC05 写单线圈</li>
              <li>FC02 读离散输入，只读</li>
              <li>FC03 读保持寄存器 / FC06 写单寄存器 / FC16 写多寄存器</li>
              <li>FC04 读输入寄存器，只读</li>
              <li>单次读取最多 125 个寄存器或 2000 个线圈</li>
              <li>单次写入最多 123 个寄存器，地址范围 0..65535</li>
              <li>寄存器位 BOOL 写使用“读当前寄存器 - 改位 - 写回”</li>
            </ul>
            <Divider />
            <Typography.Paragraph type="secondary">
              未实现 FC23 读写组合、FC22 掩码写和串行 RTU；工具聚焦 Modbus TCP 与 TCP Security。
            </Typography.Paragraph>
          </Card>
        </Col>
      </Row>

      <Card className="content-card" title={<Space><FileExcelOutlined />Excel 与常见点位表</Space>}>
        <Typography.Paragraph>
          模板为“设备 + 点位 + 说明”结构。也可以直接把常见的单表点位清单拖入导入区，只要包含设备名称和点位字段。导入按设备名称和设备名称 + 点位编码进行 Upsert。
        </Typography.Paragraph>
        <Row gutter={[24, 8]}>
          <Col xs={24} md={12}>
            <Typography.Title level={5}>地址格式</Typography.Title>
            <Typography.Paragraph type="secondary">
              MODBUS_REFERENCE：传统 5 位参考地址；ZERO_BASED：0-based 协议地址。留空时按区域自动识别常见参考地址。线圈建议显式填写地址格式，避免 1 与 00001 的歧义。
            </Typography.Paragraph>
          </Col>
          <Col xs={24} md={12}>
            <Typography.Title level={5}>全量校验</Typography.Title>
            <Typography.Paragraph type="secondary">
              发现任何错误时不会写入数据库。报告会给出工作表、行号、字段和原因；警告不阻止导入。导出不会包含私钥密码。
            </Typography.Paragraph>
          </Col>
        </Row>
      </Card>

      <Card className="content-card" title={<Space><LockOutlined />BACnet 对接接口</Space>}>
        <Typography.Paragraph>
          默认对象建议：只读 BOOL 映射 BINARY_INPUT；可写 BOOL 映射 BINARY_VALUE；只读数值映射 ANALOG_INPUT；可写数值映射 ANALOG_VALUE。对象实例号按对象类型全局唯一。
        </Typography.Paragraph>
        <Typography.Paragraph>
          外部 BACnet 服务可调用 GET /api/devices/&#123;id&#125;/bacnet/export 获取完整对象清单和 Modbus 来源元数据。后端扩展点：
        </Typography.Paragraph>
        <pre className="code-block">{`public interface BacnetValueAdapter {
  BacnetObjectType suggestObjectType(ModbusPoint point);
  AdapterResult toPresentValue(
      ModbusPoint point,
      BacnetObjectType objectType,
      ModbusValueCodec.DecodedValue value);
  boolean supports(ModbusPoint point, BacnetObjectType objectType);
}`}</pre>
        <Typography.Paragraph type="secondary">
          注册自定义 Spring Bean 即可覆盖默认适配器，例如为 STRING/HEX 定义厂商对象映射，或把 UINT64 映射到多态/模拟对象。
        </Typography.Paragraph>
        <Divider />
        <Typography.Title level={5}>主要 REST 端点</Typography.Title>
        <div className="endpoint-grid">
          <code>GET /api/devices</code>
          <code>POST /api/devices/&#123;id&#125;/test</code>
          <code>GET /api/devices/&#123;id&#125;/points</code>
          <code>POST /api/devices/&#123;id&#125;/read-all</code>
          <code>POST /api/points/&#123;id&#125;/write</code>
          <code>GET /api/excel/template</code>
          <code>POST /api/excel/import</code>
          <code>GET /api/devices/&#123;id&#125;/bacnet/export</code>
        </div>
      </Card>

      <Alert
        type="warning"
        showIcon
        className="inline-alert"
        message="生产部署提示"
        description="本工具没有内置用户认证，并允许从后端主机读取证书文件路径。请部署在可信管理网络中，通过反向代理增加认证；不要把服务直接暴露到互联网。"
      />
    </>
  );
}
