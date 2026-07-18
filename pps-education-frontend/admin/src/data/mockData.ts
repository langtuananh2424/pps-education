import {
  UserRole,
  Campus,
  Classroom,
  Course,
  Employee,
  Task,
  AttendanceLog,
  LeaveRequest,
  Student,
  GradeEntry,
  CommentEntry,
  Lecture,
  Exam,
  StudentExamSubmission,
  Invoice,
  Expense,
  Lead,
  ClassroomRoom,
  FeedbackTicket,
  SyllabusPlan,
  Permission,
  RolePermission
} from "../types";

export const mockCampuses: Campus[] = [
  {
    id: "CAMP-01",
    name: "PPS Hai Bà Trưng (Trụ sở chính)",
    address: "250 Phố Huế, Hai Bà Trưng, Hà Nội",
    type: "SELF_OPERATED",
    managerId: "EMP-003",
    managerName: "Trần Đức Nam"
  },
  {
    id: "CAMP-02",
    name: "PPS Cầu Giấy (Cơ sở 2)",
    address: "88 Trần Thái Tông, Cầu Giấy, Hà Nội",
    type: "SELF_OPERATED",
    managerId: "EMP-004",
    managerName: "Nguyễn Thị Mai"
  },
  {
    id: "CAMP-03",
    name: "Trường Tiểu học Nghĩa Tân (Liên kết)",
    address: "14 Tô Hiệu, Cầu Giấy, Hà Nội",
    type: "AFFILIATED",
    contactPerson: "Cô Hiệu Trưởng - Nguyễn Thu Hương",
    contractStatus: "ACTIVE",
    contractExpiryDate: "2027-06-30",
    managerId: "EMP-003",
    managerName: "Trần Đức Nam"
  },
  {
    id: "CAMP-04",
    name: "Trường THCS Lê Quý Đôn (Liên kết)",
    address: "68 Nguyễn Văn Huyên, Cầu Giấy, Hà Nội",
    type: "AFFILIATED",
    contactPerson: "Thầy Hiệu Phó - Phạm Xuân Tấn",
    contractStatus: "PENDING",
    contractExpiryDate: "2026-08-31",
    managerId: "EMP-004",
    managerName: "Nguyễn Thị Mai"
  }
];

export const mockCourses: Course[] = [
  { id: "CRS-01", name: "Tiếng Anh Trẻ Em PPS Junior Starter", code: "PPS-J-STAR" },
  { id: "CRS-02", name: "Tiếng Anh Giao Tiếp Học Thuật - Academic Communication", code: "PPS-A-COMM" },
  { id: "CRS-03", name: "Chương Trình Luyện Thi Cambridge PET/KET", code: "PPS-CAM-PET" },
  { id: "CRS-04", name: "Anh Văn Phản Xạ PPS Active Kids", code: "PPS-ACT-KID" }
];

export const mockClassrooms: Classroom[] = [
  {
    id: "CLS-01",
    name: "Junior-Starter-J1A",
    type: "CENTER",
    campusId: "CAMP-01",
    courseId: "CRS-01",
    teacherId: "EMP-005",
    roomName: "Phòng 201 (Lý thuyết)",
    maxStudents: 20,
    schedule: "Thứ 2, Thứ 4 (18:00 - 19:30)"
  },
  {
    id: "CLS-02",
    name: "Lớp Liên Kết - Nghĩa Tân 3A1",
    type: "AFFILIATED",
    campusId: "CAMP-03",
    courseId: "CRS-04",
    teacherId: "EMP-005",
    roomName: "Phòng 102 (Linh hoạt)",
    maxStudents: 35,
    schedule: "Thứ 3, Thứ 5 (14:00 - 15:30)"
  },
  {
    id: "CLS-03",
    name: "Communication-C2B",
    type: "CENTER",
    campusId: "CAMP-02",
    courseId: "CRS-02",
    teacherId: "EMP-006",
    roomName: "Phòng Lab 1",
    maxStudents: 15,
    schedule: "Thứ Bảy, Chủ Nhật (09:00 - 10:30)"
  }
];

