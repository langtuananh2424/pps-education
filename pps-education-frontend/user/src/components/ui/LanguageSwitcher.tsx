import React from "react";
import { useTranslation } from "react-i18next";
import { LANGUAGE_STORAGE_KEY } from "@/i18n";

const LANGUAGES: { code: "vi" | "en"; label: string }[] = [
  { code: "vi", label: "VI" },
  { code: "en", label: "EN" }
];

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const currentLanguage = i18n.language === "en" ? "en" : "vi";

  const handleChange = (code: "vi" | "en") => {
    if (code === currentLanguage) return;
    i18n.changeLanguage(code);
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, code);
  };

  return (
    <div className="flex items-center gap-0.5 text-[11px] font-bold px-1 py-1 rounded-full bg-white/90 border border-slate-200 shadow-sm">
      {LANGUAGES.map(({ code, label }) => (
        <button
          key={code}
          type="button"
          onClick={() => handleChange(code)}
          aria-pressed={currentLanguage === code}
          className={`px-2.5 py-1 rounded-full transition-colors cursor-pointer ${
            currentLanguage === code ? "bg-[#0e8c86] text-white" : "text-slate-500 hover:text-slate-800"
          }`}
        >
          {label}
        </button>
      ))}
    </div>
  );
}
