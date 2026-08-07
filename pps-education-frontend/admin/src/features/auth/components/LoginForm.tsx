import React, { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";
import GoogleSignInButton from "./GoogleSignInButton";
import { useDialog } from "@/components/ui/DialogProvider";

const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

interface LoginFormProps {
  usernameOrEmail: string;
  onUsernameOrEmailChange: (value: string) => void;
  onLoginSuccess: () => void;
}

export default function LoginForm({ usernameOrEmail, onUsernameOrEmailChange, onLoginSuccess }: LoginFormProps) {
  const { login } = useApp();
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const { alertDialog } = useDialog();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!usernameOrEmail.trim()) {
      setError("Vui lòng điền tài khoản hoặc email đăng nhập.");
      return;
    }
    if (!password) {
      setError("Vui lòng điền mật khẩu.");
      return;
    }

    setLoading(true);
    try {
      await login(usernameOrEmail.trim(), password);
      onLoginSuccess();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Không thể kết nối tới hệ thống. Vui lòng thử lại.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="space-y-2">
        <h2 className="text-3xl font-extrabold text-slate-900 tracking-tight font-display uppercase leading-none">WELCOME BACK</h2>
        <p className="text-sm text-slate-400 font-medium">Welcome back! Please enter your details.</p>
      </div>

      {error && (
        <div className="p-3 bg-rose-50 border border-rose-100 text-rose-700 text-sm rounded-xl font-semibold flex items-center gap-2 animate-in fade-in mt-6">
          <div className="w-1.5 h-1.5 rounded-full bg-rose-500 shrink-0 animate-ping" />
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4 mt-6">
        <div className="space-y-1.5">
          <label className="text-sm font-bold text-slate-700 tracking-wide block pl-0.5">Tài khoản hoặc Email</label>
          <input
            type="text"
            required
            placeholder="username hoặc email@pps.edu.vn"
            value={usernameOrEmail}
            onChange={(e) => {
              onUsernameOrEmailChange(e.target.value);
              setError(null);
            }}
            className="w-full bg-white border border-slate-200 text-sm px-4 py-3 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#EA580C]/10 focus:border-[#EA580C] text-slate-800 font-medium transition-all"
          />
        </div>

        <div className="space-y-1.5">
          <label className="text-sm font-bold text-slate-700 tracking-wide block pl-0.5">Mật khẩu</label>
          <div className="relative">
            <input
              type={showPassword ? "text" : "password"}
              required
              placeholder="••••••••••••"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setError(null);
              }}
              className="w-full bg-white border border-slate-200 text-sm px-4 py-3 pr-12 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#EA580C]/10 focus:border-[#EA580C] text-slate-800 font-medium transition-all"
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-4 top-3 text-slate-400 hover:text-slate-600 transition-colors"
            >
              {showPassword ? <EyeOff className="w-4.5 h-4.5" /> : <Eye className="w-4.5 h-4.5" />}
            </button>
          </div>
        </div>

        <div className="flex items-center justify-between pt-1">
          <label className="flex items-center gap-2 text-sm text-slate-500 cursor-pointer select-none">
            <input type="checkbox" checked={rememberMe} onChange={() => setRememberMe(!rememberMe)} className="sr-only" />
            <div
              className={`w-4 h-4 rounded border flex items-center justify-center transition-all ${
                rememberMe ? "border-[#EA580C] bg-[#EA580C]" : "border-slate-200 bg-white"
              }`}
            >
              {rememberMe && (
                <svg className="w-2.5 h-2.5 text-white stroke-[3.5]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                </svg>
              )}
            </div>
            <span className="font-medium text-slate-600">Remember me</span>
          </label>
          <a
            href="#forgot"
            onClick={(e) => {
              e.preventDefault();
              alertDialog("Tính năng Khôi phục mật khẩu đang khóa. Vui lòng liên hệ Admin qua admin@pps.edu.vn.");
            }}
            className="text-sm font-semibold text-slate-500 hover:text-[#EA580C] transition-colors"
          >
            Forgot password
          </a>
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full bg-[#EA580C] hover:bg-[#D94E07] active:scale-[0.99] text-white font-bold text-sm py-3.5 rounded-xl flex items-center justify-center gap-2 shadow-[0_4px_14px_rgba(234,88,12,0.2)] transition-all disabled:opacity-50 cursor-pointer text-center mt-6"
        >
          {loading ? (
            <>
              <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              <span>Sign-in...</span>
            </>
          ) : (
            <span>Sign in</span>
          )}
        </button>
      </form>

      {googleClientId && (
        <>
          <div className="flex items-center gap-3 mt-5">
            <div className="flex-1 h-px bg-slate-100" />
            <span className="text-sm font-semibold text-slate-400 uppercase tracking-wider">Hoặc</span>
            <div className="flex-1 h-px bg-slate-100" />
          </div>

          <div className="mt-4">
            <GoogleSignInButton
              onSuccess={onLoginSuccess}
              onError={(message) => setError(message)}
            />
          </div>
        </>
      )}
    </>
  );
}
