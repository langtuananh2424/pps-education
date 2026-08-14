import i18n from "i18next";
import { initReactI18next } from "react-i18next";

import commonEn from "./locales/en/common.json";
import authEn from "./locales/en/auth.json";
import commonVi from "./locales/vi/common.json";
import authVi from "./locales/vi/auth.json";

export const LANGUAGE_STORAGE_KEY = "pps_language";

const storedLanguage = typeof window !== "undefined" ? window.localStorage.getItem(LANGUAGE_STORAGE_KEY) : null;

i18n.use(initReactI18next).init({
  resources: {
    vi: { common: commonVi, auth: authVi },
    en: { common: commonEn, auth: authEn }
  },
  lng: storedLanguage === "en" ? "en" : "vi",
  fallbackLng: "vi",
  defaultNS: "common",
  ns: ["common", "auth"],
  interpolation: { escapeValue: false },
  returnEmptyString: false
});

export default i18n;
