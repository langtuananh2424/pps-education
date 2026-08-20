/**
 * Xếp "lane" cho các buổi học chồng tiết trong cùng 1 ngày trên lưới thời
 * khóa biểu (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-19) —
 * thuật toán greedy interval-packing đơn giản, cap 3 lane. Buổi thứ 4+
 * chồng cùng khung tiết bị gom vào overflow (caller tự xử lý hiển thị
 * "+N khác"). Đây là đơn giản hoá có chủ đích, không phải thư viện xếp
 * lane calendar đầy đủ.
 */

export const MAX_LANES = 3;

export interface LaneItem {
  id: number;
  startPeriod: number;
  endPeriod: number;
}

export interface LaneAssignment {
  lane: number;
  overflow: boolean;
}

export function assignLanes(items: LaneItem[]): Map<number, LaneAssignment> {
  const result = new Map<number, LaneAssignment>();
  const sorted = [...items].sort((a, b) => a.startPeriod - b.startPeriod || a.id - b.id);
  // laneEndPeriod[lane] = tiết cuối cùng (lớn nhất) đang chiếm lane đó
  const laneEndPeriod: number[] = [];

  for (const item of sorted) {
    let placed = false;
    for (let lane = 0; lane < MAX_LANES; lane++) {
      if (laneEndPeriod[lane] === undefined || laneEndPeriod[lane] < item.startPeriod) {
        laneEndPeriod[lane] = item.endPeriod;
        result.set(item.id, { lane, overflow: false });
        placed = true;
        break;
      }
    }
    if (!placed) {
      result.set(item.id, { lane: MAX_LANES - 1, overflow: true });
    }
  }

  return result;
}
