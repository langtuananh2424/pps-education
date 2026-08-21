import React from "react";
import { Award, AlertTriangle, BookOpen } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Classroom } from "@/types";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";

interface AcademicDashboardProps {
  classrooms: Classroom[];
}

const gradeDistribution = [
  { gradeKey: "below5", grade: "< 5.0", pct: 5, color: "bg-slate-300" },
  { gradeKey: "r5to65", grade: "5.0 - 6.5", pct: 15, color: "bg-slate-400" },
  { gradeKey: "r65to8", grade: "6.5 - 8.0", pct: 45, color: "bg-brand-orange" },
  { gradeKey: "r8to9", grade: "8.0 - 9.0", pct: 25, color: "bg-brand-yellow" },
  { gradeKey: "r9to10", grade: "9.0 - 10", pct: 10, color: "bg-brand-red animate-pulse" }
];

export default function AcademicDashboard({ classrooms }: AcademicDashboardProps) {
  const { t } = useTranslation("dashboard");
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("academic.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("academic.subtitle")}</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="flex items-center gap-4">
          <div className="p-3 rounded-lg bg-indigo-50 text-indigo-600">
            <BookOpen className="w-6 h-6" />
          </div>
          <div>
            <span className="text-[11px] text-slate-400 font-bold block uppercase tracking-wider font-display">{t("academic.activeCourses")}</span>
            <span className="text-xl font-bold text-slate-800 font-display">{t("academic.classesUnit", { count: classrooms.length })}</span>
          </div>
        </Card>

        <Card className="flex items-center gap-4">
          <div className="p-3 rounded-lg bg-emerald-50 text-emerald-600">
            <Award className="w-6 h-6" />
          </div>
          <div>
            <span className="text-[11px] text-slate-400 font-bold block uppercase tracking-wider font-display">{t("academic.avgAttendanceRate")}</span>
            <span className="text-xl font-bold text-emerald-600 font-display">94.8%</span>
          </div>
        </Card>

        <Card className="flex items-center gap-4">
          <div className="p-3 rounded-lg bg-rose-50 text-rose-600">
            <AlertTriangle className="w-6 h-6" />
          </div>
          <div>
            <span className="text-[11px] text-slate-400 font-bold block uppercase tracking-wider font-display">{t("academic.pendingComments")}</span>
            <span className="text-xl font-bold text-rose-600 font-display">{t("academic.commentsUnit", { count: 2 })}</span>
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <h3 className="text-sm font-bold text-slate-800 font-display mb-3">{t("academic.scheduleTitle")}</h3>
          <div className="divide-y divide-slate-100">
            {classrooms.map((cls) => (
              <div key={cls.id} className="py-3 flex items-center justify-between first:pt-0 last:pb-0">
                <div>
                  <span className="text-xs font-bold text-slate-800">{cls.name}</span>
                  <div className="flex items-center gap-1.5 text-[10px] text-slate-500 mt-1">
                    <span>{t("academic.roomLabel", { room: cls.roomName })}</span>
                    <span>•</span>
                    <span>{t("academic.scheduleLabel", { schedule: cls.schedule })}</span>
                  </div>
                </div>
                <Badge variant={cls.type === "AFFILIATED" ? "warning" : "info"}>
                  {cls.type === "AFFILIATED" ? t("academic.typeAffiliated") : t("academic.typeCenter")}
                </Badge>
              </div>
            ))}
          </div>
        </Card>

        <Card className="flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-slate-800 font-display">{t("academic.gradeChartTitle")}</h3>
            <p className="text-[11px] text-slate-400 mt-0.5">{t("academic.gradeChartSubtitle")}</p>
          </div>

          <div className="h-44 w-full flex items-end justify-between px-6 mt-4 border-b border-slate-100 pb-1">
            {gradeDistribution.map((bar) => (
              <div key={bar.gradeKey} className="flex flex-col items-center gap-1 w-[15%]">
                <div className="h-32 w-full flex items-end justify-center">
                  <div
                    style={{ height: `${bar.pct * 2.5}px` }}
                    className={`w-5 sm:w-8 ${bar.color} rounded-t-xs relative group`}
                  >
                    <div className="absolute bottom-full mb-1 left-1/2 -translate-x-1/2 bg-slate-900 text-white text-[9px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap">
                      {t("academic.studentsPercentTooltip", { percent: bar.pct })}
                    </div>
                  </div>
                </div>
                <span className="text-[9px] font-bold text-slate-500 whitespace-nowrap">{bar.grade}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}