export const mockEmployees: Employee[] = [
  {
    id: "EMP-001",
    fullName: "Lăng Tuấn Anh",
    email: "tuananh.lang@pps.edu.vn",
    phone: "0912345678",
    role: UserRole.EXECUTIVE,
    campusIds: ["CAMP-01", "CAMP-02", "CAMP-03", "CAMP-04"],
    degrees: ["Thạc sĩ Quản lý Giáo dục - ĐH Quốc gia Hà Nội"],
    certificates: ["CEO Giáo dục Chuyên nghiệp"],
    contracts: [{ id: "CTR-001", type: "LABOR", startDate: "2020-01-01", baseSalary: 60000000 }],
    rewards: ["Lãnh đạo xuất sắc PPS 2025"],
    disciplines: [],
    joinedDate: "2020-01-01",
    status: "ACTIVE"
  },
  {
    id: "EMP-002",
    fullName: "Vũ Khánh Linh",
    email: "linhvk@pps.edu.vn",
    phone: "0923456789",
    role: UserRole.HEAD_ACADEMIC,
    campusIds: ["CAMP-01", "CAMP-02"],
    degrees: ["Cử nhân Sư phạm Tiếng Anh - ĐH Ngoại Ngữ"],
    certificates: ["IELTS 8.5", "TESOL", "TKT Cambridge"],
    contracts: [{ id: "CTR-002", type: "LABOR", startDate: "2021-06-01", baseSalary: 35000000 }],
    rewards: ["Đóng góp Sáng tạo Học thuật 2024"],
    disciplines: [],
    joinedDate: "2021-06-01",
    status: "ACTIVE"
  },
  {
    id: "EMP-003",
    fullName: "Trần Đức Nam",
    email: "namtd@pps.edu.vn",
    phone: "0934567890",
    role: UserRole.SITE_MANAGER,
    campusIds: ["CAMP-01", "CAMP-03"],
    degrees: ["Cử nhân Quản trị Kinh doanh - ĐH Ngoại Thương"],
    certificates: ["Quản lý Điểm trường Chuyên nghiệp"],
    contracts: [{ id: "CTR-003", type: "LABOR", startDate: "2022-03-15", baseSalary: 25000000 }],
    rewards: ["Cơ sở Tiên tiến 2025"],
    disciplines: [],
    joinedDate: "2022-03-15",
    status: "ACTIVE"
  },
  {
    id: "EMP-004",
    fullName: "Nguyễn Thị Mai",
    email: "maint@pps.edu.vn",
    phone: "0945678901",
    role: UserRole.SITE_MANAGER,
    campusIds: ["CAMP-02", "CAMP-04"],
    degrees: ["Cử nhân Sư phạm Tiếng Anh - ĐH Sư Phạm Hà Nội"],
    certificates: ["Quản lý Giáo dục Độc lập"],
    contracts: [{ id: "CTR-004", type: "LABOR", startDate: "2023-01-10", baseSalary: 24000000 }],
    rewards: [],
    disciplines: [],
    joinedDate: "2023-01-10",
    status: "ACTIVE"
  },
  {
    id: "EMP-005",
    fullName: "Lê Thu Hà",
    email: "halet@pps.edu.vn",
    phone: "0956789012",
    role: UserRole.TEACHER,
    campusIds: ["CAMP-01", "CAMP-03"],
    degrees: ["Cử nhân Ngôn ngữ Anh - ĐH Hà Nội"],
    certificates: ["IELTS 8.0", "CELTA Cambridge"],
    contracts: [{ id: "CTR-005", type: "COLLABORATOR", startDate: "2023-09-01", baseSalary: 18000000 }],
    rewards: ["Giáo viên được yêu thích nhất Quý 4/2025"],
    disciplines: [],
    joinedDate: "2023-09-01",
    status: "ACTIVE"
  },
  {
    id: "EMP-006",
    fullName: "Michael Smith",
    email: "michael@pps.edu.vn",
    phone: "0967890123",
    role: UserRole.TEACHER,
    campusIds: ["CAMP-02", "CAMP-04"],
    degrees: ["Bachelor of Arts in English - University of Leeds"],
    certificates: ["TEFL", "CELTA"],
    contracts: [{ id: "CTR-006", type: "LABOR", startDate: "2024-02-01", baseSalary: 42000000 }],
    rewards: [],
    disciplines: [],
    joinedDate: "2024-02-01",
    status: "ACTIVE"
  },
  {
    id: "EMP-007",
    fullName: "Phạm Hồng Hạnh",
    email: "hanhph@pps.edu.vn",
    phone: "0978901234",
    role: UserRole.HR_MANAGER,
    campusIds: ["CAMP-01", "CAMP-02"],
    degrees: ["Cử nhân Quản trị Nhân lực - ĐH Kinh tế Quốc dân"],
    certificates: ["Chứng chỉ HR Chuyên nghiệp - SHRM"],
    contracts: [{ id: "CTR-007", type: "LABOR", startDate: "2022-09-01", baseSalary: 22000000 }],
    rewards: [],
    disciplines: [],
    joinedDate: "2022-09-01",
    status: "ACTIVE"
  },
  {
    id: "EMP-008",
    fullName: "Đỗ Gia Bảo",
    email: "baodg@pps.edu.vn",
    phone: "0989012345",
    role: UserRole.STAFF,
    campusIds: ["CAMP-01", "CAMP-03"],
    degrees: ["Cử nhân Xã hội học - ĐH KHXH & Nhân văn"],
    certificates: ["Tư vấn Tuyển sinh Chuyên nghiệp"],
    contracts: [{ id: "CTR-008", type: "LABOR", startDate: "2024-05-15", baseSalary: 12000000 }],
    rewards: ["Chiến thần tuyển sinh Tháng 6/2026"],
    disciplines: [],
    joinedDate: "2024-05-15",
    status: "ACTIVE"
  },
  {
    id: "EMP-009",
    fullName: "Nguyễn Thu Thủy",
    email: "thuynt@pps.edu.vn",
    phone: "0990123456",
    role: UserRole.STAFF,
    campusIds: ["CAMP-01", "CAMP-02", "CAMP-03", "CAMP-04"],
    degrees: ["Cử nhân Kế toán - Học viện Tài chính"],
    certificates: ["Kế toán trưởng"],
    contracts: [{ id: "CTR-009", type: "LABOR", startDate: "2021-11-01", baseSalary: 16000000 }],
    rewards: [],
    disciplines: [],
    joinedDate: "2021-11-01",
    status: "ACTIVE"
  },
  {
    id: "EMP-010",
    fullName: "Nguyễn Văn Hùng",
    email: "hungnv@pps.edu.vn",
    phone: "0901234567",
    role: UserRole.OPS_MANAGER,
    campusIds: ["CAMP-01", "CAMP-02", "CAMP-03", "CAMP-04"],
    degrees: ["Kỹ sư Quản lý Chuỗi cung ứng - ĐH Bách Khoa Hà Nội"],
    certificates: ["Quản lý Vận hành Chuỗi Giáo dục"],
    contracts: [{ id: "CTR-010", type: "LABOR", startDate: "2021-02-15", baseSalary: 30000000 }],
    rewards: ["Tối ưu Vận hành xuất sắc 2025"],
    disciplines: [],
    joinedDate: "2021-02-15",
    status: "ACTIVE"
  }
];

