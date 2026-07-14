import React, { useEffect, useState } from "react";
import { GraduationCap } from "lucide-react";
import { CommentEntry, GradeEntry, Student } from "@/types";
import { mockClassrooms } from "@/data/mockData";
import { readStoredCommentEntries, readStoredGradeEntries, readStoredStudents, writeStoredCommentEntries, writeStoredGradeEntries, writeStoredStudents } from "../storage";
import { getStatusLabel } from "../utils";
import NotificationBanner from "../components/NotificationBanner";
import StudentListPanel from "../components/StudentListPanel";
import StudentDetailPanel from "../components/StudentDetailPanel";
import StudentFormModal, { StudentFormValues } from "../components/StudentFormModal";

const emptyForm: StudentFormValues = {
  fullName: "",
  birthDate: "2018-05-15",
  avatarUrl: "",
  phone: "",
  parentName: "",
  parentPhone: "",
  campusId: "CAMP-01",
  classIds: ["CLS-01"],
  status: "STUDYING"
};

function toFormValues(s: Student): StudentFormValues {
  return {
    fullName: s.fullName,
    birthDate: s.birthDate,
    avatarUrl: s.avatarUrl || "",
    phone: s.phone,
    parentName: s.parentName,
    parentPhone: s.parentPhone,
    campusId: s.campusId,
    classIds: s.classIds,
    status: s.status
  };
}

