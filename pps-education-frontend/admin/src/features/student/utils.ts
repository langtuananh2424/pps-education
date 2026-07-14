import { Student } from "@/types";
import { BadgeVariant } from "@/components/ui/Badge";

export function getStatusLabel(status: Student["status"]): string {
  switch (status) {
    case "STUDYING":
      return "Đang học";
    case "RESERVED":
      return "Bảo lưu";
    case "SUSPENDED":
      return "Đình chỉ";
    case "GRADUATED":
      return "Tốt nghiệp";
    default:
      return status;
  }
}

export function getStatusVariant(status: Student["status"]): BadgeVariant {
  switch (status) {
    case "STUDYING":
      return "success";
    case "RESERVED":
      return "warning";
    case "SUSPENDED":
      return "danger";
    case "GRADUATED":
      return "info";
    default:
      return "neutral";
  }
}
