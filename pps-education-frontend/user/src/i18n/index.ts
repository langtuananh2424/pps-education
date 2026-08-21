import i18n from "i18next";
import { initReactI18next } from "react-i18next";

import commonEn from "./locales/en/common.json";
import authEn from "./locales/en/auth.json";
import portalEn from "./locales/en/portal.json";
import portalExercisesEn from "./locales/en/portal-exercises.json";
import portalProgressEn from "./locales/en/portal-progress.json";
import portalScheduleEn from "./locales/en/portal-schedule.json";
import portalGradesEn from "./locales/en/portal-grades.json";
import portalAccountEn from "./locales/en/portal-account.json";
import commonVi from "./locales/vi/common.json";
import authVi from "./locales/vi/auth.json";
import portalVi from "./locales/vi/portal.json";
import portalExercisesVi from "./locales/vi/portal-exercises.json";
import portalProgressVi from "./locales/vi/portal-progress.json";
import portalScheduleVi from "./locales/vi/portal-schedule.json";
import portalGradesVi from "./locales/vi/portal-grades.json";
import portalAccountVi from "./locales/vi/portal-account.json";

export const LANGUAGE_STORAGE_KEY = "pps_language";

const storedLanguage = typeof window !== "undefined" ? window.localStorage.getItem(LANGUAGE_STORAGE_KEY) : null;

i18n.use(initReactI18next).init({
  resources: {
    vi: {
      common: commonVi,
      auth: authVi,
      portal: portalVi,
      "portal-exercises": portalExercisesVi,
      "portal-progress": portalProgressVi,
      "portal-schedule": portalScheduleVi,
      "portal-grades": portalGradesVi,
      "portal-account": portalAccountVi
    },
    en: {
      common: commonEn,
      auth: authEn,
      portal: portalEn,
      "portal-exercises": portalExercisesEn,
      "portal-progress": portalProgressEn,
      "portal-schedule": portalScheduleEn,
      "portal-grades": portalGradesEn,
      "portal-account": portalAccountEn
    }
  },
  lng: storedLanguage === "en" ? "en" : "vi",
  fallbackLng: "vi",
  defaultNS: "common",
  ns: ["common", "auth", "portal", "portal-exercises", "portal-progress", "portal-schedule", "portal-grades", "portal-account"],
  interpolation: { escapeValue: false },
  returnEmptyString: false
});

export default i18n;
