import { useEffect, useState } from "react";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import { ClassResponse, listClasses, listClassTeachers } from "../api";

/**
 * Danh sách lớp người dùng hiện tại được phép thao tác, theo đúng điểm trường đang chọn ở Header.
 * Dùng chung cho mọi màn có lọc theo lớp (Sổ điểm UC-19/20, Điểm danh UC-15, Soạn & giao đề UC-40,
 * Nhận xét UC-21/22, Kho bài giảng UC-23) — trước đây mỗi trang tự lặp lại đúng logic này.
 *
 * - academic.class.manage (HEAD_ACADEMIC/Admin) hoặc SITE_MANAGER: thấy hết lớp thuộc site đang chọn
 *   (SITE_MANAGER không dạy lớp nào nhưng cần xem lại sổ điểm bất kỳ lớp nào mình quản lý — UC-20).
 * - Còn lại (GV thường): chỉ thấy lớp mình có mặt trong class_teachers.
 */
export function useEligibleClasses() {
  const { hasPermission, currentUser, selectedCampusId } = useApp();
  const isSiteManager = currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false;
  const canSeeAllClasses = hasPermission("academic.class.manage") || isSiteManager;

  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    listClasses({ siteId: selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined })
      .then(async (allClasses) => {
        if (cancelled) return;
        if (canSeeAllClasses || !currentUser) {
          setClasses(allClasses);
          return;
        }
        const teacherLists = await Promise.all(allClasses.map((c) => listClassTeachers(c.id).catch(() => [])));
        if (cancelled) return;
        setClasses(allClasses.filter((_, i) => teacherLists[i].some((t) => t.teacherUserId === currentUser.id)));
      })
      .catch(() => {
        if (!cancelled) setClasses([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [canSeeAllClasses, currentUser, selectedCampusId]);

  return { classes, loading };
}
