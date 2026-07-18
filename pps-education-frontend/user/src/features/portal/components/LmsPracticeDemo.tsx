import React, { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Mic, ShoppingBag, Sparkles, Trophy, Volume2 } from "lucide-react";

interface DictationExercise {
  id: string;
  sentence: string;
  blanks: string[];
  hint: string;
}

interface SpeakingExercise {
  id: string;
  phrase: string;
  vietnamese: string;
  difficulty: "Dễ" | "Trung bình" | "Khó";
}

interface RewardItem {
  id: string;
  title: string;
  cost: number;
  image: string;
  description: string;
}

const DICTATIONS: DictationExercise[] = [
  {
    id: "DICT-01",
    sentence: "I love learning English at PPS Junior Club",
    blanks: ["learning", "English"],
    hint: "Tôi yêu thích việc học tiếng Anh tại PPS Junior Club"
  },
  {
    id: "DICT-02",
    sentence: "The magical unicorn is jumping over the pink cloud",
    blanks: ["magical", "unicorn", "cloud"],
    hint: "Chú kỳ lân ma thuật đang nhảy qua đám mây màu hồng"
  }
];

const SPEAKING: SpeakingExercise[] = [
  { id: "SPEAK-01", phrase: "Hello, nice to meet you!", vietnamese: "Xin chào, rất vui được gặp bạn!", difficulty: "Dễ" },
  { id: "SPEAK-02", phrase: "The unicorn has a beautiful golden horn.", vietnamese: "Chú kỳ lân có một chiếc sừng vàng rất đẹp.", difficulty: "Trung bình" }
];

const REWARDS: RewardItem[] = [
  { id: "REW-01", title: "Sticker Kỳ lân lấp lánh", cost: 150, image: "🦄", description: "Bộ sticker kỳ lân dễ thương để dán vào góc học tập!" },
  { id: "REW-02", title: "Bút chì ma thuật", cost: 300, image: "✏️", description: "Chiếc bút chì thần kỳ giúp vẽ nên những giấc mơ đầy màu sắc." },
  { id: "REW-03", title: "Voucher học phí 5%", cost: 800, image: "🎟️", description: "Giảm 5% học phí kỳ tiếp theo." }
];

function speakText(text: string) {
  if ("speechSynthesis" in window) {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "en-US";
    utterance.rate = 0.85;
    window.speechSynthesis.speak(utterance);
  }
}

type SubTab = "dictation" | "speaking" | "rewards";

/**
 * Demo minh họa cho Luyện Nghe-Nói (UC-26) và cửa hàng đổi thưởng — giữ nguyên
 * trải nghiệm tương tác từ bản thiết kế gốc, dữ liệu tĩnh cục bộ (không gọi API)
 * vì backend chưa xây dựng phần này. Điểm/xu chỉ tồn tại trong phiên xem hiện tại.
 */
