/**
 * Xếp "lane" cho các buổi học chồng tiết trong cùng 1 ngày trên lưới thời
 * khóa biểu (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-19,
 * BỎ giới hạn 3 lane 2026-08-21 theo yêu cầu người dùng — Quản lý điểm
 * trường cần thấy ĐỦ mọi lớp trùng tiết, VD 5 lớp cùng học tiết 2-3, thay
 * vì gộp/che khuất buổi thứ 4 trở lên) — thuật toán greedy interval-
 * packing, KHÔNG còn cap số lane: buổi thứ N chồng giờ vẫn được cấp 1 lane
 * riêng, cột ngày đó tự giãn rộng theo (xem ClassPeriodGrid — mặc định độ
 * rộng 3 lane, chỉ giãn thêm khi thật sự cần).
 */

export const DEFAULT_LANES = 3;

export interface LaneItem {
  id: number;
  startPeriod: number;
  endPeriod: number;
}

export interface LaneAssignment {
  lane: number;
}

export function assignLanes(items: LaneItem[]): Map<number, LaneAssignment> {
  const result = new Map<number, LaneAssignment>();
  const sorted = [...items].sort((a, b) => a.startPeriod - b.startPeriod || a.id - b.id);
  // laneEndPeriod[lane] = tiết cuối cùng (lớn nhất) đang chiếm lane đó
  const laneEndPeriod: number[] = [];

  for (const item of sorted) {
    let lane = laneEndPeriod.findIndex((end) => end < item.startPeriod);
    if (lane === -1) lane = laneEndPeriod.length;
    laneEndPeriod[lane] = item.endPeriod;
    result.set(item.id, { lane });
  }

  return result;
}

/** Số lane thực tế đã dùng (lane lớn nhất + 1) trong 1 kết quả assignLanes — dùng để tính độ rộng cột. */
export function laneCountUsed(assignments: Map<number, LaneAssignment>): number {
  let max = 0;
  for (const { lane } of assignments.values()) {
    if (lane + 1 > max) max = lane + 1;
  }
  return max;
}
