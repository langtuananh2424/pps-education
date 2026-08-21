import React from "react";
import { useTranslation } from "react-i18next";
import { Languages } from "lucide-react";
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
    <div className="flex items-center gap-1 text-[11px] font-bold px-1.5 py-1 rounded-full bg-white border border-slate-200/50 shadow-soft">
      <Languages className="w-3.5 h-3.5 text-slate-400 ml-1" />
      {LANGUAGES.map(({ code, label }) => (
        <button
          key={code}
          type="button"
          onClick={() => handleChange(code)}
          aria-pressed={currentLanguage === code}
          className={`px-2 py-1 rounded-full transition-colors cursor-pointer ${
            currentLanguage === code ? "bg-brand-gradient text-white" : "text-slate-500 hover:text-slate-800"
          }`}
        >
          {label}
        </button>
      ))}
    </div>
  );
}