export default function LmsPracticeDemo() {
  const [subTab, setSubTab] = useState<SubTab>("dictation");
  const [points, setPoints] = useState(120);

  const [dictationIdx, setDictationIdx] = useState(0);
  const [dictationAnswers, setDictationAnswers] = useState<Record<string, string>>({});
  const [dictationChecked, setDictationChecked] = useState(false);
  const [dictationCorrect, setDictationCorrect] = useState(false);

  const [speakingIdx, setSpeakingIdx] = useState(0);
  const [isRecording, setIsRecording] = useState(false);
  const [speakingScore, setSpeakingScore] = useState<number | null>(null);

  const [purchaseSuccess, setPurchaseSuccess] = useState<string | null>(null);

  const currentDictation = DICTATIONS[dictationIdx];
  const currentSpeaking = SPEAKING[speakingIdx];

  const handleCheckDictation = () => {
    const allCorrect = currentDictation.blanks.every(
      (blank) => (dictationAnswers[`${currentDictation.id}-${blank}`] || "").trim().toLowerCase() === blank.toLowerCase()
    );
    setDictationChecked(true);
    setDictationCorrect(allCorrect);
    if (allCorrect) setPoints((p) => p + 50);
  };

  const handleNextDictation = () => {
    setDictationIdx((i) => (i + 1) % DICTATIONS.length);
    setDictationChecked(false);
    setDictationAnswers({});
  };

  const handleRecord = () => {
    if (isRecording) return;
    setIsRecording(true);
    setSpeakingScore(null);
    speakText(currentSpeaking.phrase);
    setTimeout(() => {
      setIsRecording(false);
      const score = Math.floor(Math.random() * 21) + 80;
      setSpeakingScore(score);
      setPoints((p) => p + (score >= 90 ? 30 : 15));
    }, 2500);
  };

  const handleBuy = (reward: RewardItem) => {
    if (points < reward.cost) return;
    setPoints((p) => p - reward.cost);
    setPurchaseSuccess(reward.title);
    setTimeout(() => setPurchaseSuccess(null), 2500);
  };

  return (
    <div className="bg-white border border-line/80 rounded-[20px] p-6 space-y-5 relative">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="inline-flex items-center gap-1.5 bg-gold/10 text-gold border border-gold/30 px-3 py-1 rounded-full text-[10px] font-extrabold uppercase tracking-wider">
          <Sparkles size={12} /> Sắp ra mắt — dữ liệu minh họa, chưa lưu thật
        </span>
        <div className="bg-plum/10 px-3 py-1.5 rounded-xl border border-plum/20 flex items-center gap-2">
          <span className="text-xs font-extrabold text-plum">XU DEMO:</span>
          <span className="text-sm font-extrabold text-gold">⭐ {points}</span>
        </div>
      </div>

      <div className="flex gap-2 border-b border-line pb-3">
        {(
          [
            ["dictation", "✏️ Chép chính tả"],
            ["speaking", "🎙️ Nói phản xạ"],
            ["rewards", "🛍️ Đổi xu thưởng"]
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            onClick={() => setSubTab(key)}
            className={`px-4 py-2 rounded-xl font-extrabold text-xs transition-all border ${
              subTab === key ? "bg-teal text-white border-teal-deep" : "bg-white text-muted border-line hover:bg-slate-50"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <AnimatePresence mode="wait">
        {subTab === "dictation" && (
          <motion.div key="dictation" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="max-w-xl mx-auto text-center space-y-5">
            <span className="text-xs font-extrabold text-coral">
              Bài {dictationIdx + 1} / {DICTATIONS.length}
            </span>
            <div className="bg-coral/5 p-5 rounded-[20px] border border-coral/20 inline-block">
              <button
                onClick={() => speakText(currentDictation.sentence)}
                className="w-14 h-14 bg-coral text-white rounded-full flex items-center justify-center mx-auto"
              >
                <Volume2 size={26} />
              </button>
              <span className="block text-xs font-extrabold text-coral mt-2">Bấm để nghe</span>
            </div>
            <div className="bg-sky-2 border border-line p-3 rounded-xl text-left">
              <p className="text-xs text-muted font-extrabold">Gợi ý:</p>
              <p className="text-sm font-bold text-ink">{currentDictation.hint}</p>
            </div>
            <div className="text-base font-bold text-ink leading-loose flex flex-wrap justify-center items-center gap-2">
              {currentDictation.sentence.split(" ").map((word, idx) => {
                const cleaned = word.replace(/[.,!?]/g, "");
                if (!currentDictation.blanks.includes(cleaned)) return <span key={idx}>{word}</span>;
                const ansKey = `${currentDictation.id}-${cleaned}`;
                const val = dictationAnswers[ansKey] || "";
                const isCorrect = val.trim().toLowerCase() === cleaned.toLowerCase();
                return (
                  <input
                    key={idx}
                    value={val}
                    disabled={dictationChecked}
                    onChange={(e) => setDictationAnswers((prev) => ({ ...prev, [ansKey]: e.target.value }))}
                    placeholder="???"
                    className={`border-b-2 text-center outline-none w-24 font-extrabold ${
                      dictationChecked ? (isCorrect ? "text-teal-deep border-teal" : "text-coral border-coral") : "border-ink focus:border-coral"
                    }`}
                  />
                );
              })}
            </div>
            {dictationChecked && (
              <div className={`p-3 rounded-xl text-center border font-extrabold text-sm ${dictationCorrect ? "bg-teal/10 text-teal-deep border-teal" : "bg-coral/10 text-coral border-coral/30"}`}>
                {dictationCorrect ? "✨ Chính xác! +50 xu" : "❌ Chưa đúng hết, nghe lại và thử lại nhé!"}
              </div>
            )}
            <button
              onClick={dictationChecked ? handleNextDictation : handleCheckDictation}
              className="w-full bg-ink hover:bg-slate-900 py-3 rounded-xl text-white font-extrabold text-sm flex items-center justify-center gap-1.5"
            >
              {dictationChecked ? (
                <>
                  Câu tiếp theo <Trophy size={14} />
                </>
              ) : (
                "Kiểm tra đáp án"
              )}
            </button>
          </motion.div>
        )}

        {subTab === "speaking" && (
          <motion.div key="speaking" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="max-w-xl mx-auto text-center space-y-5">
            <span className="text-xs font-extrabold text-gold">
              Bài {speakingIdx + 1} / {SPEAKING.length}
            </span>
            <div className="bg-sky-2 p-5 rounded-[20px] border border-line space-y-1">
              <button onClick={() => speakText(currentSpeaking.phrase)} className="w-10 h-10 bg-teal text-white rounded-full inline-flex items-center justify-center mb-2">
                <Volume2 size={18} />
              </button>
              <h3 className="text-lg font-extrabold text-ink">{currentSpeaking.phrase}</h3>
              <p className="text-sm italic text-muted font-bold">{currentSpeaking.vietnamese}</p>
              <span className="inline-block px-2 py-0.5 bg-gold/20 text-gold border border-gold/30 text-[10px] font-bold rounded-full">Độ khó: {currentSpeaking.difficulty}</span>
            </div>
            <button
              onClick={handleRecord}
              disabled={isRecording}
              className={`w-20 h-20 rounded-full flex items-center justify-center border border-coral mx-auto ${isRecording ? "bg-coral text-white animate-pulse" : "bg-coral/10 text-coral"}`}
            >
              <Mic size={32} />
            </button>
            <p className="text-xs font-bold text-muted">{isRecording ? "Đang ghi âm..." : "Bấm mic để ghi âm thử"}</p>
            {speakingScore !== null && (
              <div className="bg-sky-2 p-4 rounded-[20px] border border-line space-y-1">
                <span className="text-xl font-extrabold text-teal">{speakingScore}%</span>
                <p className="text-sm font-semibold text-ink">{speakingScore >= 90 ? "Phát âm rất chuẩn!" : "Khá tốt, luyện thêm chút nữa nhé!"}</p>
              </div>
            )}
            {speakingScore !== null && (
              <button
                onClick={() => {
                  setSpeakingIdx((i) => (i + 1) % SPEAKING.length);
                  setSpeakingScore(null);
                }}
                className="w-full bg-ink hover:bg-slate-900 py-3 rounded-xl text-white font-extrabold text-sm"
              >
                Câu tiếp theo
              </button>
            )}
          </motion.div>
        )}

        {subTab === "rewards" && (
          <motion.div key="rewards" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="space-y-4">
            {purchaseSuccess && (
              <div className="bg-teal/10 text-teal-deep p-3 rounded-xl border border-teal/30 font-extrabold text-sm text-center">
                🎉 Đã đổi thành công: {purchaseSuccess}!
              </div>
            )}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {REWARDS.map((item) => {
                const canAfford = points >= item.cost;
                return (
                  <div key={item.id} className="border border-line rounded-[20px] p-4 space-y-3 text-center">
                    <div className="w-14 h-14 bg-sky-2 rounded-[16px] flex items-center justify-center text-3xl mx-auto border border-line">{item.image}</div>
                    <h4 className="font-extrabold text-ink text-sm">{item.title}</h4>
                    <p className="text-[11px] text-muted font-bold">{item.description}</p>
                    <div className="flex justify-between items-center text-xs pt-2 border-t border-line">
                      <span className="text-muted font-extrabold">⭐ {item.cost} xu</span>
                    </div>
                    <button
                      onClick={() => handleBuy(item)}
                      disabled={!canAfford}
                      className={`w-full py-2 rounded-xl font-extrabold text-xs ${canAfford ? "bg-plum text-white" : "bg-gray-50 text-muted opacity-50 cursor-not-allowed"}`}
                    >
                      {canAfford ? "Đổi quà ngay" : "Chưa đủ xu"}
                    </button>
                  </div>
                );
              })}
            </div>
            <div className="flex items-center gap-2 text-[11px] text-rose-500 font-bold">
              <ShoppingBag size={14} />
              Bảng xu này chỉ minh họa trong phiên xem hiện tại, không lưu vào tài khoản thật.
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
