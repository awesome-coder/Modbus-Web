import dayjs from 'dayjs';
import type {
  BacnetObjectType,
  ByteOrder,
  DataType,
  Device,
  DeviceStatus,
  Metadata,
  Option,
  Point,
  PointAccess,
  PointArea,
  ProtocolType,
  ValueQuality,
  WordOrder,
} from './types';

export const areaFallback: Record<PointArea, string> = {
  COIL: '线圈',
  DISCRETE_INPUT: '离散输入',
  HOLDING_REGISTER: '保持寄存器',
  INPUT_REGISTER: '输入寄存器',
};

export const areaFunctionCode: Record<PointArea, number> = {
  COIL: 1,
  DISCRETE_INPUT: 2,
  HOLDING_REGISTER: 3,
  INPUT_REGISTER: 4,
};

export const areaColor: Record<PointArea, string> = {
  COIL: 'green',
  DISCRETE_INPUT: 'cyan',
  HOLDING_REGISTER: 'blue',
  INPUT_REGISTER: 'purple',
};

export const dataTypeFallback: Record<DataType, string> = {
  BOOL: 'BOOL',
  INT16: 'INT16',
  UINT16: 'UINT16',
  INT32: 'INT32',
  UINT32: 'UINT32',
  INT64: 'INT64',
  UINT64: 'UINT64',
  FLOAT32: 'FLOAT32',
  FLOAT64: 'FLOAT64',
  STRING: 'STRING',
  HEX: 'HEX',
};

export const accessFallback: Record<PointAccess, string> = {
  READ_ONLY: '只读',
  READ_WRITE: '读写',
  WRITE_ONLY: '只写',
};

export const bacnetFallback: Record<BacnetObjectType, string> = {
  ANALOG_INPUT: '模拟量输入',
  ANALOG_OUTPUT: '模拟量输出',
  ANALOG_VALUE: '模拟量值',
  BINARY_INPUT: '二进制输入',
  BINARY_OUTPUT: '二进制输出',
  BINARY_VALUE: '二进制值',
  MULTI_STATE_INPUT: '多态输入',
  MULTI_STATE_OUTPUT: '多态输出',
  MULTI_STATE_VALUE: '多态值',
};

export const qualityColor: Record<ValueQuality, string> = {
  GOOD: 'success',
  BAD: 'error',
  UNCERTAIN: 'warning',
};

export function optionLabel(
  options: Option[] | undefined,
  value: string | null | undefined,
  fallback?: string,
): string {
  if (!value) return '-';
  return options?.find((item) => item.value === value)?.labelZh ?? fallback ?? value;
}

export function formatTime(value: string | null | undefined): string {
  if (!value) return '-';
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
}

export function formatShortTime(value: string | null | undefined): string {
  if (!value) return '-';
  return dayjs(value).format('HH:mm:ss');
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return String(error);
}

export function layoutOf(
  byteOrder: ByteOrder,
  wordOrder: WordOrder,
): 'ABCD' | 'BADC' | 'CDAB' | 'DCBA' {
  if (byteOrder === 'BIG_ENDIAN' && wordOrder === 'BIG_ENDIAN') return 'ABCD';
  if (byteOrder === 'LITTLE_ENDIAN' && wordOrder === 'BIG_ENDIAN') return 'BADC';
  if (byteOrder === 'BIG_ENDIAN' && wordOrder === 'LITTLE_ENDIAN') return 'CDAB';
  return 'DCBA';
}

export function presetForPoint(point: Point): string {
  return layoutOf(point.byteOrder, point.wordOrder);
}

export function statusByDevice(statuses: DeviceStatus[], deviceId: number): DeviceStatus | undefined {
  return statuses.find((item) => item.deviceId === deviceId);
}

export function protocolLabel(protocol: ProtocolType): string {
  return protocol === 'MODBUS_TCP_SECURE' ? 'Modbus TCP Security' : 'Modbus TCP';
}

export function onlineCount(devices: Device[], statuses: DeviceStatus[]): number {
  return devices.filter((device) => statusByDevice(statuses, device.id)?.connected).length;
}

export function metaValue(
  meta: Metadata | null,
  key: keyof Metadata,
  value: string | null | undefined,
): string {
  if (!value) return '-';
  const options = meta?.[key];
  if (Array.isArray(options)) {
    return optionLabel(options as Option[], value);
  }
  return value;
}
