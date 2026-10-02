import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "./tokenStorage";
import i18n, { LANGUAGE_STORAGE_KEY } from "@/i18n";

/** Header Accept-Language theo ngôn ngữ đang chọn ở LanguageSwitcher — backend dùng để trả message lỗi
 *  song ngữ qua MessageSource (xem GlobalExceptionHandler.error(status, ex) phía backend). */
function currentLanguageHeader(): Record<string, string> {
  const lang = typeof window !== "undefined" ? window.localStorage.getItem(LANGUAGE_STORAGE_KEY) : null;
  return { "Accept-Language": lang === "en" ? "en" : "vi" };
}

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

/**
 * Nhiều exception ở BE (VD SubmissionPastDeadlineException — xem ExerciseAttemptService.java)
 * viết message kỹ thuật cho log/debug, kèm "id=123" nội bộ và mốc giờ ISO thô (UTC "Z") — không
 * có ý nghĩa với học sinh/phụ huynh khi hiện thẳng lên UI. Không sửa message gốc ở BE (đội khác
 * phụ trách), format lại ở đây trước khi hiển thị: bỏ "id=N", đổi giờ ISO trong ngoặc sang giờ
 * địa phương dễ đọc.
 */
export function friendlyApiErrorMessage(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) return fallback;
  const deadlineMatch = err.message.match(/(quá hạn nộp) \(([^)]+)\)/);
  if (deadlineMatch) {
    const deadline = new Date(deadlineMatch[2]);
    const formatted = Number.isNaN(deadline.getTime()) ? deadlineMatch[2] : deadline.toLocaleString("vi-VN");
    return `Bài đã quá hạn nộp (hạn nộp: ${formatted}) — đề này không cho phép nộp trễ.`;
  }
  return err.message.replace(/\bid=\d+\s*/g, "").replace(/\s{2,}/g, " ").trim();
}

const API_BASE = "/api";

interface RequestOptions extends RequestInit {
  /** Endpoint công khai (login/refresh/logout) — không gắn Authorization, không tự refresh khi 401. */
  skipAuth?: boolean;
  /** Đánh dấu nội bộ để tránh refresh lặp vô hạn khi request retry sau refresh cũng bị 401. */
  isRetry?: boolean;
  /** Huỷ request nếu quá số ms này (VD upload ảnh trên mạng di động chập chờn) — mặc định không giới hạn,
   *  vì upload video/AI job có thể chạy lâu hợp lệ. Hết giờ thì ném ApiError 408 với message dễ hiểu. */
  timeoutMs?: number;
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
        if (!res.ok) {
          // Tab khác (dùng chung token trong localStorage) vừa xoay vòng refresh token trước tab này —
          // backend chỉ từ chối token cũ (ân hạn nhiều-tab, xem AuthService#refresh), dùng luôn token
          // mới tab kia đã ghi thay vì đăng xuất tab này.
          const latestRefreshToken = getRefreshToken();
          return latestRefreshToken && latestRefreshToken !== refreshToken ? getAccessToken() : null;
        }
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

/** fetch kèm hạn chờ: fetch gốc không có timeout, mạng di động "treo" (gói tin rớt, không lỗi hẳn) sẽ chờ vô hạn. */
async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs?: number): Promise<Response> {
  if (!timeoutMs) return fetch(url, init);
  const controller = new AbortController();
  const callerSignal = init.signal;
  const onCallerAbort = () => controller.abort();
  if (callerSignal?.aborted) controller.abort();
  callerSignal?.addEventListener("abort", onCallerAbort);
  let timedOut = false;
  const timer = window.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (err) {
    if (timedOut) throw new ApiError(408, i18n.t("common:errors.requestTimeout"));
    throw err;
  } finally {
    window.clearTimeout(timer);
    callerSignal?.removeEventListener("abort", onCallerAbort);
  }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { skipAuth, isRetry, timeoutMs, ...init } = options;
  // FormData (upload multipart) không được tự set Content-Type json —
  // trình duyệt cần tự sinh header với boundary đúng, set thủ công sẽ làm BE không parse được multipart.
  const headers: Record<string, string> = {
    ...(init.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
    ...currentLanguageHeader(),
    ...(init.headers as Record<string, string> | undefined)
  };

  if (!skipAuth) {
    const accessToken = getAccessToken();
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  }

  const res = await fetchWithTimeout(`${API_BASE}${path}`, { ...init, headers }, timeoutMs);

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
