import i18n from "i18next";
import { initReactI18next } from "react-i18next";

import commonEn from "./locales/en/common.json";
import authEn from "./locales/en/auth.json";
import dashboardEn from "./locales/en/dashboard.json";
import layoutEn from "./locales/en/layout.json";
import academicClassesEn from "./locales/en/academic-classes.json";
import academicGradesEn from "./locales/en/academic-grades.json";
import academicCommentsEn from "./locales/en/academic-comments.json";
import academicCurriculumEn from "./locales/en/academic-curriculum.json";
import academicHomeworkEn from "./locales/en/academic-homework.json";
import lmsQuestionAuthoringEn from "./locales/en/lms-question-authoring.json";
import lmsGradingEn from "./locales/en/lms-grading.json";
import lmsReviewVideoEn from "./locales/en/lms-review-video.json";
import lmsDocumentsEn from "./locales/en/lms-documents.json";
import commonVi from "./locales/vi/common.json";
import authVi from "./locales/vi/auth.json";
import dashboardVi from "./locales/vi/dashboard.json";
import layoutVi from "./locales/vi/layout.json";
import academicClassesVi from "./locales/vi/academic-classes.json";
import academicGradesVi from "./locales/vi/academic-grades.json";
import academicCommentsVi from "./locales/vi/academic-comments.json";
import academicCurriculumVi from "./locales/vi/academic-curriculum.json";
import academicHomeworkVi from "./locales/vi/academic-homework.json";
import lmsQuestionAuthoringVi from "./locales/vi/lms-question-authoring.json";
import lmsGradingVi from "./locales/vi/lms-grading.json";
import lmsReviewVideoVi from "./locales/vi/lms-review-video.json";
import lmsDocumentsVi from "./locales/vi/lms-documents.json";

export const LANGUAGE_STORAGE_KEY = "pps_language";

const storedLanguage = typeof window !== "undefined" ? window.localStorage.getItem(LANGUAGE_STORAGE_KEY) : null;

i18n.use(initReactI18next).init({
  resources: {
    vi: {
      common: commonVi,
      auth: authVi,
      dashboard: dashboardVi,
      layout: layoutVi,
      "academic-classes": academicClassesVi,
      "academic-grades": academicGradesVi,
      "academic-comments": academicCommentsVi,
      "academic-curriculum": academicCurriculumVi,
      "academic-homework": academicHomeworkVi,
      "lms-question-authoring": lmsQuestionAuthoringVi,
      "lms-grading": lmsGradingVi,
      "lms-review-video": lmsReviewVideoVi,
      "lms-documents": lmsDocumentsVi
    },
    en: {
      common: commonEn,
      auth: authEn,
      dashboard: dashboardEn,
      layout: layoutEn,
      "academic-classes": academicClassesEn,
      "academic-grades": academicGradesEn,
      "academic-comments": academicCommentsEn,
      "academic-curriculum": academicCurriculumEn,
      "academic-homework": academicHomeworkEn,
      "lms-question-authoring": lmsQuestionAuthoringEn,
      "lms-grading": lmsGradingEn,
      "lms-review-video": lmsReviewVideoEn,
      "lms-documents": lmsDocumentsEn
    }
  },
  lng: storedLanguage === "en" ? "en" : "vi",
  fallbackLng: "vi",
  defaultNS: "common",
  ns: [
    "common",
    "auth",
    "dashboard",
    "layout",
    "academic-classes",
    "academic-grades",
    "academic-comments",
    "academic-curriculum",
    "academic-homework",
    "lms-question-authoring",
    "lms-grading",
    "lms-review-video",
    "lms-documents"
  ],
  interpolation: { escapeValue: false },
  returnEmptyString: false
});

export default i18n;