export const mockTasks: Task[] = [
  {
    id: "TSK-001",
    title: "Triển khai họp hành chính với TH Nghĩa Tân",
    description: "Lên biên bản tiếp nhận công văn tuần mới, thống nhất lịch phòng linh hoạt tuần tới của lớp Nghĩa Tân 3A1.",
    assignedTo: "Trần Đức Nam",
    assignedBy: "Nguyễn Văn Hùng",
    departmentId: "DEPT-OPS",
    deadline: "2026-07-15",
    status: "IN_PROGRESS",
    createdAt: "2026-07-10",
    comments: [
      { id: "C-1", authorName: "Nguyễn Văn Hùng", content: "Lưu ý kiểm tra lại phòng học số 102 xem thiết bị chiếu có mượt không.", createdAt: "2026-07-10 10:00" }
    ]
  },
  {
    id: "TSK-002",
    title: "Hoàn thiện ngân hàng câu hỏi Nói - active kids",
    description: "Soạn và kiểm duyệt 50 mẫu câu luyện phát âm cho các khối lớp Junior.",
    assignedTo: "Lê Thu Hà",
    assignedBy: "Vũ Khánh Linh",
    departmentId: "DEPT-ACAD",
    deadline: "2026-07-12",
    status: "TODO",
    createdAt: "2026-07-09"
  },
  {
    id: "TSK-003",
    title: "Tổng hợp đối soát đóng học phí đợt 1 tháng 7",
    description: "Quét và kiểm tra tự động trạng thái các hóa đơn có mã QR, lập biên bản đối chiếu gửi Ban giám đốc.",
    assignedTo: "Nguyễn Thu Thủy",
    assignedBy: "Lăng Tuấn Anh",
    departmentId: "DEPT-FIN",
    deadline: "2026-07-11",
    status: "WAITING_APPROVAL",
    createdAt: "2026-07-08",
    comments: [
      { id: "C-2", authorName: "Nguyễn Thu Thủy", content: "Đã hoàn thành quét ngân hàng, có 3 giao dịch lỗi chuyển khoản sai cú pháp đã sửa.", createdAt: "2026-07-10 16:30" }
    ]
  }
];

export const mockAttendanceLogs: AttendanceLog[] = [
  { id: "ATT-001", employeeId: "EMP-005", employeeName: "Lê Thu Hà", time: "2026-07-10 07:55", method: "FINGERPRINT", campusId: "CAMP-01", status: "SUCCESS" },
  { id: "ATT-002", employeeId: "EMP-008", employeeName: "Đỗ Gia Bảo", time: "2026-07-10 08:02", method: "FACE", campusId: "CAMP-01", status: "SUCCESS" },
  { id: "ATT-003", employeeId: "EMP-006", employeeName: "Michael Smith", time: "2026-07-10 08:15", method: "GPS", campusId: "CAMP-02", status: "SUCCESS" }
];

export const mockLeaveRequests: LeaveRequest[] = [
  {
    id: "LEV-001",
    employeeId: "EMP-008",
    employeeName: "Đỗ Gia Bảo",
    departmentName: "Phòng Tuyển sinh & CSKH",
    type: "LEAVE",
    startDate: "2026-07-15",
    endDate: "2026-07-16",
    reason: "Khám sức khỏe định kỳ tại bệnh viện",
    steps: [
      { role: UserRole.SITE_MANAGER, approverName: "Trần Đức Nam", status: "APPROVED", notes: "Duyệt nghỉ 1 ngày, bàn giao công việc tuyển sinh cho chị Thảo.", updatedAt: "2026-07-10 09:30" },
      { role: UserRole.OPS_MANAGER, status: "PENDING" }
    ],
    currentStepIndex: 1,
    status: "PENDING",
    createdAt: "2026-07-09"
  },
  {
    id: "LEV-002",
    employeeId: "EMP-003",
    employeeName: "Trần Đức Nam",
    departmentName: "Quản lý Điểm trường",
    type: "LATE",
    startDate: "2026-07-12 08:00",
    endDate: "2026-07-12 10:00",
    reason: "Đưa con đi tiêm chủng buổi sáng",
    steps: [
      { role: UserRole.EXECUTIVE, approverName: "Lăng Tuấn Anh", status: "APPROVED", notes: "Đã duyệt, sắp xếp công việc họp giao ban cơ sở lùi sang chiều.", updatedAt: "2026-07-10 14:00" }
    ],
    currentStepIndex: 0,
    status: "APPROVED",
    createdAt: "2026-07-10"
  }
];

