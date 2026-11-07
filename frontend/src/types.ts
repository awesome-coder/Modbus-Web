export type ProtocolType = 'MODBUS_TCP' | 'MODBUS_TCP_SECURE';
export type PointArea = 'COIL' | 'DISCRETE_INPUT' | 'HOLDING_REGISTER' | 'INPUT_REGISTER';
export type DataType =
  | 'BOOL'
  | 'INT16'
  | 'UINT16'
  | 'INT32'
  | 'UINT32'
  | 'INT64'
  | 'UINT64'
  | 'FLOAT32'
  | 'FLOAT64'
  | 'STRING'
  | 'HEX';
export type ByteOrder = 'BIG_ENDIAN' | 'LITTLE_ENDIAN';
export type WordOrder = 'BIG_ENDIAN' | 'LITTLE_ENDIAN';
export type PointAccess = 'READ_ONLY' | 'READ_WRITE' | 'WRITE_ONLY';
export type StringCharset = 'ASCII' | 'UTF_8' | 'UTF_16BE' | 'UTF_16LE';
export type ValueQuality = 'GOOD' | 'BAD' | 'UNCERTAIN';
export type BacnetObjectType =
  | 'ANALOG_INPUT'
  | 'ANALOG_OUTPUT'
  | 'ANALOG_VALUE'
  | 'BINARY_INPUT'
  | 'BINARY_OUTPUT'
  | 'BINARY_VALUE'
  | 'MULTI_STATE_INPUT'
  | 'MULTI_STATE_OUTPUT'
  | 'MULTI_STATE_VALUE';

export interface Option {
  value: string;
  labelZh: string;
  labelEn: string;
  extra?: Record<string, unknown>;
}

export interface EndiannessPreset {
  value: string;
  labelZh: string;
  labelEn: string;
  byteOrder: ByteOrder;
  wordOrder: WordOrder;
  example: string;
}

export interface Metadata {
  protocols: Option[];
  areas: Option[];
  dataTypes: Option[];
  byteOrders: Option[];
  wordOrders: Option[];
  accessModes: Option[];
  stringCharsets: Option[];
  bacnetObjectTypes: Option[];
  endiannessPresets: EndiannessPreset[];
  limits: Record<string, number>;
}

export interface Device {
  id: number;
  name: string;
  protocol: ProtocolType;
  host: string;
  port: number;
  unitId: number;
  connectTimeoutMs: number;
  requestTimeoutMs: number;
  enabled: boolean;
  tlsClientCertPath: string | null;
  tlsClientKeyPath: string | null;
  tlsKeyPasswordSet: boolean;
  tlsCaCertPath: string | null;
  tlsVerifyHostname: boolean;
  description: string | null;
  pointCount: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface DevicePayload {
  name: string;
  protocol: ProtocolType;
  host: string;
  port: number;
  unitId: number;
  connectTimeoutMs: number;
  requestTimeoutMs: number;
  enabled: boolean;
  tlsClientCertPath?: string | null;
  tlsClientKeyPath?: string | null;
  tlsKeyPassword?: string | null;
  tlsCaCertPath?: string | null;
  tlsVerifyHostname: boolean;
  description?: string | null;
}

export interface DeviceStatus {
  deviceId: number;
  deviceName: string;
  protocol: ProtocolType;
  connected: boolean;
  connectedAt: string | null;
  lastError: string | null;
  reconnectCount: number;
  requestCount: number;
  errorCount: number;
}

export interface ConnectionTestResult {
  success: boolean;
  message: string;
  latencyMs: number;
  secure: boolean;
  resolvedHost: string;
  port: number;
}

export interface BacnetMapping {
  id: number;
  pointId: number;
  pointCode: string;
  pointName: string;
  deviceId: number;
  deviceName: string;
  enabled: boolean;
  objectType: BacnetObjectType;
  objectInstance: number;
  objectId: string;
  objectName: string;
  units: string | null;
  covIncrement: number | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface Point {
  id: number;
  deviceId: number;
  deviceName: string;
  code: string;
  name: string;
  area: PointArea;
  address: number;
  addressReference: string;
  unitId: number | null;
  effectiveUnitId: number;
  dataType: DataType;
  byteOrder: ByteOrder;
  wordOrder: WordOrder;
  bitIndex: number | null;
  registerLength: number | null;
  registerCount: number;
  stringCharset: StringCharset;
  scale: number;
  offset: number;
  decimals: number | null;
  unit: string | null;
  access: PointAccess;
  enabled: boolean;
  description: string | null;
  writable: boolean;
  bacnetMapping: BacnetMapping | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface PointPayload {
  code: string;
  name: string;
  area: PointArea;
  address: number;
  unitId: number | null;
  dataType: DataType;
  byteOrder: ByteOrder;
  wordOrder: WordOrder;
  bitIndex: number | null;
  registerLength: number | null;
  stringCharset: StringCharset;
  scale: number;
  offset: number;
  decimals: number | null;
  unit?: string | null;
  access: PointAccess;
  enabled: boolean;
  description?: string | null;
}

export interface PointReadResult {
  pointId: number;
  pointCode: string;
  pointName: string;
  area: PointArea;
  address: number;
  addressReference: string;
  dataType: DataType;
  success: boolean;
  value: string | null;
  numericValue: number | null;
  booleanValue: boolean | null;
  rawHex: string | null;
  quality: ValueQuality;
  error: string | null;
  unit: string | null;
  registerCount: number;
  timestamp: string;
  elapsedMs: number;
}

export interface ReadAllResult {
  deviceId: number;
  deviceName: string;
  startedAt: string;
  finishedAt: string;
  durationMs: number;
  total: number;
  successCount: number;
  errorCount: number;
  results: PointReadResult[];
}

export interface WriteResult {
  success: boolean;
  message: string;
  readBack: PointReadResult;
}

export interface ImportIssue {
  severity: 'ERROR' | 'WARNING' | 'INFO';
  sheet: string;
  row: number;
  field: string;
  message: string;
}

export interface ImportReport {
  success: boolean;
  message: string;
  importedAt: string;
  devicesCreated: number;
  devicesUpdated: number;
  pointsCreated: number;
  pointsUpdated: number;
  skipped: number;
  issues: ImportIssue[];
}

export interface BacnetGenerateResult {
  created: number;
  skipped: number;
  mappings: BacnetMapping[];
  warnings: string[];
}

export interface BacnetObjectView {
  mappingId: number;
  pointId: number;
  pointCode: string;
  pointName: string;
  objectType: BacnetObjectType;
  objectInstance: number;
  objectId: string;
  objectName: string;
  presentValueType: string;
  presentValue: unknown;
  units: string | null;
  covIncrement: number | null;
  enabled: boolean;
  source: Record<string, unknown>;
}

export interface BacnetPreview {
  deviceId: number;
  deviceName: string;
  generatedAt: string;
  valuesIncluded: boolean;
  objects: BacnetObjectView[];
  warnings: string[];
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  details?: Record<string, string> | null;
  path: string;
}
