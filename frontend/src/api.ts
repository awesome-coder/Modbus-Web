import type { ApiErrorBody } from './types';

export class ApiError extends Error {
  status: number;
  details?: Record<string, string> | null;

  constructor(message: string, status: number, details?: Record<string, string> | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

async function parseError(response: Response): Promise<ApiError> {
  let message = `${response.status} ${response.statusText}`;
  let details: Record<string, string> | null | undefined;
  try {
    const body = (await response.json()) as ApiErrorBody;
    if (body?.message) {
      message = body.message;
    }
    if (body?.details && typeof body.details === 'object') {
      details = body.details as Record<string, string>;
      const fieldMessages = Object.values(details);
      if (fieldMessages.length > 0) {
        message += ` (${fieldMessages.join('; ')})`;
      }
    }
  } catch {
    // Keep status text when the response is not JSON.
  }
  return new ApiError(message, response.status, details);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body && !(init.body instanceof FormData)
          ? { 'Content-Type': 'application/json' }
          : {}),
        ...init?.headers,
      },
    });
  } catch (error) {
    throw new ApiError(
      `无法连接后端服务：${error instanceof Error ? error.message : String(error)}`,
      0,
    );
  }
  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<T>(path, { method: 'POST', body: form });
  },
};

export async function downloadFile(path: string, fallbackName: string): Promise<void> {
  const response = await fetch(path);
  if (!response.ok) {
    throw await parseError(response);
  }
  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  const plainMatch = disposition.match(/filename="?([^";]+)"?/i);
  const filename = utf8Match
    ? decodeURIComponent(utf8Match[1])
    : plainMatch
      ? plainMatch[1]
      : fallbackName;
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
