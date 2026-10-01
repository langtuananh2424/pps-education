#!/usr/bin/env python3
"""Tổng hợp chỉ số chất lượng nhận xét AI (UC-74) theo tuần từ log backend.

Backend ghi 1 dòng log mỗi lần soạn nháp / viết lại toàn bộ:
    ... CommentAiDraftService : COMMENT_AI_METRICS {"event": "DRAFT", "rows": 26, ...}
(xem CommentAiDraftMetrics.java). Log không chứa nội dung nhận xét hay tên học sinh.

Cách dùng:
    docker compose logs --no-color backend | python3 scripts/comment-ai-metrics.py
    python3 scripts/comment-ai-metrics.py backend-1.log backend-2.log
    python3 scripts/comment-ai-metrics.py --csv backend.log > metrics.csv

Ngưỡng "tốt" lấy theo bảng đo lường đã thống nhất (2026-09-29); dòng nào không đạt được đánh dấu "✗".
"""
import argparse
import csv
import datetime as dt
import json
import re
import sys
from collections import defaultdict

PREFIX = "COMMENT_AI_METRICS"
DATE_RE = re.compile(r"(\d{4}-\d{2}-\d{2})")

def pct(part, whole):
    return 100.0 * part / whole if whole else 0.0


def week_of(line):
    match = DATE_RE.search(line)
    if not match:
        return "không rõ ngày"
    year, week, _ = dt.date.fromisoformat(match.group(1)).isocalendar()
    return f"{year}-W{week:02d}"


def parse(lines):
    weeks = defaultdict(list)
    for line in lines:
        idx = line.find(PREFIX)
        if idx < 0:
            continue
        payload = line[idx + len(PREFIX):].strip()
        try:
            weeks[week_of(line)].append(json.loads(payload))
        except json.JSONDecodeError:
            continue
    return weeks


def summarize(records):
    rows = sum(r.get("rows", 0) for r in records)
    written = sum(r.get("written", 0) for r in records)
    warn = defaultdict(int)
    for r in records:
        for key, value in (r.get("warningRows") or {}).items():
            warn[key] += value
    weighted = lambda key: (sum(r.get(key, 0) * r.get("written", 0) for r in records) / written) if written else 0.0
    return {
        "jobs": len(records),
        "rows": rows,
        "opening_repeat_pct": 100.0 * weighted("openingRepeatRate"),
        "closing_repeat_pct": 100.0 * weighted("closingRepeatRate"),
        "avg_length": weighted("avgLength"),
        "short_sentence_pct": pct(sum(r.get("shortSentenceRows", 0) for r in records), written),
        "digits_pct": pct(warn["CONTAINS_DIGITS"], written),
        "lesson_title_pct": pct(warn["LESSON_TITLE"], written),
        "pronoun_mismatch_pct": pct(warn["PRONOUN_MISMATCH"], written),
        "repeated_pattern_pct": pct(warn["REPEATED_PATTERN"], written),
        "similar_pct": pct(warn["SIMILAR_IN_SESSION"] + warn["SIMILAR_TO_PREVIOUS"], written),
        "not_written": warn["NOT_WRITTEN"],
        "homework_without_data": sum(r.get("homeworkWithoutData", 0) for r in records),
        "unmatched": sum(r.get("unmatched", 0) for r in records),
    }


# (khoá trong summarize, nhãn, hàm kiểm tra đạt ngưỡng hoặc None nếu chỉ để theo dõi, mô tả ngưỡng)
CHECKS = [
    ("opening_repeat_pct", "Lặp mở bài %", lambda v: v < 30, "< 30%"),
    ("closing_repeat_pct", "Lặp câu kết %", lambda v: v < 30, "< 30%"),
    ("avg_length", "Độ dài TB (ký tự)", lambda v: 250 <= v <= 400, "250–400"),
    ("short_sentence_pct", "Có câu ngắn %", lambda v: v >= 95, "≥ 95%"),
    ("digits_pct", "Có chữ số %", lambda v: v == 0, "0%"),
    ("lesson_title_pct", "Nhắc tên bài %", lambda v: v == 0, "0%"),
    ("pronoun_mismatch_pct", "Sai xưng hô %", lambda v: v == 0, "0%"),
    ("homework_without_data", "Nhắc BTVN không có dữ liệu", lambda v: v == 0, "0"),
    ("repeated_pattern_pct", "Còn lặp kiểu câu %", None, ""),
    ("similar_pct", "Còn trùng lặp %", None, ""),
]


def print_table(weeks):
    if not weeks:
        print("Không tìm thấy dòng log COMMENT_AI_METRICS nào.")
        return
    for week in sorted(weeks):
        s = summarize(weeks[week])
        print(f"\n=== {week}: {s['jobs']} lần soạn, {s['rows']} dòng, "
              f"{s['not_written']} dòng AI không viết được, {s['unmatched']} câu nhắc tên chưa khớp ===")
        for key, label, check, target in CHECKS:
            value = s[key]
            mark = "" if check is None else ("✓" if check(value) else "✗")
            shown = f"{value:.1f}" if isinstance(value, float) else str(value)
            print(f"  {label:<30} {shown:>8}  {target:<8} {mark}")


def print_csv(weeks):
    writer = csv.writer(sys.stdout)
    keys = ["jobs", "rows"] + [key for key, *_ in CHECKS] + ["not_written", "unmatched"]
    writer.writerow(["week"] + keys)
    for week in sorted(weeks):
        s = summarize(weeks[week])
        writer.writerow([week] + [round(s[k], 2) if isinstance(s[k], float) else s[k] for k in keys])


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("files", nargs="*", help="file log (bỏ trống thì đọc stdin)")
    parser.add_argument("--csv", action="store_true", help="xuất CSV thay cho bảng")
    args = parser.parse_args()
    lines = []
    if args.files:
        for path in args.files:
            with open(path, encoding="utf-8", errors="replace") as handle:
                lines.extend(handle)
    else:
        lines = sys.stdin.readlines()
    weeks = parse(lines)
    (print_csv if args.csv else print_table)(weeks)


if __name__ == "__main__":
    main()
