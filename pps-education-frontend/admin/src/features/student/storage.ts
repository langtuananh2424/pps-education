import { CommentEntry, GradeEntry, Student } from "@/types";
import { mockCommentEntries, mockGradeEntries, mockStudents } from "@/data/mockData";

function readJson<T>(key: string, fallback: T): T {
  const saved = localStorage.getItem(key);
  if (saved) {
    try {
      return JSON.parse(saved) as T;
    } catch {
      /* fall through */
    }
  }
  localStorage.setItem(key, JSON.stringify(fallback));
  return fallback;
}

export const readStoredStudents = (): Student[] => readJson("pps_students", mockStudents);
export const writeStoredStudents = (students: Student[]) => localStorage.setItem("pps_students", JSON.stringify(students));

export const readStoredGradeEntries = (): GradeEntry[] => readJson("pps_grade_entries", mockGradeEntries);
export const writeStoredGradeEntries = (entries: GradeEntry[]) => localStorage.setItem("pps_grade_entries", JSON.stringify(entries));

export const readStoredCommentEntries = (): CommentEntry[] => readJson("pps_comment_entries", mockCommentEntries);
export const writeStoredCommentEntries = (entries: CommentEntry[]) => localStorage.setItem("pps_comment_entries", JSON.stringify(entries));