export const mockStudents: Student[] = [
  {
    id: "STU-001",
    fullName: "Nguyễn Minh Khang",
    birthDate: "2018-05-12",
    avatarUrl: "https://images.unsplash.com/photo-1544717305-2782549b5136?auto=format&fit=crop&w=150&h=150&q=80",
    phone: "0356123456",
    parentName: "Nguyễn Minh Đức",
    parentPhone: "0912123456",
    campusId: "CAMP-01",
    classIds: ["CLS-01"],
    status: "STUDYING",
    history: [
      "2026-05-10: Tư vấn viên Đỗ Gia Bảo chuyển đổi hồ sơ thành công từ Lead.",
      "2026-06-01: Nhập học lớp Junior-Starter-J1A."
    ]
  },
  {
    id: "STU-002",
    fullName: "Trần Mai Chi",
    birthDate: "2017-09-20",
    avatarUrl: "https://images.unsplash.com/photo-1519699047748-de8e457a634e?auto=format&fit=crop&w=150&h=150&q=80",
    phone: "0367234567",
    parentName: "Lê Thị Thu",
    parentPhone: "0923234567",
    campusId: "CAMP-03",
    classIds: ["CLS-02"],
    status: "STUDYING",
    history: [
      "2026-06-25: Nhập học hàng loạt theo file Excel trường liên kết Tiểu học Nghĩa Tân.",
      "2026-07-01: Phân công vào lớp Nghĩa Tân 3A1."
    ]
  },
  {
    id: "STU-003",
    fullName: "Phạm Gia Huy",
    birthDate: "2016-01-15",
    avatarUrl: "https://images.unsplash.com/photo-1503919545889-aef636e10ad4?auto=format&fit=crop&w=150&h=150&q=80",
    phone: "0378345678",
    parentName: "Phạm Gia Long",
    parentPhone: "0934345678",
    campusId: "CAMP-02",
    classIds: ["CLS-03"],
    status: "STUDYING",
    history: [
      "2026-03-10: Đóng học phí và gán vào lớp Communication-C2B.",
      "2026-07-05: Đề xuất bảo lưu học tập từ ngày 15/07 do gia đình đi du lịch nước ngoài dài ngày."
    ]
  },
  {
    id: "STU-004",
    fullName: "Lâm Tuệ Lâm",
    birthDate: "2018-11-30",
    avatarUrl: "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=150&h=150&q=80",
    phone: "0389456789",
    parentName: "Lâm Thế Anh",
    parentPhone: "0945456789",
    campusId: "CAMP-01",
    classIds: ["CLS-01"],
    status: "RESERVED",
    history: [
      "2026-06-15: Chuyển sang trạng thái Bảo lưu do lý do sức khỏe."
    ]
  }
];

export const mockGradeEntries: GradeEntry[] = [
  {
    id: "GRD-001",
    studentId: "STU-001",
    studentName: "Nguyễn Minh Khang",
    classId: "CLS-01",
    className: "Junior-Starter-J1A",
    midtermScore: 8.5,
    finalScore: 9.0,
    averageScore: 8.8,
    batchId: "BATCH-GRD-Jul10",
    status: "APPROVED",
    teacherComments: "Khang nghe rất tốt, tích cực phát biểu trong giờ học.",
    notes: "Đã được phê duyệt bởi Quản lý điểm trường Trần Đức Nam.",
    updatedAt: "2026-07-10"
  },
  {
    id: "GRD-002",
    studentId: "STU-002",
    studentName: "Trần Mai Chi",
    classId: "CLS-02",
    className: "Lớp Liên Kết - Nghĩa Tân 3A1",
    midtermScore: 7.5,
    finalScore: 8.0,
    averageScore: 7.8,
    batchId: "BATCH-GRD-Jul10",
    status: "PENDING",
    teacherComments: "Phát âm tiến bộ vượt bậc, tuy nhiên cần chú ý tập trung chép chính tả hơn.",
    updatedAt: "2026-07-10"
  },
  {
    id: "GRD-003",
    studentId: "STU-003",
    studentName: "Phạm Gia Huy",
    classId: "CLS-03",
    className: "Communication-C2B",
    midtermScore: 9.0,
    finalScore: 8.5,
    averageScore: 8.7,
    status: "DRAFT",
    teacherComments: "Rất xuất sắc, kỹ năng nói phản xạ cực kỳ nhạy bén.",
    updatedAt: "2026-07-09"
  }
];

