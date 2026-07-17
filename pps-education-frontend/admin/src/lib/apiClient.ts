import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "./tokenStorage";

/** Khớp đúng shape lỗi thật của backend — xem GlobalExceptionHandler.java. */
interface BackendErrorBody {
  timestamp?: string;
  status?: number;
  message?: string;
}

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

const API_BASE = "/api";

interface RequestOptions extends RequestInit {
  /** Endpoint công khai (login/refresh/logout) — không gắn Authorization, không tự refresh khi 401. */
  skipAuth?: boolean;
  /** Đánh dấu nội bộ để tránh refresh lặp vô hạn khi request retry sau refresh cũng bị 401. */
  isRetry?: boolean;
}

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken })
    })
      .then(async (res) => {
        if (!res.ok) return null;
        const data = await res.json();
        setTokens(data.accessToken, data.refreshToken);
        return data.accessToken as string;
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

async function parseBody<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { skipAuth, isRetry, ...init } = options;
  // FormData (upload multipart, VD import Excel) không được tự set Content-Type json —
  // trình duyệt cần tự sinh header với boundary đúng, set thủ công sẽ làm BE không parse được multipart.
  const headers: Record<string, string> = {
    ...(init.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
    ...(init.headers as Record<string, string> | undefined)
  };

  if (!skipAuth) {
    const accessToken = getAccessToken();
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  }

  const res = await fetch(`${API_BASE}${path}`, { ...init, headers });

  if (res.status === 401 && !skipAuth && !isRetry) {
    const newAccessToken = await refreshAccessToken();
    if (newAccessToken) {
      return apiRequest<T>(path, { ...options, isRetry: true });
    }
    clearTokens();
    window.location.href = "/login";
    throw new ApiError(401, "Phiên đăng nhập đã hết hạn.");
  }

  if (!res.ok) {
    const body = await parseBody<BackendErrorBody>(res).catch(() => ({}) as BackendErrorBody);
    throw new ApiError(res.status, body.message || res.statusText);
  }

  return parseBody<T>(res);
}
