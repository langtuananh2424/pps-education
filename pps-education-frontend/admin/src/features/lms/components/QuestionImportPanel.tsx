import React, { useRef, useState } from "react";
import { ChevronDown, Download, UploadCloud } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { buildXlsxMultiSheetTemplateBlob, buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import {
  ExamTeacherType,
  ExerciseSkillCategory,
  QuestionImportResponse,
  QuestionImportRowPreview,
  QuestionImportedRow,
  downloadExamQuestionImportWordTemplate,
  downloadQuestionImportWordTemplate,
  importExamQuestions,
  importQuestions,
  previewExamQuestionImport,
  previewQuestionImport
} from "../api";
import Button from "@/components/ui/Button";
import FloatingError from "@/components/ui/FloatingError";

type FileFormat = "xlsx" | "docx";

interface QuestionImportPanelProps {
  bankId?: number;
  examId?: number;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — khi truyền kèm (luồng "Soạn Bài
   * mới" theo Đề), lọc mẫu Excel + tải mẫu Word chỉ còn loại khớp Nhóm kỹ năng đã chọn ở bước 1
   * (skillCategoryKinds.ts phía FE, SKILL_CATEGORY_KIND_TOKENS phía backend). Bỏ trống (dùng ở trang
   * Ngân hàng câu hỏi độc lập, không có ngữ cảnh Bài) = hiện đủ tất cả như trước.
   */
  skillCategory?: ExerciseSkillCategory;
  teacherType?: ExamTeacherType;
  onImported: (createdQuestions: QuestionImportedRow[]) => void;
}

/** Mirror SKILL_CATEGORY_KIND_TOKENS ở QuestionImportService.java (backend) — nguồn chân lý DUY NHẤT
 * cho lọc file mẫu Excel phía FE, KHÔNG tự thêm/bớt token khác backend.
 * Bổ sung 2026-09-08 (đã xác nhận với người dùng) — READING mở khóa import (DOC_HIEU_LUOI/DOC_DIEN_TU,
 * mirror GridQuestionBuilder.tsx/ClozeQuestionBuilder.tsx), trước đó không có entry vì Cloze/Grid từng
 * chỉ là composite builder chưa import được.
 * Fix gap thật (bổ sung 2026-10-05, đã xác nhận với người dùng) — "DIEN_TU_DOAN_VAN" (backend hỗ trợ từ
 * 2026-09-17, xem QuestionImportService.SKILL_CATEGORY_KIND_TOKENS.VOCAB_GRAMMAR) bị bỏ sót hoàn toàn ở
 * mirror này — GV gõ tay đúng token vẫn import được (backend validate độc lập), nhưng không chọn được
 * qua dropdown "Loại câu hỏi mặc định"/không có ví dụ trong file mẫu tải về. Thêm vào đây. */
const SKILL_CATEGORY_KIND_TOKENS: Record<string, string[]> = {
  VOCAB_GRAMMAR: [
    "TRAC_NGHIEM", "TRAC_NGHIEM_VOICE", "DIEN_TU", "DIEN_TU_NHOM", "DIEN_TU_HOP_TU_VUNG",
    "DIEN_TU_HOP_TU_VUNG_ANH", "DIEN_TU_DOAN_VAN", "SAP_XEP_CAU", "SAP_XEP_CHU_CAI"
  ],
  WRITING: ["TU_LUAN"],
  LISTENING: ["TRAC_NGHIEM_VOICE", "NGHE_NHIEU_DAP_AN", "NGHE_NOP_AUDIO", "NGHE_DIEN_TU", "NGHE_CHON_HINH", "NGHE_PHIEU_THONG_TIN"],
  // Bổ sung 2026-10-17 (đã xác nhận với người dùng) — DOC_HIEU_NOI_DAP_AN (đọc hiểu — nối đáp án, tối
  // đa 8 lựa chọn dùng chung), mirror QuestionImportService.SKILL_CATEGORY_KIND_TOKENS.READING.
  READING: ["DOC_HIEU_LUOI", "DOC_HIEU_NOI_DAP_AN", "DOC_DIEN_TU"]
};

/** Mirror VALID_KINDS ở QuestionImportService.java (backend) — nguồn cho dropdown "Loại câu hỏi mặc định". */
const ALL_KIND_TOKENS = [
  "TRAC_NGHIEM", "TRAC_NGHIEM_VOICE", "DIEN_TU", "DIEN_TU_NHOM", "TU_LUAN", "SPEAKING",
  "DIEN_TU_HOP_TU_VUNG", "DIEN_TU_HOP_TU_VUNG_ANH", "DIEN_TU_DOAN_VAN", "SAP_XEP_CAU", "SAP_XEP_CHU_CAI",
  "NGHE_NOP_AUDIO", "NGHE_DIEN_TU", "NGHE_CHON_HINH", "NGHE_PHIEU_THONG_TIN", "NGHE_NHIEU_DAP_AN", "DOC_HIEU_LUOI", "DOC_HIEU_NOI_DAP_AN", "DOC_DIEN_TU"
];

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — gom 17 token thành 7 nhóm cha, mirror
 * tên nhóm (kindGroup.*) đã dùng ở cây "Loại cha → Kiểu con" của form soạn tay (QuestionEditorForm.tsx),
 * cộng 1 nhóm "reading" riêng cho 2 loại composite chỉ có ở Excel (không có kind đơn tương ứng ở form
 * soạn tay). Dùng cho accordion chọn "Loại câu hỏi mặc định" VÀ để nhóm sheet khi sinh file mẫu nhiều
 * sheet (xem handleDownloadTemplate) — 1 nguồn duy nhất cho cả 2 chỗ, tránh lệch nhau.
 */
const IMPORT_KIND_GROUPS: { labelKey: string; tokens: string[] }[] = [
  { labelKey: "kindGroup.multipleChoice", tokens: ["TRAC_NGHIEM", "TRAC_NGHIEM_VOICE", "NGHE_NHIEU_DAP_AN"] },
  { labelKey: "kindGroup.fillInBlank", tokens: ["DIEN_TU", "NGHE_DIEN_TU", "DIEN_TU_NHOM"] },
  { labelKey: "kindGroup.wordBank", tokens: ["DIEN_TU_HOP_TU_VUNG", "DIEN_TU_HOP_TU_VUNG_ANH", "DIEN_TU_DOAN_VAN", "NGHE_PHIEU_THONG_TIN"] },
  { labelKey: "kindGroup.sentenceBuilding", tokens: ["SAP_XEP_CAU", "SAP_XEP_CHU_CAI"] },
  { labelKey: "kindGroup.essay", tokens: ["TU_LUAN"] },
  { labelKey: "kindGroup.speaking", tokens: ["SPEAKING", "NGHE_NOP_AUDIO", "NGHE_CHON_HINH"] },
  { labelKey: "kindGroup.reading", tokens: ["DOC_HIEU_LUOI", "DOC_HIEU_NOI_DAP_AN", "DOC_DIEN_TU"] }
];

/**
 * Bổ sung 2026-10-19 (đã xác nhận với người dùng — sửa trực tiếp bảng xem trước) — ép 1
 * QuestionImportRowPreview (nguyên 21 cột thô từ backend) thành 1 mảng ô ĐÚNG THỨ TỰ khớp
 * `questionImportPanel.excelHeaders` (mirror comment "Thứ tự mới mỗi dòng: ..." ở `sampleRows` dưới
 * đây) — dùng để khởi tạo lưới sửa tay VÀ dựng lại file .xlsx (buildXlsxTemplateBlob) khi bấm "Kiểm
 * tra lại".
 */
function rowPreviewToCells(row: QuestionImportRowPreview): string[] {
  return [
    row.kind ?? "", row.difficulty ?? "", row.content ?? "",
    row.choiceA ?? "", row.choiceB ?? "", row.choiceC ?? "", row.choiceD ?? "",
    row.choiceE ?? "", row.choiceF ?? "", row.choiceG ?? "", row.choiceH ?? "", row.choiceI ?? "", row.choiceJ ?? "",
    row.correctAnswer ?? "", row.audioUrl ?? "", row.imageUrl ?? "", row.referencePassage ?? "",
    row.defaultPoints ?? "", row.explanation ?? "", row.tags ?? "", row.instruction ?? ""
  ];
}

/**
 * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * soạn đề nhanh — panel dùng chung cho cả trang "Ngân hàng câu hỏi" (import
 * độc lập vào 1 bank) lẫn bước "Soạn đề" trong "Soạn & giao đề" (import rồi
 * tự gắn luôn vào đề đang soạn — xem CreateAndAssignExerciseModal.tsx).
 * KHÔNG dùng chung components/ui/ImportExcelButton.tsx vì cái đó cứng 1
 * định dạng .xlsx và đang dùng ở 7 nơi khác — hỗ trợ thêm .docx ở đây sẽ
 * phải sửa hành vi chung không cần thiết cho các nơi kia.
 */
export default function QuestionImportPanel({ bankId, examId, skillCategory, teacherType, onImported }: QuestionImportPanelProps) {
  const { t } = useTranslation("lms-question-authoring");
  const [format, setFormat] = useState<FileFormat>("xlsx");
  const inputRef = useRef<HTMLInputElement>(null);
  const [previewing, setPreviewing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<QuestionImportResponse | null>(null);
  // Bổ sung 2026-10-19 (đã xác nhận với người dùng — preview trước khi lưu) — file đang chờ xác nhận,
  // giữ lại để handleConfirm gửi LẠI đúng file này lên endpoint ghi DB thật khi GV bấm "Xác nhận lưu".
  // Đơn giản hơn cache parsed rows ở backend (không cần job tạm/token hết hạn) — xem QuestionImportService#previewImport.
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  // Bổ sung 2026-10-19 (đã xác nhận với người dùng — sửa trực tiếp bảng xem trước, thay cho việc
  // phải xuống Excel sửa rồi upload lại) — luôn khớp 1:1 với result.rowPreviews (cùng khởi tạo lại
  // trong runPreview mỗi lần preview/kiểm tra lại thành công), chỉ khác là mảng NÀY sửa được qua
  // input, còn result.rowPreviews là dữ liệu "đã validate" lần gần nhất (dùng để tô đỏ dòng lỗi).
  const [editedRows, setEditedRows] = useState<string[][]>([]);
  const isPreview = result?.status === "PREVIEW";
  // Bổ sung 2026-08-28 (đã xác nhận với người dùng) — "Loại câu hỏi mặc định": khớp thói quen thật
  // "1 Ex chỉ 1 loại câu hỏi" nên cả file thường cùng 1 giá trị — chọn 1 lần ở đây thay vì gõ lại cột
  // "Loại câu hỏi" ở MỌI dòng trong file. Rỗng ("") = giữ hành vi cũ (bắt buộc mỗi dòng tự ghi loại).
  const [defaultKind, setDefaultKind] = useState("");
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — accordion "Loại cha → Kiểu con" cho
  // dropdown "Loại câu hỏi mặc định" (mirror pattern expandedGroupKey ở QuestionEditorForm.tsx), thu
  // gọn hết khi mở panel. Tối đa 1 nhóm mở cùng lúc.
  const [expandedGroupKey, setExpandedGroupKey] = useState<string | null>(null);
  const allowedKindTokens = teacherType === "FOREIGN" ? SKILL_CATEGORY_KIND_TOKENS.LISTENING : skillCategory ? SKILL_CATEGORY_KIND_TOKENS[skillCategory] : undefined;
  const defaultKindOptions = allowedKindTokens ?? ALL_KIND_TOKENS;

  // Cột cố định của mẫu Excel — ExcelQuestionRowParser.java đọc theo TÊN header (dòng 1), không theo vị
  // trí cột, nên thứ tự/ngôn ngữ header đổi được miễn còn khớp alias (xem QuestionImportFieldAliases.java)
  // — mảng dưới đây chỉ cần khớp ĐÚNG THỨ TỰ với mảng `headers`. 17 dòng ví dụ demo đủ 17 loại
  // VALID_KINDS backend hỗ trợ (bổ sung 2026-08-26: NGHE_NOP_AUDIO/NGHE_DIEN_TU cho GV nước ngoài; bổ
  // sung 2026-08-28: DIEN_TU_NHOM — "Cách B", 1 dòng tạo N Question riêng cùng groupKey, mirror
  // FillInBlankGroupBuilder.tsx; bổ sung 2026-09-08: DOC_HIEU_LUOI/DOC_DIEN_TU cho READING; bổ sung
  // 2026-09-09: NGHE_CHON_HINH — mirror VOICE_PICTURE_CHOICE ở ListeningGroupBuilder.tsx, ảnh theo từng
  // đáp án qua "URL Hình ảnh" phân tách "|"; bổ sung 2026-10-05: DIEN_TU_DOAN_VAN — fix gap thật, trước
  // đây thiếu hoàn toàn dù backend hỗ trợ từ 2026-09-17), y hệt nội dung buildTemplateBlocks() bên
  // backend (QuestionImportService.java) để 2 định dạng nhất quán. Hoist ra scope component (thay vì
  // local trong handleDownloadTemplate như trước) để dùng chung cho cả preview inline trong panel.
  // Bổ sung 2026-10-17 (đã xác nhận với người dùng — LẦN 2 cùng ngày, sau khi GV mở file mẫu thật thấy
  // "Đáp án E/F/G/H" nằm tít cuối bảng, cách xa "Đáp án A-D" bởi 7 cột khác) — 4 cột "Đáp án E/F/G/H"
  // ĐẶT NGAY SAU "Đáp án D" (trước "Đáp án đúng"), gộp chung cụm 8 cột đáp án A-H liền nhau cho dễ điền
  // tay — KHÔNG đặt cuối bảng như lần đầu nữa. An toàn vì ExcelQuestionRowParser đọc theo TÊN header
  // (không theo vị trí cột, xem QuestionImportFieldAliases) nên đổi vị trí không ảnh hưởng parse — chỉ
  // cần excelHeaders (vi/en lms-question-authoring.json) VÀ mọi dòng `sampleRows` dưới đây cùng đổi vị
  // trí nhất quán. Thứ tự mới mỗi dòng: kind, difficulty, content, A, B, C, D, E, F, G, H, correctAnswer,
  // audioUrl, imageUrl, referencePassage, points, explanation, tags, instruction.
  const sampleRows: string[][] = [
    ["TRAC_NGHIEM", "EASY", "What is the capital of France?", "London", "Paris", "Berlin", "Madrid", "", "", "", "", "", "",
      "B", "", "", "", "1", t("questionImportPanel.excelSampleExplanations.geo"), "geo,easy", ""],
    ["TRAC_NGHIEM_VOICE", "MEDIUM", "Listen and choose the word you hear.", "ship", "sheep", "chip", "cheap", "", "", "", "", "", "",
      "B", "https://example-r2.dev/lms/questions/audio/mau.mp3", "", "sheep", "1", "", "", ""],
    ["DIEN_TU", "", "She ___ (go) to school every day.", "", "", "", "", "", "", "", "", "", "",
      "goes", "", "", "", "1", t("questionImportPanel.excelSampleExplanations.presentSimple"), "", ""],
    ["DIEN_TU_NHOM", "", "Tom is very ___.|English is my ___ subject.|Our football ___ helps us win the game.", "", "", "", "", "", "", "", "", "", "",
      "smart|favourite|coach", "", "",
      "activity, advanced, beginner, classmate, smart, coach, competition, course, favourite, geography, history, practice", "1",
      t("questionImportPanel.excelSampleExplanations.fillInBlankGroup"), "", ""],
    // Cột cuối "Hướng dẫn" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-14) — dòng chỉ dẫn
    // TÙY CHỌN hiện phía trên đề (chữ nghiêng, mirror "Ex. 1: ..."/"B. ..." trong đề giấy gốc) — để trống
    // thì không hiện, không ảnh hưởng gì (xem QuestionImportService#instructionText).
    ["DIEN_TU_DOAN_VAN", "",
      "In recent years, tourism has become a (1) ___ industry. Many travellers now book a (2) ___ that includes both transport and accommodation.",
      "", "", "", "", "", "", "", "", "", "",
      "growing|package deal", "", "", "growing, package deal, accommodation, destination, resort", "1", "",
      "", "Ex. 1: Fill in each blank (1-2) with the most appropriate word or phrase."],
    ["TU_LUAN", "HARD", "Write a 150-word essay about your favorite hobby.", "", "", "", "", "", "", "", "", "", "",
      "", "", "https://example-r2.dev/lms/questions/images/mau.png", "", "2", t("questionImportPanel.excelSampleExplanations.essayRubric"), "", ""],
    ["SPEAKING", "", "Read the following sentence aloud.", "", "", "", "", "", "", "", "", "", "",
      "", "", "", "enthusiasm, literature, variety", "1", "", "", ""],
    ["DIEN_TU_HOP_TU_VUNG", "", "She ___ to school every day. He ___ football on Sundays.", "", "", "", "", "", "", "", "", "", "",
      "goes|plays", "", "", "", "1", t("questionImportPanel.excelSampleExplanations.wordBank"), "", ""],
    ["DIEN_TU_HOP_TU_VUNG_ANH", "", "1. The cat is ___ the bed. 2. The ball is ___ the box.", "", "", "", "", "", "", "", "", "", "",
      "under|next to", "", "https://example-r2.dev/lms/questions/images/mau-phong.png", "under, next to, behind, in front of, on", "1",
      t("questionImportPanel.excelSampleExplanations.wordBankPicture"), "", ""],
    ["SAP_XEP_CAU", "", "Sắp xếp thành câu hoàn chỉnh.", "", "", "", "", "", "", "", "", "", "",
      "This|is|a|pen", "", "", "", "1", t("questionImportPanel.excelSampleExplanations.sentenceBuilding"), "", ""],
    ["SAP_XEP_CHU_CAI", "", "Sắp xếp chữ cái thành từ đúng (nghĩa: nụ cười).", "", "", "", "", "", "", "", "", "", "",
      "s|m|i|l|e", "", "https://example-r2.dev/lms/questions/images/mau-smile.png", "", "1",
      t("questionImportPanel.excelSampleExplanations.letterScramble"), "", ""],
    ["NGHE_NOP_AUDIO", "", "Listen to the audio and record your answer.", "", "", "", "", "", "", "", "", "", "",
      "", "https://example-r2.dev/lms/questions/audio/mau-nghe.mp3", "", "", "1",
      t("questionImportPanel.excelSampleExplanations.listeningAudioSubmission"), "", ""],
    ["NGHE_DIEN_TU", "", "Listen and fill in the blank: She usually ___ to work.", "", "", "", "", "", "", "", "", "", "",
      "drives", "https://example-r2.dev/lms/questions/audio/mau-nghe-dien-tu.mp3", "", "", "1",
      t("questionImportPanel.excelSampleExplanations.listeningFillInBlank"), "", ""],
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-06 (qua ảnh chụp đề giấy thật GV gửi) —
    // nội dung mẫu dựng lại gần sát đề thật: dòng đầu "The Sea Lady" đứng riêng (cách phần sau bằng 1
    // dòng trống) → tự thành TIÊU ĐỀ in đậm căn giữa; "**Main Facts:**"/"**Dinner:**" dùng cú pháp đậm
    // ** ** → tự in đậm làm tiêu đề mục con; toàn bộ xuống dòng còn lại GIỮ NGUYÊN (formMode bật
    // whitespace-pre-line) — khớp ĐÚNG cơ chế WordBankPreview đã có, chỉ là trước đây sample quá đơn
    // giản nên không lộ ra được hết khả năng này.
    ["NGHE_PHIEU_THONG_TIN", "", "The Sea Lady\n\n**Main Facts:**\nBuilt in: (14) ___\nFirst tour: The Mediterranean Sea\n\n**Dinner:**\nTime: (15) ___ p.m. to 9.30 p.m.", "", "", "", "", "", "", "", "", "", "",
      "1999/nineteen ninety-nine|7:30/7.30/7h30",
      "https://example-r2.dev/lms/questions/audio/mau-phieu-thong-tin.mp3", "", "Write one or two words or a number or a date or a time.", "1",
      t("questionImportPanel.excelSampleExplanations.listeningFormCompletion"), "", ""],
    ["NGHE_CHON_HINH", "", "What time is it?", "", "", "", "", "", "", "", "", "", "",
      "B",
      "https://example-r2.dev/lms/questions/audio/mau-nghe-chon-hinh.mp3",
      "https://example-r2.dev/lms/questions/images/dong-ho-a.png|https://example-r2.dev/lms/questions/images/dong-ho-b.png|https://example-r2.dev/lms/questions/images/dong-ho-c.png",
      "", "1", t("questionImportPanel.excelSampleExplanations.listeningPictureChoice"), "", ""],
    ["NGHE_NHIEU_DAP_AN", "", "Which TWO reasons does Irina give for visiting the exhibition?", "to meet a friend", "to improve her knowledge", "to buy something", "to entertain her child", "B|D",
      "https://example-r2.dev/lms/questions/audio/mau-nghe-nhieu-dap-an.mp3", "", "", "2", t("questionImportPanel.excelSampleExplanations.listeningMultipleAnswer"), "", ""],
    ["DOC_HIEU_LUOI", "", "Who loves a subject because of visiting museums?|Who says friends think their hobby is strange?|Who talks about both a school subject and a sport?",
      "Tom", "Max", "Anna", "", "", "", "", "", "", "",
      "B|A|C", "", "", "Tom: I go to a big school in London...\n\nMax: I live in New York...\n\nAnna: I am a new student in Paris...", "1",
      t("questionImportPanel.excelSampleExplanations.gridReading"), "", ""],
    // Bổ sung 2026-10-17 (đã xác nhận với người dùng, chỉnh lại 4 LẦN CÙNG NGÀY sau khi GV test thật:
    // lần 1 đổi nội dung ví dụ cho khớp đúng sách (5 người Liam/Sophia/Noah/Emma/Oliver ghép 8 cơ sở vật
    // chất); lần 2 fix lỗi hiển thị thật — "Nội dung" KHÔNG tự đánh số thủ công (hệ thống đã tự đánh số,
    // tự làm sẽ trùng "1. 1. Liam"), dồn CẢ 2 đoạn (người + lựa chọn) vào "Đoạn văn tham chiếu" dùng
    // chung; lần 3 đổi vị trí cột Đáp án E-H lên ngay sau D; lần 4 nới trần 8→10 (A-J) sau khi phát hiện
    // 1 bài ngữ pháp nối câu IELTS thực tế dùng tới J — ví dụ mẫu vẫn giữ 8 lựa chọn A-H cho ngắn gọn).
    ["DOC_HIEU_NOI_DAP_AN", "",
      "Liam ___|Sophia ___|Noah ___|Emma ___|Oliver ___",
      "The Main Library", "The Chemistry Laboratory", "The School Canteen", "The Modern Gym",
      "The Computer Room", "The Eco-Garden", "The Main Playground", "The School Hall", "", "",
      "E|B|D|A|F", "", "",
      "THE PEOPLE\n\nLiam: wants a quiet place with comfortable chairs to study in, needs a computer for online resources and a safe locker.\n\nSophia: wants to practice science experiments after class, needs state-of-the-art equipment and must wear her school uniform.\n\nNoah: wants to play indoor basketball, needs wheelchair accessibility and a vending machine for cold refreshments.\n\nEmma: wants to read storybooks in a peaceful space with a bright lamp and a large desk, wants to borrow physical books.\n\nOliver: wants to relax outdoors near nature, prefers a quiet space with a wooden fence, cannot climb stairs.\n\nSCHOOL FACILITIES\n\nA. The Main Library: large wooden desks, private reading lamp, digital catalog, ground floor next to the entrance gate.\n\nB. The Chemistry Laboratory: state-of-the-art equipment, sinks for washing hands, school uniform required.\n\nC. The School Canteen: hot meals, fresh fruits, sweet treats, center of campus, no computers.\n\nD. The Modern Gym: basketball courts, wheelchair ramps, vending machine, outdoor shoes prohibited.\n\nE. The Computer Room: second floor, new computers, high-speed internet, secure metal lockers.\n\nF. The Eco-Garden: outdoor, no stairs, wooden fence, quiet, no food or sports allowed.\n\nG. The Main Playground: large open yard, metal fence, basic sports equipment, hot in the afternoon.\n\nH. The School Hall: graduation ceremonies, giant projector board, only open for special events.",
      "1", t("questionImportPanel.excelSampleExplanations.matchingGroup"), "", ""],
    ["DOC_DIEN_TU", "", "",
      "environment|timetable|hours", "equipment|subject|lessons", "job|homework|subjects", "", "", "", "", "", "", "",
      "B|A|C", "", "", "The school has an excellent (1)___ with many computers. Every Monday, students check their new (2)___. Most students prefer creative (3)___ like art and music.", "1",
      t("questionImportPanel.excelSampleExplanations.clozeReading"), "", ""]
  ];

  const handleDownloadTemplate = async () => {
    setError(null);
    if (format === "xlsx") {
      const headers = t("questionImportPanel.excelHeaders", { returnObjects: true }) as string[];
      // Bổ sung 2026-08-28 (đã xác nhận với người dùng) — defaultKind ưu tiên CAO HƠN lọc theo Nhóm kỹ
      // năng: GV đã chọn cụ thể 1 loại thì chỉ cần đúng 1 dòng ví dụ loại đó để copy xuống nhiều dòng,
      // không cần cả bảng tra cứu nhiều loại nữa — xuất 1 sheet đơn như cũ, không cần tách nhiều sheet.
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-06 — fix thiếu sót: nhánh này trước đó
      // KHÔNG có cột "Hướng dẫn chi tiết" dù đã thêm cho 7 sheet nhóm bên dưới — GV chọn sẵn 1 loại cụ
      // thể cũng cần thấy hướng dẫn ngay trong sheet, không chỉ khi tải bản đủ 17 loại.
      if (defaultKind) {
        const blob = buildXlsxTemplateBlob(
          [...headers, t("questionImportPanel.guideColumnHint")],
          sampleRows.filter((row) => row[0] === defaultKind).map((row) => [...row, t(`questionImportPanel.columnHints.${row[0]}`)])
        );
        downloadBlob(blob, "mau-soan-cau-hoi.xlsx");
        return;
      }
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — chưa chọn defaultKind: xuất mẫu
      // NHIỀU SHEET theo IMPORT_KIND_GROUPS (thay vì 1 sheet trộn 17 loại như trước) để mở file lên đỡ
      // rối — mỗi loại vẫn dùng chung đúng 14 cột, chỉ tách VỊ TRÍ vật lý theo nhóm. Backend đọc được
      // nhiều sheet từ ExcelQuestionRowParser#parse() (xem Javadoc ở đó). Nhóm nào không còn loại nào
      // khớp allowedKindTokens (lọc theo Nhóm kỹ năng) thì bỏ hẳn sheet đó.
      //
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-06 — thêm CỘT THỨ 15 "Hướng dẫn chi
      // tiết" ngay trong TỪNG sheet nhỏ (không chỉ ở sheet "Hướng dẫn" riêng/khối gợi ý trên UI panel):
      // GV mở đúng sheet "Trắc nghiệm" là thấy luôn giải thích cho từng loại trong sheet đó, không phải
      // lật qua tab khác. An toàn vì ExcelQuestionRowParser#resolveHeaderColumns chỉ map cột có header
      // khớp alias đã biết (QuestionImportFieldAliases) — "Hướng dẫn chi tiết" không khớp alias nào nên
      // bị bỏ qua hoàn toàn lúc đọc (không có field nào map tới), dù sample row vẫn điền sẵn nội dung và
      // GV tự gõ dòng mới không điền cột này cũng không sao (cột thuần hiển thị, không ảnh hưởng parse).
      const kindSheets = IMPORT_KIND_GROUPS
        .map((group) => {
          const tokens = allowedKindTokens ? group.tokens.filter((tok) => allowedKindTokens.includes(tok)) : group.tokens;
          const rows = sampleRows
            .filter((row) => tokens.includes(row[0]))
            .map((row) => [...row, t(`questionImportPanel.columnHints.${row[0]}`)]);
          return { name: t(group.labelKey), headers: [...headers, t("questionImportPanel.guideColumnHint")], rows };
        })
        .filter((sheet) => sheet.rows.length > 0)
        .map((sheet) => ({ name: sheet.name, headers: sheet.headers, rows: sheet.rows }));
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-06 — sheet "Hướng dẫn" đứng ĐẦU file:
      // 1 cột (VD "Đoạn văn tham chiếu") mang nhiều nghĩa tùy loại (transcript/hộp từ vựng/đoạn văn đọc
      // hiểu/dòng hướng dẫn...) — nếu GV mở file từ đồng nghiệp gửi lại (không qua panel import, không
      // thấy khối gợi ý cột trên UI) thì không biết cột nào cần điền gì. Nhúng thẳng nội dung gợi ý
      // (questionImportPanel.columnHints.*, CÙNG nguồn với khối gợi ý trên UI, không viết lại) vào
      // chính file. Header 2 cột cố tình chọn tên KHÔNG khớp alias "content"/"Nội dung"
      // (QuestionImportFieldAliases) để ExcelQuestionRowParser#parse() luôn coi đây là sheet phụ, tự bỏ
      // qua khi import — đã đúng theo thiết kế "sheet thiếu header bắt buộc thì bỏ qua, không throw".
      const guideTokens = allowedKindTokens ?? ALL_KIND_TOKENS;
      const guideSheet = {
        name: t("questionImportPanel.guideSheetName"),
        headers: [t("questionImportPanel.guideColumnKind"), t("questionImportPanel.guideColumnHint")],
        rows: guideTokens.map((token) => [t(`questionImportPanel.kindLabels.${token}`), t(`questionImportPanel.columnHints.${token}`)])
      };
      const blob = buildXlsxMultiSheetTemplateBlob([guideSheet, ...kindSheets]);
      downloadBlob(blob, "mau-soan-cau-hoi.xlsx");
      return;
    }
    setDownloadingTemplate(true);
    try {
      const blob = await (examId
        ? downloadExamQuestionImportWordTemplate(skillCategory, teacherType, defaultKind || undefined)
        : downloadQuestionImportWordTemplate(defaultKind || undefined));
      downloadBlob(blob, "mau-soan-cau-hoi.docx");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("questionImportPanel.downloadTemplateFailed"));
    } finally {
      setDownloadingTemplate(false);
    }
  };

  /**
   * Bổ sung 2026-10-19 (đã xác nhận với người dùng — preview trước khi lưu, thay cho hành vi cũ ghi
   * thẳng vào DB ngay khi chọn file) — chỉ gọi endpoint preview (parse + validate, KHÔNG ghi DB), giữ
   * lại file để handleConfirm gửi lại khi GV xác nhận. Dùng chung cho cả preview lần đầu (handleFile)
   * lẫn "Kiểm tra lại" sau khi sửa tay (handleRevalidate) — khởi tạo lại editedRows từ kết quả MỚI
   * mỗi lần gọi, để lưới sửa luôn khớp đúng dữ liệu đã validate gần nhất.
   */
  const runPreview = async (file: File) => {
    setPreviewing(true);
    setError(null);
    try {
      const res = examId
        ? await previewExamQuestionImport(examId, file, defaultKind || undefined)
        : bankId
          ? await previewQuestionImport(bankId, file, defaultKind || undefined)
          : (() => { throw new Error(t("common.missingExamOrBankContext")); })();
      setResult(res);
      setPendingFile(file);
      setEditedRows(res.rowPreviews.map(rowPreviewToCells));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("questionImportPanel.previewFailed"));
    } finally {
      setPreviewing(false);
    }
  };

  const handleFile = async (file: File | null) => {
    if (!file) return;
    const lower = file.name.toLowerCase();
    if (!lower.endsWith(".xlsx") && !lower.endsWith(".docx")) {
      setError(t("questionImportPanel.invalidFileType"));
      return;
    }
    setResult(null);
    await runPreview(file);
    if (inputRef.current) inputRef.current.value = "";
  };

  const handleCellChange = (rowIdx: number, colIdx: number, value: string) => {
    setEditedRows((prev) => prev.map((row, i) => (i === rowIdx ? row.map((c, j) => (j === colIdx ? value : c)) : row)));
  };

  /**
   * Bổ sung 2026-10-19 (đã xác nhận với người dùng) — dựng lại 1 file .xlsx TỪ editedRows (đã sửa
   * tay trên lưới) bằng buildXlsxTemplateBlob (tự dựng OOXML, không parse — an toàn, không phải
   * thư viện ngoài xử lý file người dùng), rồi gửi lại qua ĐÚNG pipeline preview để validate lại —
   * không tự validate bằng JS (xem Javadoc QuestionImportRowPreview phía backend).
   */
  const handleRevalidate = async () => {
    const headers = t("questionImportPanel.excelHeaders", { returnObjects: true }) as string[];
    const blob = buildXlsxTemplateBlob(headers, editedRows);
    const regenerated = new File([blob], "xem-truoc-da-sua.xlsx", { type: blob.type });
    await runPreview(regenerated);
  };

  /** Gửi lại ĐÚNG file đã xem trước để ghi DB thật (giống hệt logic preview — chỉ khác dryRun ở backend). */
  const handleConfirm = async () => {
    if (!pendingFile) return;
    setConfirming(true);
    setError(null);
    try {
      const res = examId
        ? await importExamQuestions(examId, pendingFile, defaultKind || undefined)
        : bankId
          ? await importQuestions(bankId, pendingFile, defaultKind || undefined)
          : (() => { throw new Error(t("common.missingExamOrBankContext")); })();
      setResult(res);
      setPendingFile(null);
      if (res.successRows > 0) onImported(res.createdQuestions);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("questionImportPanel.importFailed"));
    } finally {
      setConfirming(false);
    }
  };

  const handleReset = () => {
    setResult(null);
    setPendingFile(null);
    setEditedRows([]);
    setError(null);
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
        {(["xlsx", "docx"] as FileFormat[]).map((f) => (
          <button
            key={f}
            type="button"
            onClick={() => setFormat(f)}
            className={`text-[13px] font-bold px-3 py-1.5 rounded-md transition-all ${
              format === f ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
            }`}
          >
            {f === "xlsx" ? t("questionImportPanel.formatExcel") : t("questionImportPanel.formatWord")}
          </button>
        ))}
      </div>

      <div className="space-y-2">
        <label className="block font-bold text-slate-600 mb-1 text-[12px] uppercase tracking-wider">{t("questionImportPanel.defaultKindLabel")}</label>
        {/*
         * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05 — đổi <Select> dropdown phẳng 17
         * token thành accordion gom theo IMPORT_KIND_GROUPS, mirror ĐÚNG pattern vừa duyệt ở
         * QuestionEditorForm.tsx (KIND_GROUPS + expandedGroupKey). "Không đặt mặc định" tách thành nút
         * riêng đứng trên accordion (không thuộc nhóm nào).
         */}
        <button
          type="button"
          onClick={() => setDefaultKind("")}
          className={`w-full text-left px-3 py-2 rounded-lg border text-sm font-semibold transition-colors ${
            defaultKind === "" ? "border-brand-red bg-brand-red/5 text-brand-red" : "border-slate-200 bg-white text-slate-500 hover:bg-slate-50"
          }`}
        >
          {t("questionImportPanel.defaultKindNone")}
        </button>
        {IMPORT_KIND_GROUPS.map((group) => {
          const tokens = group.tokens.filter((tok) => defaultKindOptions.includes(tok));
          if (tokens.length === 0) return null;
          const isExpanded = expandedGroupKey === group.labelKey;
          const hasActiveToken = tokens.includes(defaultKind);
          return (
            <div key={group.labelKey} className="border border-slate-200 rounded-xl overflow-hidden">
              <button
                type="button"
                onClick={() => setExpandedGroupKey(isExpanded ? null : group.labelKey)}
                className={`w-full flex items-center justify-between gap-2 px-3 py-2 text-left transition-colors ${
                  hasActiveToken ? "bg-brand-red/5" : "bg-slate-50 hover:bg-slate-100"
                }`}
              >
                <span className="text-[11px] font-extrabold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                  {t(group.labelKey)}
                  {hasActiveToken && !isExpanded && (
                    <span className="normal-case font-bold text-brand-red">— {t(`questionImportPanel.kindLabels.${defaultKind}`)}</span>
                  )}
                </span>
                <ChevronDown className={`w-4 h-4 text-slate-400 shrink-0 transition-transform ${isExpanded ? "rotate-180" : ""}`} />
              </button>
              {isExpanded && (
                <div className="p-2.5 pt-2 border-t border-slate-200 flex flex-wrap gap-2">
                  {tokens.map((token) => (
                    <button
                      key={token}
                      type="button"
                      onClick={() => setDefaultKind(token)}
                      className={`px-3 py-1.5 rounded-lg border text-sm font-semibold transition-colors ${
                        defaultKind === token ? "border-brand-red bg-brand-red/5 text-brand-red" : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
                      }`}
                    >
                      {t(`questionImportPanel.kindLabels.${token}`)}
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}

        {/*
         * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-06 — BỎ khối text dài render
         * questionImportPanel.columnHints.<token> khỏi panel (trước đó ở đây, key columnHintTitle đã xóa
         * hẳn vì không còn chỗ dùng) — nội dung hint giờ đã dài/chi tiết hơn nhiều (viết thêm theo yêu
         * cầu người dùng để giải thích rõ "Đoạn văn tham chiếu") nên hiện thẳng trong panel bị TRÀN, đẩy
         * "Ví dụ 1 dòng mẫu" xuống xa, ngược lại mục tiêu ban đầu "thấy ngay không cần lướt". Đồng thời
         * giờ ĐÃ có cùng nội dung này ngay trong file Excel (sheet "Hướng dẫn" + cột "Hướng dẫn chi tiết"
         * ở từng sheet nhỏ/từng loại lẻ, xem handleDownloadTemplate) nên không cần lặp lại nguyên văn ở
         * đây nữa — key i18n questionImportPanel.columnHints.* VẪN GIỮ (dùng để sinh nội dung Excel), chỉ
         * bỏ chỗ hiện trong JSX panel. Chỉ còn preview 1 dòng mẫu (ngắn gọn, trực quan) + 1 dòng trỏ sang
         * Excel nếu cần đọc đầy đủ.
         */}
        {defaultKind && (
          <div className="bg-slate-50/60 border border-slate-200 rounded-xl p-3 space-y-2">
            {(() => {
              const headers = t("questionImportPanel.excelHeaders", { returnObjects: true }) as string[];
              const sampleRow = sampleRows.find((row) => row[0] === defaultKind);
              if (!sampleRow) return null;
              const filledCells = headers
                .map((header, i) => ({ header, value: sampleRow[i] }))
                .filter((cell, i) => i > 0 && cell.value);
              if (filledCells.length === 0) return null;
              return (
                <div className="space-y-1">
                  <p className="text-[11px] font-extrabold uppercase tracking-wider text-slate-500">{t("questionImportPanel.samplePreviewTitle")}</p>
                  <div className="space-y-1">
                    {filledCells.map((cell) => (
                      <div key={cell.header} className="flex gap-2 text-[13px]">
                        <span className="font-bold text-slate-400 shrink-0 min-w-[110px]">{cell.header}</span>
                        <span className="text-slate-700 whitespace-pre-line break-words">{cell.value}</span>
                      </div>
                    ))}
                  </div>
                  <p className="text-[12px] text-slate-400 italic pt-1">{t("questionImportPanel.columnHintExcelPointer")}</p>
                </div>
              );
            })()}
          </div>
        )}
      </div>

      <button
        type="button"
        onClick={handleDownloadTemplate}
        disabled={downloadingTemplate}
        className="w-full flex items-center justify-center gap-2 border border-dashed border-slate-300 rounded-lg py-3 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
      >
        <Download className="w-4 h-4" />
        {downloadingTemplate ? t("common.loading") : t("questionImportPanel.downloadTemplate", { format })}
      </button>

      {!result && (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={previewing}
          className="w-full flex flex-col items-center justify-center gap-2 border-2 border-dashed border-slate-200 rounded-xl py-8 text-slate-500 hover:border-brand-orange hover:bg-orange-50/30 transition-colors disabled:opacity-50"
        >
          <UploadCloud className="w-6 h-6 text-brand-orange" />
          <span className="text-sm font-bold text-slate-700">
            {previewing ? t("questionImportPanel.previewing") : t("questionImportPanel.clickToChooseFile", { format })}
          </span>
        </button>
      )}
      <input ref={inputRef} type="file" accept=".xlsx,.docx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0] ?? null)} />

      <FloatingError message={error} onClose={() => setError(null)} />

      {result && (
        <div className="space-y-3">
          {isPreview && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 text-[13px] font-semibold text-amber-800">
              {t("questionImportPanel.previewNotice")}
            </div>
          )}

          <div className="grid grid-cols-3 gap-2 text-center">
            <div className="bg-slate-50 border border-slate-100 rounded-lg py-2">
              <div className="text-sm font-bold text-slate-800">{result.totalRows ?? "—"}</div>
              <div className="text-[12px] text-slate-400">{t("questionImportPanel.totalRows")}</div>
            </div>
            <div className="bg-emerald-50 border border-emerald-100 rounded-lg py-2">
              <div className="text-sm font-bold text-emerald-600">{result.successRows}</div>
              <div className="text-[12px] text-emerald-500">{t("questionImportPanel.successRows")}</div>
            </div>
            <div className="bg-rose-50 border border-rose-100 rounded-lg py-2">
              <div className="text-sm font-bold text-rose-600">{result.failedRows}</div>
              <div className="text-[12px] text-rose-500">{t("questionImportPanel.failedRows")}</div>
            </div>
          </div>

          {isPreview && result.rowPreviews.length > 0 && (
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-[12px] font-bold text-slate-600 uppercase">{t("questionImportPanel.previewRowsTitle")}</span>
                <Button type="button" variant="secondary" size="sm" onClick={handleRevalidate} disabled={previewing || confirming}>
                  {previewing ? t("questionImportPanel.previewing") : t("questionImportPanel.revalidateButton")}
                </Button>
              </div>
              {/*
               * Bổ sung 2026-10-19 (đã xác nhận với người dùng) — bảng dạng Excel sửa trực tiếp: mỗi
               * input khớp 1 cột file mẫu (xem rowPreviewToCells), dòng lỗi tô đỏ + hiện lý do ngay dưới
               * dòng đó. Sửa xong bấm "Kiểm tra lại" (handleRevalidate) để dựng lại file + validate lại
               * qua ĐÚNG backend, không tự validate bằng JS.
               */}
              <div className="border border-slate-200 rounded-lg overflow-auto max-h-[480px]">
                <table className="text-[13px] border-collapse w-max">
                  <thead className="sticky top-0 bg-slate-50 z-10">
                    <tr>
                      <th className="px-2 py-1.5 text-left font-bold text-slate-500 border-b border-slate-200 whitespace-nowrap">#</th>
                      {(t("questionImportPanel.excelHeaders", { returnObjects: true }) as string[]).map((h, i) => (
                        <th key={i} className="px-2 py-1.5 text-left font-bold text-slate-500 border-b border-slate-200 whitespace-nowrap min-w-[140px]">
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {result.rowPreviews.map((meta, rowIdx) => (
                      <React.Fragment key={rowIdx}>
                        <tr className={meta.valid ? "" : "bg-rose-50/60"}>
                          <td className="px-2 py-1 border-b border-slate-100 font-mono text-slate-400 whitespace-nowrap">
                            {meta.sheetLabel ? `${meta.sheetLabel} · ` : ""}
                            {meta.rowNumber}
                          </td>
                          {(editedRows[rowIdx] ?? []).map((cellValue, colIdx) => (
                            <td key={colIdx} className="p-0.5 border-b border-slate-100">
                              <input
                                value={cellValue}
                                onChange={(e) => handleCellChange(rowIdx, colIdx, e.target.value)}
                                className="w-36 px-1.5 py-1 text-[13px] border border-transparent rounded focus:border-brand-red focus:outline-none bg-transparent"
                              />
                            </td>
                          ))}
                        </tr>
                        {!meta.valid && (
                          <tr className="bg-rose-50/60">
                            <td />
                            <td colSpan={editedRows[rowIdx]?.length ?? 21} className="px-2 py-1 text-rose-600 text-[12px] font-semibold border-b border-rose-100">
                              {meta.error}
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {!isPreview && result.errorSummary.length > 0 && (
            <div className="border border-rose-100 rounded-lg overflow-hidden">
              <div className="bg-rose-50 px-3 py-1.5 text-[12px] font-bold text-rose-600 uppercase">{t("questionImportPanel.errorDetailTitle")}</div>
              <div className="max-h-48 overflow-y-auto divide-y divide-slate-100">
                {result.errorSummary.map((e, i) => (
                  <div key={i} className="px-3 py-1.5 text-sm flex gap-2">
                    <span className="font-mono font-bold text-slate-400 shrink-0">#{e.row}</span>
                    <span className="text-slate-600">{e.reason}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="flex gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={handleReset} disabled={confirming}>
              {isPreview ? t("questionImportPanel.chooseAnotherFile") : t("questionImportPanel.importAnotherFile")}
            </Button>
            {isPreview && (
              <Button type="button" variant="primary" size="sm" onClick={handleConfirm} disabled={confirming || result.successRows === 0}>
                {confirming ? t("questionImportPanel.confirming") : t("questionImportPanel.confirmSaveButton", { count: result.successRows })}
              </Button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