export const mockCommentEntries: CommentEntry[] = [
  {
    id: "CMT-001",
    studentId: "STU-001",
    studentName: "Nguyễn Minh Khang",
    classId: "CLS-01",
    className: "Junior-Starter-J1A",
    type: "DAILY",
    content: "Con hoàn thành xuất sắc 3 bài nghe trên LMS, đạt điểm tối đa chuỗi ngày học.",
    isWarning: false,
    batchId: "B-CMT-1",
    status: "APPROVED",
    createdAt: "2026-07-10"
  },
  {
    id: "CMT-002",
    studentId: "STU-002",
    studentName: "Trần Mai Chi",
    classId: "CLS-02",
    className: "Lớp Liên Kết - Nghĩa Tân 3A1",
    type: "DAILY",
    content: "Chi hôm nay mệt, vắng học không lý do, chưa nộp bài tập chép chính tả.",
    isWarning: true,
    batchId: "B-CMT-1",
    status: "PENDING",
    createdAt: "2026-07-10"
  }
];

export const mockLectures: Lecture[] = [
  {
    id: "LEC-001",
    title: "Unit 1: Greetings and Introduction",
    description: "Video hướng dẫn chi tiết cách phát âm các nguyên âm cơ bản và hội thoại chào hỏi.",
    fileType: "VIDEO",
    cdnUrl: "https://pps-cdn.vn/lectures/greetings_intro.mp4",
    courseId: "CRS-01",
    views: 124
  },
  {
    id: "LEC-002",
    title: "Tài liệu học tập Unit 1 - Từ vựng & Mẫu câu",
    description: "Tệp tin PDF tổng hợp các từ vựng và mẫu câu chào hỏi thông dụng.",
    fileType: "PDF",
    cdnUrl: "https://pps-cdn.vn/lectures/unit1_vocab.pdf",
    courseId: "CRS-01",
    views: 95
  }
];

export const mockExams: Exam[] = [
  { id: "EXM-001", title: "Bài Kiểm Tra Giữa Kỳ Junior Starter", courseId: "CRS-01", type: "QUIZ", questionsCount: 20, deadline: "2026-07-20" },
  { id: "EXM-002", title: "Luyện phát âm Reflexive Dialogue", courseId: "CRS-04", type: "SPEAKING", questionsCount: 5, deadline: "2026-07-18" }
];

export const mockStudentExamSubmissions: StudentExamSubmission[] = [
  {
    id: "SUB-001",
    examId: "EXM-001",
    examTitle: "Bài Kiểm Tra Giữa Kỳ Junior Starter",
    studentId: "STU-001",
    studentName: "Nguyễn Minh Khang",
    className: "Junior-Starter-J1A",
    score: 9.5,
    submittedAt: "2026-07-09 19:45",
    status: "GRADED",
    teacherFeedback: "Làm bài rất cẩn thận, sai duy nhất 1 câu nghe điền từ.",
    skillsScores: { listening: 10, reading: 9, speaking: 9, writing: 10 }
  },
  {
    id: "SUB-002",
    examId: "EXM-002",
    examTitle: "Luyện phát âm Reflexive Dialogue",
    studentId: "STU-002",
    studentName: "Trần Mai Chi",
    className: "Lớp Liên Kết - Nghĩa Tân 3A1",
    submittedAt: "2026-07-10 15:30",
    status: "GRADING",
    skillsScores: { listening: 8, speaking: 0 } // Speaking needs manual grading
  }
];

export const mockInvoices: Invoice[] = [
  {
    id: "INV-001",
    studentId: "STU-001",
    studentName: "Nguyễn Minh Khang",
    parentName: "Nguyễn Minh Đức",
    amount: 3500000,
    scholarshipApplied: "PPS-SCHOLAR-10",
    discountAmount: 350000,
    finalAmount: 3150000,
    dueDate: "2026-07-15",
    status: "PAID",
    paidAmount: 3150000,
    qrUrl: "https://img.vietqr.io/image/970415-1022345678-compact2.jpg?amount=3150000&addInfo=PPS%20INV001%20MINHKHANG",
    createdAt: "2026-07-01",
    campusId: "CAMP-01"
  },
  {
    id: "INV-002",
    studentId: "STU-003",
    studentName: "Phạm Gia Huy",
    parentName: "Phạm Gia Long",
    amount: 4500000,
    finalAmount: 4500000,
    dueDate: "2026-07-15",
    status: "PENDING",
    paidAmount: 0,
    qrUrl: "https://img.vietqr.io/image/970415-1022345678-compact2.jpg?amount=4500000&addInfo=PPS%20INV002%20GIAHUY",
    createdAt: "2026-07-01",
    campusId: "CAMP-02"
  },
  {
    id: "INV-003",
    studentId: "STU-004",
    studentName: "Lâm Tuệ Lâm",
    parentName: "Lâm Thế Anh",
    amount: 3500000,
    finalAmount: 3500000,
    dueDate: "2026-07-05",
    status: "OVERDUE",
    paidAmount: 0,
    qrUrl: "https://img.vietqr.io/image/970415-1022345678-compact2.jpg?amount=3500000&addInfo=PPS%20INV003%20TUELAM",
    createdAt: "2026-06-20",
    campusId: "CAMP-03"
  }
];