export default function ProfilesPage() {
  const [students, setStudents] = useState<Student[]>(readStoredStudents);
  const [gradeEntries, setGradeEntries] = useState<GradeEntry[]>(readStoredGradeEntries);
  const [commentEntries, setCommentEntries] = useState<CommentEntry[]>(readStoredCommentEntries);
  const [selectedStudentId, setSelectedStudentId] = useState<string | null>(students[0]?.id || null);
  const [searchTerm, setSearchTerm] = useState("");
  const [notification, setNotification] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [editingStudent, setEditingStudent] = useState<Student | null>(null);

  useEffect(() => writeStoredStudents(students), [students]);
  useEffect(() => writeStoredGradeEntries(gradeEntries), [gradeEntries]);
  useEffect(() => writeStoredCommentEntries(commentEntries), [commentEntries]);

  const classrooms = mockClassrooms;
  const filteredStudents = students.filter(
    (s) => s.fullName.toLowerCase().includes(searchTerm.toLowerCase()) || s.parentName.toLowerCase().includes(searchTerm.toLowerCase()) || s.id.toLowerCase().includes(searchTerm.toLowerCase())
  );
  const selectedStudent = students.find((s) => s.id === selectedStudentId) || null;
  const studentComments = commentEntries.filter((c) => c.studentId === selectedStudentId);
  const studentGrade = selectedStudent ? gradeEntries.find((g) => g.studentId === selectedStudentId && g.classId === (selectedStudent.classIds[0] || "")) || null : null;

  const handleUpdateStatus = (studentId: string, newStatus: Student["status"]) => {
    const timeString = new Date().toISOString().substring(0, 10);
    setStudents((prev) =>
      prev.map((s) => (s.id === studentId ? { ...s, status: newStatus, history: [...s.history, `${timeString}: Cập nhật trạng thái học tập sang ${getStatusLabel(newStatus)} (Hệ thống ghi nhận).`] } : s))
    );
    setNotification(`🔄 Đã cập nhật trạng thái học viên sang: ${getStatusLabel(newStatus)}`);
  };

  const handleSubmitForm = (values: StudentFormValues) => {
    const timeString = new Date().toISOString().substring(0, 10);

    if (editingStudent) {
      setStudents((prev) =>
        prev.map((s) =>
          s.id === editingStudent.id
            ? {
                ...s,
                fullName: values.fullName,
                birthDate: values.birthDate,
                avatarUrl: values.avatarUrl.trim() || undefined,
                phone: values.phone,
                parentName: values.parentName,
                parentPhone: values.parentPhone,
                campusId: values.campusId,
                classIds: values.classIds,
                status: values.status,
                history: [...s.history, `${timeString}: Cập nhật thông tin hồ sơ học viên.`]
              }
            : s
        )
      );
      setNotification(`✅ Đã cập nhật thông tin học viên ${values.fullName} thành công!`);
    } else {
      const newId = `STU-00${students.length + 1}`;
      const newStudent: Student = {
        id: newId,
        fullName: values.fullName,
        birthDate: values.birthDate,
        avatarUrl: values.avatarUrl.trim() || undefined,
        phone: values.phone,
        parentName: values.parentName,
        parentPhone: values.parentPhone,
        campusId: values.campusId,
        classIds: values.classIds,
        status: values.status,
        history: [`${timeString}: Khởi tạo hồ sơ học viên mới trên hệ thống.`]
      };
      setStudents((prev) => [...prev, newStudent]);
      setSelectedStudentId(newId);
      setNotification(`✅ Đã tiếp nhận học viên mới: ${values.fullName} (${newId})!`);
    }

    setShowModal(false);
    setEditingStudent(null);
  };

  const handleDelete = (studentId: string) => {
    const student = students.find((s) => s.id === studentId);
    if (!student) return;
    if (!window.confirm(`CẢNH BÁO: Bạn có chắc chắn muốn xóa hồ sơ học sinh "${student.fullName}"? Mọi nhận xét học tập và bảng điểm liên quan sẽ bị xóa.`)) return;

    const updated = students.filter((s) => s.id !== studentId);
    setStudents(updated);
    if (selectedStudentId === studentId) setSelectedStudentId(updated[0]?.id || null);
    setNotification(`❌ Đã xóa hồ sơ học viên ${student.fullName} khỏi hệ thống.`);
  };

  const handleAddComment = (type: CommentEntry["type"], content: string, isWarning: boolean) => {
    if (!selectedStudent) return;
    const primaryClassId = selectedStudent.classIds[0] || "CLS-01";
    const matchedClass = classrooms.find((c) => c.id === primaryClassId);
    const timeString = new Date().toISOString().substring(0, 10);

    const newComment: CommentEntry = {
      id: `CMT-${Date.now().toString().substring(8)}`,
      studentId: selectedStudent.id,
      studentName: selectedStudent.fullName,
      classId: primaryClassId,
      className: matchedClass?.name || "Lớp học",
      type,
      content,
      isWarning,
      status: "APPROVED",
      createdAt: timeString
    };

    setCommentEntries((prev) => [newComment, ...prev]);
    setStudents((prev) =>
      prev.map((s) =>
        s.id === selectedStudent.id
          ? { ...s, history: [...s.history, `${timeString}: Giáo viên gửi đánh giá ${type === "DAILY" ? "hàng ngày" : type === "MIDTERM" ? "giữa kỳ" : "cuối kỳ"} - ${isWarning ? "⚠️ CÓ CẢNH BÁO" : "Bình thường"}.`] }
          : s
      )
    );
    setNotification("✅ Đã cập nhật nhận xét và đồng bộ hóa thành công!");
  };

  const handleSaveGrade = (midterm: number, final: number, comments: string) => {
    if (!selectedStudent) return;
    const primaryClassId = selectedStudent.classIds[0] || "CLS-01";
    const matchedClass = classrooms.find((c) => c.id === primaryClassId);
    const averageNum = Math.round(((midterm + final) / 2) * 10) / 10;
    const timeString = new Date().toISOString().substring(0, 10);
    const existingGrade = gradeEntries.find((g) => g.studentId === selectedStudent.id && g.classId === primaryClassId);

    if (existingGrade) {
      setGradeEntries((prev) =>
        prev.map((g) => (g.id === existingGrade.id ? { ...g, midtermScore: midterm, finalScore: final, averageScore: averageNum, teacherComments: comments, updatedAt: timeString } : g))
      );
    } else {
      const newGrade: GradeEntry = {
        id: `GRD-${Date.now().toString().substring(8)}`,
        studentId: selectedStudent.id,
        studentName: selectedStudent.fullName,
        classId: primaryClassId,
        className: matchedClass?.name || "Lớp học",
        midtermScore: midterm,
        finalScore: final,
        averageScore: averageNum,
        status: "APPROVED",
        teacherComments: comments,
        updatedAt: timeString
      };
      setGradeEntries((prev) => [newGrade, ...prev]);
    }

    setStudents((prev) =>
      prev.map((s) => (s.id === selectedStudent.id ? { ...s, history: [...s.history, `${timeString}: Cập nhật bảng điểm học tập (Giữa kỳ: ${midterm}, Cuối kỳ: ${final}, Trung bình: ${averageNum}).`] } : s))
    );
    setNotification(`✅ Đã cập nhật học bạ lớp ${matchedClass?.name || ""} thành công!`);
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900 flex items-center gap-2">
          <GraduationCap className="w-6 h-6 text-brand-orange" />
          Hồ Sơ Học Sinh & Học Trình
        </h1>
        <p className="text-xs text-slate-500 mt-1">
          Quản lý thông tin học viên (CRUD), theo dõi trạng thái học tập, học bạ điểm số (UC-13/14).
        </p>
      </div>

      <NotificationBanner message={notification} onClose={() => setNotification(null)} />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-7">
          <StudentListPanel
            students={filteredStudents}
            classrooms={classrooms}
            selectedStudentId={selectedStudentId}
            searchTerm={searchTerm}
            onSearchChange={setSearchTerm}
            onSelect={setSelectedStudentId}
            onAdd={() => {
              setEditingStudent(null);
              setShowModal(true);
            }}
            onEdit={(s) => {
              setEditingStudent(s);
              setShowModal(true);
            }}
            onDelete={handleDelete}
          />
        </div>

        <div className="lg:col-span-5">
          <StudentDetailPanel
            student={selectedStudent}
            comments={studentComments}
            grade={studentGrade}
            onUpdateStatus={handleUpdateStatus}
            onAddComment={handleAddComment}
            onSaveGrade={handleSaveGrade}
          />
        </div>
      </div>

      {showModal && (
        <StudentFormModal
          isEdit={!!editingStudent}
          initialValues={editingStudent ? toFormValues(editingStudent) : emptyForm}
          classrooms={classrooms}
          onClose={() => setShowModal(false)}
          onSubmit={handleSubmitForm}
        />
      )}
    </div>
  );
}
