import React, { createContext, useContext, useRef, useState } from "react";
import Modal from "./Modal";
import Button from "./Button";

interface AlertOptions {
  title?: string;
  okLabel?: string;
}

interface ConfirmOptions {
  title?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** true: nút xác nhận tô màu cảnh báo (dùng cho hành động xóa/hủy/gỡ liên kết). */
  danger?: boolean;
}

interface PromptOptions {
  title?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  defaultValue?: string;
  placeholder?: string;
  /** true: nút xác nhận disable khi ô nhập còn trống. */
  required?: boolean;
  multiline?: boolean;
}

type DialogRequest =
  | { kind: "alert"; message: string; options?: AlertOptions; resolve: () => void }
  | { kind: "confirm"; message: string; options?: ConfirmOptions; resolve: (value: boolean) => void }
  | { kind: "prompt"; message: string; options?: PromptOptions; resolve: (value: string | null) => void };

interface DialogContextValue {
  /** Thay window.alert() — popup có nút OK, khớp giao diện app thay vì dialog gốc trình duyệt. */
  alertDialog: (message: string, options?: AlertOptions) => Promise<void>;
  /** Thay window.confirm() — popup 2 nút Xác nhận/Hủy, trả về true/false. */
  confirmDialog: (message: string, options?: ConfirmOptions) => Promise<boolean>;
  /** Thay window.prompt() — popup có ô nhập text, trả về chuỗi đã nhập hoặc null nếu bấm Hủy. */
  promptDialog: (message: string, options?: PromptOptions) => Promise<string | null>;
}

const DialogContext = createContext<DialogContextValue | null>(null);

/**
 * Mount 1 lần ở gốc app (App.tsx) — thay toàn bộ window.alert/confirm/prompt (dialog gốc trình
 * duyệt, không đồng bộ giao diện) bằng popup dùng chung Modal sẵn có. Chỉ giữ 1 request tại 1 thời
 * điểm (đúng hành vi window.alert/confirm/prompt vốn cũng chặn tuần tự, không xếp chồng nhiều popup).
 */
export function DialogProvider({ children }: { children: React.ReactNode }) {
  const [request, setRequest] = useState<DialogRequest | null>(null);
  const [inputValue, setInputValue] = useState("");
  // Tránh resolve 2 lần (VD bấm nút rồi Modal onClose bắn thêm 1 lần nữa trong cùng tick).
  const resolvedRef = useRef(false);

  const closeWith = (result: unknown) => {
    if (resolvedRef.current || !request) return;
    resolvedRef.current = true;
    if (request.kind === "alert") request.resolve();
    else if (request.kind === "confirm") request.resolve(result as boolean);
    else request.resolve(result as string | null);
    setRequest(null);
  };

  const alertDialog = (message: string, options?: AlertOptions) =>
    new Promise<void>((resolve) => {
      resolvedRef.current = false;
      setRequest({ kind: "alert", message, options, resolve });
    });

  const confirmDialog = (message: string, options?: ConfirmOptions) =>
    new Promise<boolean>((resolve) => {
      resolvedRef.current = false;
      setRequest({ kind: "confirm", message, options, resolve });
    });

  const promptDialog = (message: string, options?: PromptOptions) =>
    new Promise<string | null>((resolve) => {
      resolvedRef.current = false;
      setInputValue(options?.defaultValue ?? "");
      setRequest({ kind: "prompt", message, options, resolve });
    });

  const promptRequired = request?.kind === "prompt" && request.options?.required;
  const promptDisabled = promptRequired && !inputValue.trim();
  const confirmLabel = !request
    ? ""
    : request.kind === "alert"
      ? request.options?.okLabel ?? "Đã hiểu"
      : request.options?.confirmLabel ?? "Xác nhận";

  return (
    <DialogContext.Provider value={{ alertDialog, confirmDialog, promptDialog }}>
      {children}
      {request && (
        <Modal
          open
          onClose={() => closeWith(request.kind === "alert" ? undefined : request.kind === "confirm" ? false : null)}
          title={request.options?.title ?? (request.kind === "alert" ? "Thông báo" : request.kind === "confirm" ? "Xác nhận" : "Nhập thông tin")}
          size="md"
        >
          <p className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed">{request.message}</p>

          {request.kind === "prompt" &&
            (request.options?.multiline ? (
              <textarea
                autoFocus
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                placeholder={request.options?.placeholder}
                rows={3}
                className="w-full mt-3 bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
              />
            ) : (
              <input
                autoFocus
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                placeholder={request.options?.placeholder}
                className="w-full mt-3 bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
              />
            ))}

          <div className="flex justify-end gap-2 mt-5">
            {request.kind !== "alert" && (
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => closeWith(request.kind === "confirm" ? false : null)}
              >
                {request.options?.cancelLabel ?? "Hủy"}
              </Button>
            )}
            <Button
              type="button"
              variant={request.kind === "confirm" && request.options?.danger ? "danger" : "primary"}
              size="sm"
              disabled={request.kind === "prompt" && promptDisabled}
              onClick={() => closeWith(request.kind === "alert" ? undefined : request.kind === "confirm" ? true : inputValue)}
            >
              {confirmLabel}
            </Button>
          </div>
        </Modal>
      )}
    </DialogContext.Provider>
  );
}

export function useDialog(): DialogContextValue {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error("useDialog() phải dùng bên trong <DialogProvider>.");
  return ctx;
}