export const mockExpenses: Expense[] = [
  { id: "EXP-001", category: "SALARY", amount: 154000000, description: "Chi trả lương cán bộ giáo viên, nhân viên Tháng 6/2026", createdAt: "2026-07-05" },
  { id: "EXP-002", category: "RENT", amount: 35000000, description: "Chi phí thuê mặt bằng PPS Cầu Giấy", campusId: "CAMP-02", createdAt: "2026-07-01" },
  { id: "EXP-003", category: "CDN_INFRA", amount: 12500000, description: "Hạ tầng CDN và hệ thống LMS video", createdAt: "2026-07-03" }
];

export const mockLeads: Lead[] = [
  {
    id: "LED-001",
    fullName: "Vũ Nhật Minh",
    phone: "0904445556",
    email: "minhvn@gmail.com",
    source: "Facebook Ads",
    status: "QUALIFIED",
    counselorId: "EMP-008",
    counselorName: "Đỗ Gia Bảo",
    history: [
      "2026-07-05: Lead tự động đổ từ Landing Page chiến dịch hè.",
      "2026-07-06: Gia Bảo gọi điện, mẹ bé quan tâm chương trình Active Kids."
    ],
    createdAt: "2026-07-05"
  },
  {
    id: "LED-002",
    fullName: "Hoàng Lê Nam",
    phone: "0915556667",
    email: "namhl@gmail.com",
    source: "Website",
    status: "NEW",
    history: [
      "2026-07-09: Phụ huynh đăng ký tư vấn kiểm tra trình độ Tiếng Anh miễn phí."
    ],
    createdAt: "2026-07-09"
  },
  {
    id: "LED-003",
    fullName: "Bùi Khánh An",
    phone: "0926667778",
    email: "anbk@gmail.com",
    source: "Marketing Trực tiếp",
    status: "WON",
    counselorId: "EMP-008",
    counselorName: "Đỗ Gia Bảo",
    history: [
      "2026-07-01: Gặp trực tiếp tại sự kiện khai giảng trường liên kết.",
      "2026-07-08: Phụ huynh đồng ý nhập học, đóng học phí đợt 1 và chuyển hồ sơ chính thức."
    ],
    createdAt: "2026-07-01"
  }
];

export const mockClassroomRooms: ClassroomRoom[] = [
  { id: "RM-101", campusId: "CAMP-01", roomName: "Phòng 201 (Lý thuyết)", capacity: 25, type: "THEORY", isFlexible: false, status: "AVAILABLE" },
  { id: "RM-102", campusId: "CAMP-01", roomName: "Phòng 202 (Lý thuyết)", capacity: 25, type: "THEORY", isFlexible: false, status: "OCCUPIED" },
  { id: "RM-103", campusId: "CAMP-02", roomName: "Phòng Máy tính Cầu Giấy", capacity: 20, type: "COMPUTER", isFlexible: false, status: "AVAILABLE" },
  { id: "RM-104", campusId: "CAMP-03", roomName: "Phòng 102 (Linh hoạt)", capacity: 40, type: "THEORY", isFlexible: true, status: "AVAILABLE" }
];

export const mockFeedbackTickets: FeedbackTicket[] = [
  {
    id: "TKT-001",
    campusId: "CAMP-03",
    campusName: "Trường Tiểu học Nghĩa Tân (Liên kết)",
    senderName: "Cô Hiệu Trưởng - Nguyễn Thu Hương",
    category: "TEACHER",
    content: "Đề nghị kiểm tra lại giờ giấc lên lớp của giáo viên bản xứ, hôm Thứ Ba vừa rồi thầy Michael Smith tới lớp trễ 5 phút.",
    priority: "HIGH",
    status: "IN_PROGRESS",
    createdAt: "2026-07-08",
    history: [
      { text: "Tiếp nhận thông tin phản hồi từ Đại diện trường Nghĩa Tân.", time: "2026-07-08 11:00", author: "Trần Đức Nam" },
      { text: "Đã làm việc với giáo viên Michael Smith, giáo viên giải trình do tắc đường kẹt xe và cam kết đi sớm hơn từ tuần sau.", time: "2026-07-09 14:00", author: "Trần Đức Nam" }
    ]
  },
  {
    id: "TKT-002",
    campusId: "CAMP-04",
    campusName: "Trường THCS Lê Quý Đôn (Liên kết)",
    senderName: "Thầy Hiệu Phó - Phạm Xuân Tấn",
    category: "CLASSROOM",
    content: "Hệ thống loa ở phòng học đa năng đôi lúc chập chờn, nhờ kỹ thuật trung tâm kiểm tra lại trước buổi học Thứ Sáu.",
    priority: "MEDIUM",
    status: "RESOLVED",
    resolutionText: "Đã cử nhân sự hành chính mang loa dự phòng tới lắp đặt thay thế loa lỗi.",
    createdAt: "2026-07-09",
    history: [
      { text: "Tiếp nhận phản hồi kỹ thuật loa phòng học.", time: "2026-07-09 09:00", author: "Nguyễn Thị Mai" },
      { text: "Hoàn tất thay thế loa và nghiệm thu chất lượng âm thanh tốt.", time: "2026-07-09 16:00", author: "Nguyễn Thị Mai" }
    ]
  }
];

