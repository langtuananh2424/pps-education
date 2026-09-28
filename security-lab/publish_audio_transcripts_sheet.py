"""
Day bang "Link audio + Transcript" cua bo audio bac thang (s1/s2/s3) len tab
"audio-transcripts" trong Google Sheet -- de doi chieu dang dung loai cau tra
loi nao. Transcript dung lai DUNG ham bien doi trong generate_ladder_audio.py
(khong go tay), nen luon khop voi noi dung TTS da doc.

Chay: GOOGLE_SA_JSON=... GSHEET_ID=... python3 publish_audio_transcripts_sheet.py
"""

import csv
import os

import gspread

import generate_ladder_audio as gen

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
AUDIO_MAP = os.path.join(SCRIPT_DIR, "results", "reflex_ladder_audio_map.csv")
TAB = "audio-transcripts"

LEVEL_LABEL = {
    "s1": "Nói lần 1 — giọng Việt đọc tiếng Anh, ngập ngừng nhẹ, ngắt 0.7s giữa các câu (kỳ vọng trượt)",
    "s2": "Nói lần 2 — giọng Việt đọc tiếng Anh, lưu loát (kỳ vọng vẫn trượt do phát âm)",
    "s3": "Nói lần 3 — giọng tiếng Anh en-US-AnaNeural, lưu loát (kỳ vọng đạt)",
}


def spoken_text(level, final_text):
    _rate, pause, transform = gen.LEVELS[level]
    if transform is None:
        return final_text
    parts = [transform(i, s) for i, s in enumerate(gen.split_sentences(final_text))]
    return f" [ngắt {pause}s] ".join(parts)


def main():
    import json
    with open(gen.LADDER_FILE, encoding="utf-8") as f:
        ladder = {k: v for k, v in json.load(f).items() if not k.startswith("_")}
    urls = {}
    with open(AUDIO_MAP, encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            urls[os.path.splitext(row["filename"])[0]] = row["url"]

    rows = [["Link audio", "Transcript", "Câu hỏi", "Lần nói", "Độ dài (s)"]]
    for qid, item in ladder.items():
        for level in ("s1", "s2", "s3"):
            stem = f"q{qid}_{level}"
            path = os.path.join(gen.OUT_DIR, f"q{qid}", f"{stem}.mp3")
            dur = round(gen.duration(path), 1) if os.path.isfile(path) else ""
            text = spoken_text(level, item["writing"][2])
            if dur and dur >= gen.MAX_SECONDS - 0.1:
                text += f"  (bị cắt ở {gen.MAX_SECONDS}s như máy ghi âm thật — phần cuối không có trong audio)"
            rows.append([urls.get(stem, ""), text, f"{qid}: {item['question']}", LEVEL_LABEL[level], dur])

    # File cho loadtest_reflex_writing_speaking.py doc -> ghi transcript vao TUNG dong ket qua noi.
    transcripts_csv = os.path.join(SCRIPT_DIR, "results", "reflex_ladder_transcripts.csv")
    with open(transcripts_csv, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["stem", "transcript"])
        for qid, item in ladder.items():
            for level in ("s1", "s2", "s3"):
                w.writerow([f"q{qid}_{level}", spoken_text(level, item["writing"][2])])
    print(f"Da ghi transcript cho script test: {transcripts_csv}")

    if not os.environ.get("GSHEET_ID"):
        return
    ss = gspread.service_account(filename=os.environ["GOOGLE_SA_JSON"]).open_by_key(os.environ["GSHEET_ID"])
    try:
        ws = ss.worksheet(TAB)
        ws.clear()
    except gspread.WorksheetNotFound:
        ws = ss.add_worksheet(title=TAB, rows=len(rows) + 5, cols=len(rows[0]))
    ws.update(rows, "A1", value_input_option="RAW")
    ws.format("A1:E1", {"textFormat": {"bold": True}})
    ws.freeze(rows=1)
    print(f"Da ghi {len(rows) - 1} dong vao tab '{TAB}': {ss.url}")


if __name__ == "__main__":
    main()
