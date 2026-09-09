import { useEffect, useState } from "react";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import { ClassResponse, listClasses, listClassTeachers } from "../api";

/**
 * Danh sách lớp người dùng hiện tại được phép thao tác, theo đúng điểm trường đang chọn ở Header.
 * Dùng chung cho mọi màn có lọc theo lớp (Sổ điểm UC-19/20, Điểm danh UC-15, Soạn & giao đề UC-40,
 * Nhận xét UC-21/22, Kho bài giảng UC-23) — trước đây mỗi trang tự lặp lại đúng logic này.
 *
 * - Có phân công thật (đứng tên trong class_teachers của ít nhất 1 lớp): LUÔN chỉ thấy đúng những
 *   lớp đó — kể cả khi tài khoản còn có thêm quyền quản trị rộng hơn (VD tài khoản demo vừa dạy 1
 *   lớp vừa có academic.class.manage để tiện test nhiều màn). Quyền quản trị không được phép nới
 *   rộng phạm vi của 1 tài khoản đã có phân công cụ thể — mỗi người chỉ thao tác đúng phạm vi
 *   điểm trường + lớp học được phân, không tự nhiên thấy thêm lớp khác.
 * - Không có phân công thật nào (VD HEAD_ACADEMIC/SYS_ADMIN thuần, hoặc SITE_MANAGER không đứng
 *   lớp): academic.class.manage, academic.class.view-all (V64, bổ sung ngoài SDD gốc, đã xác
 *   nhận với người dùng 2026-07-30 — dành riêng cho Trưởng phòng đào tạo/Quản trị viên, tách
 *   khỏi academic.class.manage vốn mang nghĩa UC-18 "xếp lớp"), hoặc SITE_MANAGER mới cho thấy
 *   hết lớp thuộc site đang chọn (SITE_MANAGER cần xem lại sổ điểm bất kỳ lớp nào mình quản lý
 *   — UC-20).
 *
 * `myAssignedClassCount` LUÔN tính riêng theo class_teachers thật, tách biệt khỏi `classes` — dùng
 * ở Header để quyết định hiện/ẩn pill "Lớp" theo đúng có-được-phân-công-hay-không, không dựa vào quyền.
 */
export function useEligibleClasses() {
  const { hasPermission, currentUser, selectedCampusId } = useApp();
  const isSiteManager = currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false;
  const canSeeAllClasses = hasPermission("academic.class.manage") || hasPermission("academic.class.view-all") || isSiteManager;

  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [myAssignedClassCount, setMyAssignedClassCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    listClasses({ siteId: selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined })
      .then(async (allClasses) => {
        if (cancelled || !currentUser) {
          if (!cancelled) setClasses(canSeeAllClasses ? allClasses.filter((c) => c.status === "IN_PROGRESS") : []);
          return;
        }
        const teacherLists = await Promise.all(allClasses.map((c) => listClassTeachers(c.id).catch(() => [])));
        if (cancelled) return;
        // Dropdown "Lớp" cạnh "Điểm trường" chỉ để chọn lớp đang thao tác hàng ngày (sổ điểm, điểm
        // danh, giao đề...) nên chỉ hiện lớp đang học (IN_PROGRESS) — lớp chưa khai giảng/đã kết
        // thúc/đã hủy không có tác vụ nào để chọn ở đây.
        const inProgressClasses = allClasses.filter((c) => c.status === "IN_PROGRESS");
        const assignedClasses = inProgressClasses.filter((c) =>
          teacherLists[allClasses.indexOf(c)].some((t) => t.teacherUserId === currentUser.id && !t.assignedTo)
        );
        setMyAssignedClassCount(assignedClasses.length);
        // Có phân công thật thì LUÔN dùng đúng phạm vi đó, dù canSeeAllClasses cũng đúng — quyền
        // quản trị chỉ mở rộng phạm vi cho tài khoản KHÔNG đứng lớp nào thật (admin/site manager thuần).
        setClasses(assignedClasses.length > 0 ? assignedClasses : canSeeAllClasses ? inProgressClasses : []);
      })
      .catch(() => {
        if (!cancelled) {
          setClasses([]);
          setMyAssignedClassCount(0);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [canSeeAllClasses, currentUser, selectedCampusId]);

  return { classes, myAssignedClassCount, loading };
}