export const mockSyllabusPlans: SyllabusPlan[] = [
  {
    id: "PLN-001",
    classId: "CLS-02",
    className: "Lớp Liên Kết - Nghĩa Tân 3A1",
    type: "WEEKLY",
    title: "Kế hoạch tuần 2 tháng 7 (13/07 - 19/07)",
    content: "Luyện phát âm Reflexive Dialogue, thực hiện 2 bài nghe trên LMS. Tổ chức trò chơi từ vựng Flashcard.",
    authorName: "Lê Thu Hà",
    updatedAt: "2026-07-10"
  },
  {
    id: "PLN-002",
    classId: "CLS-01",
    className: "Junior-Starter-J1A",
    type: "YEARLY",
    title: "Phác đồ kế hoạch năm học 2026-2027",
    content: "Kế hoạch đào tạo gồm 4 học phần chính, chuẩn đầu ra Cambridge Starter, thi đánh giá định kỳ 3 tháng/lần.",
    authorName: "Lê Thu Hà",
    updatedAt: "2026-07-10"
  }
];

export const mockPermissions: Permission[] = [
  // Authentication & System
  { id: "P-01", code: "system.admin", name: "Toàn quyền quản trị hệ thống", module: "SYS", description: "Được làm tất cả thao tác", apiEndpoints: ["GET /api/sys/users", "POST /api/sys/users", "PUT /api/sys/users/:id", "DELETE /api/sys/users/:id", "POST /api/sys/permissions/override", "GET /api/sys/audit-logs"] },
  { id: "P-02", code: "user.manage", name: "Quản lý người dùng", module: "SYS", description: "Tạo, sửa, khóa tài khoản", apiEndpoints: ["GET /api/sys/users", "POST /api/sys/users", "PUT /api/sys/users/:id", "DELETE /api/sys/users/:id"] },
  { id: "P-03", code: "permission.edit", name: "Cấu hình quyền", module: "SYS", description: "Gán quyền, override quyền cho user", apiEndpoints: ["POST /api/sys/permissions/override", "GET /api/sys/audit-logs"] },
  // Task
  { id: "P-04", code: "task.create", name: "Tạo nhiệm vụ & giao việc", module: "TASK", description: "Phân công công việc cho cấp dưới", apiEndpoints: ["POST /api/tasks", "PUT /api/tasks/:id/status", "POST /api/tasks/:id/approve"] },
  { id: "P-05", code: "task.view_all", name: "Xem toàn bộ công việc", module: "TASK", description: "Bao quát tiến độ các phòng ban", apiEndpoints: ["GET /api/tasks"] },
  // HRM
  { id: "P-06", code: "hrm.manage", name: "Quản trị nhân sự", module: "HRM", description: "Quản lý hợp đồng, hồ sơ cán bộ giáo viên", apiEndpoints: ["GET /api/hrm/employees", "POST /api/hrm/employees", "PUT /api/hrm/employees/:id"] },
  { id: "P-07", code: "hrm.approve_leave", name: "Duyệt đơn từ ngoại tuyến", module: "HRM", description: "Duyệt nghỉ phép, nghỉ ốm, đi muộn", apiEndpoints: ["GET /api/hrm/leaves", "POST /api/hrm/leaves/approve"] },
  { id: "P-08", code: "hrm.payroll", name: "Xem và tính toán lương", module: "HRM", description: "Tổng hợp công và tính toán lương", apiEndpoints: ["GET /api/hrm/payroll", "POST /api/hrm/payroll/calculate"] },
  // CRM
  { id: "P-09", code: "crm.manage", name: "Quản lý Leads tuyển sinh", module: "CRM", description: "Chăm sóc data phụ huynh, chia lead, tư vấn", apiEndpoints: ["GET /api/crm/leads", "POST /api/crm/leads"] },
  { id: "P-10", code: "crm.convert_student", name: "Chuyển đổi lead thành học viên", module: "CRM", description: "Tự động sinh hồ sơ học sinh khi nộp phí", apiEndpoints: ["POST /api/crm/leads/convert"] },
  // Student
  { id: "P-11", code: "student.manage", name: "Quản lý hồ sơ học sinh", module: "STUDENT", description: "Tạo mới, sửa đổi hồ sơ lý lịch điện tử", apiEndpoints: ["GET /api/students", "POST /api/students", "PUT /api/students/:id"] },
  { id: "P-12", code: "student.attendance", name: "Điểm danh học sinh", module: "STUDENT", description: "Ghi nhận chuyên cần chuyên môn đầu tiết", apiEndpoints: ["POST /api/students/attendance", "POST /api/students/attendance/sms"] },
  // Academic
  { id: "P-13", code: "academic.curriculum", name: "Cấu hình khung chương trình", module: "ACAD", description: "Thiết lập cấu trúc điểm, đề xuất khung", apiEndpoints: ["GET /api/academic/syllabus", "POST /api/academic/syllabus"] },
  { id: "P-14", code: "academic.grade_entry", name: "Nhập điểm học phần", module: "ACAD", description: "Nhập điểm thành phần học sinh", apiEndpoints: ["POST /api/academic/grades/entry"] },
  { id: "P-15", code: "academic.grade_approve", name: "Phê duyệt sổ điểm", module: "ACAD", description: "Duyệt điểm công khai hoặc từ chối", apiEndpoints: ["POST /api/academic/grades/approve"] },
  { id: "P-16", code: "academic.comment_write", name: "Viết nhận xét định kỳ", module: "ACAD", description: "Ghi nhận xét Daily/Mid/Final", apiEndpoints: ["POST /api/academic/comments/entry"] },
  { id: "P-17", code: "academic.comment_approve", name: "Duyệt nhận xét", module: "ACAD", description: "Duyệt hiển thị Portal Phụ huynh", apiEndpoints: ["POST /api/academic/comments/approve"] },
  // LMS
  { id: "P-18", code: "lms.manage", name: "Quản lý kho bài giảng LMS", module: "LMS", description: "Tải học liệu lên CDN", apiEndpoints: ["POST /api/lms/lectures/upload"] },
  { id: "P-19", code: "lms.exam_grade", name: "Chấm thi tự luận/Nói", module: "LMS", description: "Chấm tay bài nói ghi âm, bài tự luận", apiEndpoints: ["POST /api/lms/exams/grade"] },
  // Finance
  { id: "P-20", code: "finance.billing", name: "Quản lý học phí", module: "FIN", description: "Xuất hóa đơn, quét QR gạch nợ tự động", apiEndpoints: ["GET /api/finance/invoices", "POST /api/finance/invoices/generate"] },
  { id: "P-21", code: "finance.expenses", name: "Ghi nhận chi phí vận hành", module: "FIN", description: "Khai báo tiền mặt chi lương, mặt bằng", apiEndpoints: ["GET /api/finance/expenses"] },
  { id: "P-22", code: "finance.report_all", name: "Xem báo cáo tài chính tổng", module: "FIN", description: "Biểu đồ thu chi toàn chuỗi doanh nghiệp", apiEndpoints: ["GET /api/finance/reports/cashflow"] },
  // Facility
  { id: "P-23", code: "facility.manage", name: "Quản lý điểm trường & CSVC", module: "FACILITY", description: "Xếp phòng, theo dõi hợp đồng liên kết", apiEndpoints: ["GET /api/facility/campuses", "POST /api/facility/rooms/schedule"] },
  { id: "P-24", code: "facility.feedback_resolve", name: "Xử lý phản hồi đối tác", module: "FACILITY", description: "Tiếp nhận kiến nghị từ trường liên kết", apiEndpoints: ["POST /api/facility/feedback/resolve"] }
];

