import React, { useState } from "react";
import { ChevronDown, Eye, EyeOff, Lock, Mail, User } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import GoogleSignInButton from "../components/GoogleSignInButton";
import heroBoy from "@/assets/hero-boy.png";
import heroUnicorn from "@/assets/hero-unicorn.png";
import heroGirl from "@/assets/hero-girl.png";

/** Không gọi navigate thật cho các mục menu/CTA — đây là placeholder trực quan theo đúng bản thiết kế, chưa có trang đích thật. */
const preventNav = (e: React.MouseEvent) => e.preventDefault();

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

          <GoogleSignInButton onSuccess={() => setError(null)} onError={setError} />
        </section>
      </main>
    </div>
  );
}
