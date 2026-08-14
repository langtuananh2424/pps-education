import React, { useState } from "react";
import { ChevronDown, Eye, EyeOff, Lock, Mail } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import GoogleSignInButton from "../components/GoogleSignInButton";
import LanguageSwitcher from "@/components/ui/LanguageSwitcher";
import heroBoy from "@/assets/hero-boy.png";
import heroUnicorn from "@/assets/hero-unicorn.png";
import heroGirl from "@/assets/hero-girl.png";

/** Không gọi navigate thật cho các mục menu/CTA — đây là placeholder trực quan theo đúng bản thiết kế, chưa có trang đích thật. */
const preventNav = (e: React.MouseEvent) => e.preventDefault();

export default function LoginPage() {
  const { t } = useTranslation("auth");
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
      setError(err instanceof ApiError ? err.message : t("errors.loginFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-hero-page">
      <nav className="navbar">
        <div className="navbar-inner">
          <div className="brand">
            <div className="brand-text">
              <div className="top">{t("brand.top")}</div>
              <div className="bottom">{t("brand.bottom")}</div>
            </div>
          </div>

          <ul className="nav-links">
            <li>
              <a href="#" className="" onClick={preventNav}>
                {t("nav.about")}
              </a>
            </li>
            <li>
              <a href="#" onClick={preventNav}>
                {t("nav.corner")} <ChevronDown />
              </a>
            </li>
            <li>
              <a href="#" onClick={preventNav}>
                {t("nav.coordinator")}
              </a>
            </li>
            <li>
              <a href="#" onClick={preventNav}>
                {t("nav.news")}
              </a>
            </li>
            <li>
              <a href="#" onClick={preventNav}>
                {t("nav.contact")}
              </a>
            </li>
          </ul>
        </div>
        <div style={{ position: "absolute", right: 40, top: "50%", transform: "translateY(-50%)" }}>
          <LanguageSwitcher />
        </div>
      </nav>

      <main className="wrap">
        <section className="hero">
          {/* <div className="eyebrow">
            <span className="dot" /> CHÀO MỪNG TRỞ LẠI VỚI CHÚNG TÔI
          </div> */}
          <h1 className="headline">
            {t("hero.headlineLine1")}
            <br />
            {t("hero.headlineLine2Word")} <span className="accent">{t("hero.headlineAccent")}</span>
          </h1>
          <p className="sub">{t("hero.subtitle")}</p>

          <div className="stage">
            <div className="shadow-floor" />
            <div className="char boy" title={t("hero.boyTitle")}>
              <img src={heroBoy} alt={t("hero.boyAlt")} />
            </div>
            <div className="char unicorn" title={t("hero.unicornTitle")}>
              <img src={heroUnicorn} alt={t("hero.unicornAlt")} />
            </div>
            <div className="char girl" title={t("hero.boyTitle")}>
              <img src={heroGirl} alt={t("hero.girlAlt")} />
            </div>
          </div>
        </section>

        <section className="card">
          <h2>{t("card.title")}</h2>
          <p className="card-sub">{t("card.subtitle")}</p>

          <form onSubmit={handleSubmit}>
            {error && (
              <div style={{ marginBottom: 16 }} className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">
                {error}
              </div>
            )}

            <div className="field">
              <label htmlFor="usernameOrEmail">{t("card.emailLabel")}</label>
              <div className="input-row">
                <Mail size={17} />
                <input
                  id="usernameOrEmail"
                  type="text"
                  placeholder={t("card.emailPlaceholder")}
                  value={usernameOrEmail}
                  onChange={(e) => setUsernameOrEmail(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="field">
              <label htmlFor="password">{t("card.passwordLabel")}</label>
              <div className="input-row">
                <Lock size={17} />
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder={t("card.passwordPlaceholder")}
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
                {t("card.forgotPassword")}
              </a>
              <button type="submit" className="login-btn" disabled={submitting}>
                {submitting ? t("card.signingIn") : t("card.signIn")}
              </button>
            </div>
          </form>

          <div className="divider">{t("card.orDivider")}</div>

          <GoogleSignInButton onSuccess={() => setError(null)} onError={setError} />
        </section>
      </main>
    </div>
  );
}
