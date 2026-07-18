import React, { useState } from "react";
import { ChevronDown, Eye, EyeOff, Lock, Mail, User } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import heroBoy from "@/assets/hero-boy.png";
import heroUnicorn from "@/assets/hero-unicorn.png";
import heroGirl from "@/assets/hero-girl.png";

/** Không gọi navigate thật cho các mục menu/CTA — đây là placeholder trực quan theo đúng bản thiết kế, chưa có trang đích thật. */
const preventNav = (e: React.MouseEvent) => e.preventDefault();

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48">
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.3 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.6 6.5 29.6 4.5 24 4.5 12.9 4.5 4 13.4 4 24.5s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.7-.4-4z" />
      <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.6 15.9 18.9 13 24 13c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.6 6.5 29.6 4.5 24 4.5c-7.5 0-14 4.2-17.7 10.2z" />
      <path fill="#4CAF50" d="M24 44.5c5.5 0 10.4-1.9 14.2-5.1l-6.6-5.4C29.6 35.6 27 36.5 24 36.5c-5.3 0-9.7-3.3-11.3-8l-6.6 5.1C9.9 39.9 16.4 44.5 24 44.5z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-1 2.8-2.9 5.1-5.3 6.6l6.6 5.4C40.5 36.7 44 31.1 44 24.5c0-1.3-.1-2.7-.4-4z" />
    </svg>
  );
}

export default function LoginPage() {
  const { login } = useApp();
  const [usernameOrEmail, setUsernameOrEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(usernameOrEmail.trim(), password);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đăng nhập thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-hero-page">
      <nav className="navbar">
        <div className="brand">
          <div className="brand-text">
            <div className="top">PPS Education</div>
            <div className="bottom">CỔNG THÔNG TIN</div>
          </div>
        </div>

        <ul className="nav-links">
          <li>
            <a href="#" className="active" onClick={preventNav}>
              Về chúng tôi
            </a>
          </li>
          <li>
            <a href="#" onClick={preventNav}>
              Góc học tập <ChevronDown />
            </a>
          </li>
          <li>
            <a href="#" onClick={preventNav}>
              Điều phối viên
            </a>
          </li>
          <li>
            <a href="#" onClick={preventNav}>
              Tin tức &amp; Sự kiện
            </a>
          </li>
          <li>
            <a href="#" onClick={preventNav}>
              Liên hệ
            </a>
          </li>
        </ul>

        <div className="nav-right">
          <div className="icon-btn">
            <User size={17} />
          </div>
          <button className="cta-btn" type="button" onClick={preventNav}>
            Dùng thử miễn phí
          </button>
          <div className="lang">
            Vi <ChevronDown size={10} />
          </div>
        </div>
      </nav>

      <main className="wrap">
        <section className="hero">
          {/* <div className="eyebrow">
            <span className="dot" /> CHÀO MỪNG TRỞ LẠI VỚI CHÚNG TÔI
          </div> */}
          <h1 className="headline">
            Chào mừng
            <br />
            bạn <span className="accent">trở lại!</span>
          </h1>
          <p className="sub">Học viên là số một! Gắn kết để phát triển, đồng hành để bứt phá.</p>

          <div className="stage">
            <div className="shadow-floor" />
            <div className="char boy" title="Di chuột để xem mình nổi lên nè!">
              <img src={heroBoy} alt="Bạn nhỏ nam" />
            </div>
            <div className="char unicorn" title="Chạm vào kỳ lân nào!">
              <img src={heroUnicorn} alt="Kỳ lân linh vật PPS Education" />
            </div>
            <div className="char girl" title="Di chuột để xem mình nổi lên nè!">
              <img src={heroGirl} alt="Bạn nhỏ nữ" />
            </div>
          </div>
        </section>

        <section className="card">
          <h2>Đăng nhập vào tài khoản</h2>
          <p className="card-sub">Nhập thông tin để tiếp tục hành trình khám phá của bạn</p>

          <form onSubmit={handleSubmit}>
            {error && (
              <div style={{ marginBottom: 16 }} className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">
                {error}
              </div>
            )}

            <div className="field">
              <label htmlFor="usernameOrEmail">Email của bạn</label>
              <div className="input-row">
                <Mail size={17} />
                <input
                  id="usernameOrEmail"
                  type="text"
                  placeholder="Nhập email của bạn"
                  value={usernameOrEmail}
                  onChange={(e) => setUsernameOrEmail(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="field">
              <label htmlFor="password">Mật khẩu</label>
              <div className="input-row">
                <Lock size={17} />
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Nhập mật khẩu"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <button type="button" className="toggle-eye" onClick={() => setShowPassword((v) => !v)}>
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <div className="row-between">
              <a href="#" className="forgot" onClick={preventNav}>
                Quên mật khẩu?
              </a>
              <button type="submit" className="login-btn" disabled={submitting}>
                {submitting ? "Đang..." : "Đăng nhập"}
              </button>
            </div>
          </form>

          <div className="divider">Hoặc đăng nhập nhanh</div>

          <button className="google-btn" type="button" onClick={preventNav}>
            <GoogleIcon />
            Google
          </button>

          <p className="register">
            Bạn chưa có tài khoản?{" "}
            <a href="#" onClick={preventNav}>
              Đăng ký ngay
            </a>
          </p>
        </section>
      </main>
    </div>
  );
}
