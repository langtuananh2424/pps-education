import i18n from "i18next";
import { initReactI18next } from "react-i18next";

import commonEn from "./locales/en/common.json";
import authEn from "./locales/en/auth.json";
import dashboardEn from "./locales/en/dashboard.json";
import commonVi from "./locales/vi/common.json";
import authVi from "./locales/vi/auth.json";
import dashboardVi from "./locales/vi/dashboard.json";

export const LANGUAGE_STORAGE_KEY = "pps_language";

const storedLanguage = typeof window !== "undefined" ? window.localStorage.getItem(LANGUAGE_STORAGE_KEY) : null;

i18n.use(initReactI18next).init({
  resources: {
    vi: { common: commonVi, auth: authVi, dashboard: dashboardVi },
    en: { common: commonEn, auth: authEn, dashboard: dashboardEn }
  },
  lng: storedLanguage === "en" ? "en" : "vi",
  fallbackLng: "vi",
  defaultNS: "common",
  ns: ["common", "auth", "dashboard"],
  interpolation: { escapeValue: false },
  returnEmptyString: false
});

export default i18n;