/**
 * Khớp ĐÚNG bảng role_permissions thật của backend (truy vấn trực tiếp từ DB
 * — không suy diễn). Dùng 22 permission code thật (xem API.md), KHÔNG dùng
 * mã bịa của bản Google AI Studio nữa. STUDENT/PARENT không có quyền business
 * nào ở app Admin — 2 role này thuộc app "user" riêng (Portal HS/PH).
 */
export const mockRolePermissions: RolePermission[] = [
  {
    role: UserRole.SYS_ADMIN,
    permissions: [
      "facility.manage", "permission.audit.view", "permission.catalog.manage",
      "permission.override.manage", "permission.role.manage", "user.manage", "user.role.manage"
    ]
  },
  {
    role: UserRole.EXECUTIVE,
    permissions: ["finance.report.view", "task.create"]
  },
  {
    role: UserRole.HEAD_ACADEMIC,
    permissions: [
      "academic.class.manage", "academic.curriculum.manage", "academic.grade.manage",
      "facility.room.manage", "task.create"
    ]
  },
  {
    role: UserRole.SITE_MANAGER,
    permissions: ["crm.lead.assign", "student.manage", "student.status.manage", "task.create"]
  },
  {
    role: UserRole.HR_MANAGER,
    permissions: ["hrm.manage", "task.create"]
  },
  {
    role: UserRole.STAFF,
    permissions: [
      "academic.class.manage", "crm.lead.assign", "crm.lead.manage",
      "facility.room.manage", "finance.manage", "student.manage", "student.status.manage"
    ]
  },
  {
    role: UserRole.TEACHER,
    permissions: ["lms.exercise.manage", "lms.grading.manage"]
  },
  {
    role: UserRole.OPS_MANAGER,
    permissions: ["facility.manage", "task.create"]
  },
  {
    role: UserRole.PARTNER_REP,
    permissions: []
  },
  {
    role: UserRole.PARENT,
    permissions: []
  },
  {
    role: UserRole.STUDENT,
    permissions: []
  }
];

