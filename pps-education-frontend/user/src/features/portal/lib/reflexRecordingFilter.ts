/**
 * V199 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b: bộ lọc bản ghi âm của học sinh,
 * port từ `recorder.js` trong mã tham chiếu bàn giao 29/9 của phòng đào tạo. Chỉ chạy khi công tắc
 * `reflex.recording_filter_enabled` (Quản trị hệ thống → Cài đặt hệ thống) đang BẬT.
 *
 * Khác mã tham chiếu ở một chỗ, cố ý: KHÔNG bật `noiseSuppression`/`autoGainControl` của trình duyệt — V183 đã đo
 * được hai xử lý đó có lượt làm mất hẳn dải <2000 Hz của giọng nói. Bộ lọc ở đây chỉ làm trên bản ghi thô sau khi
 * dừng ghi:
 *   1. Đổi về WAV mono 16 kHz (đúng định dạng backend gửi AI; backend nhận WAV thì không phải chuyển đổi lại).
 *   2. Lọc thông cao 90 Hz (tiếng ù quạt/xe) + thông thấp 7800 Hz (giữ đỉnh /s/ /ʃ/ /f/ cho checkpoint âm cuối).
 *   3. Cổng dìm tiếng nền −7 dB giữa các đoạn nói (mốc = sàn ồn của chính bản ghi +12 dB, không cao hơn giọng
 *      chính −28 dB; mở sớm 200 ms, giữ 360 ms). Phòng quá ồn thì cổng không bao giờ đóng — thà không lọc còn hơn
 *      lọc vào tiếng nói (đừng "sửa" min() thành max()).
 */

const TARGET_RATE = 16000;

type AudioContextCtor = typeof AudioContext;

function audioContextCtor(): AudioContextCtor | null {
  return window.AudioContext || (window as unknown as { webkitAudioContext?: AudioContextCtor }).webkitAudioContext || null;
}

/** Trình duyệt có đủ Web Audio (decode + OfflineAudioContext) để lọc không. */
export function recordingFilterSupported(): boolean {
  return audioContextCtor() != null && typeof window.OfflineAudioContext !== "undefined";
}

/** Lọc bản ghi thô → WAV 16 kHz mono. Ném lỗi nếu trình duyệt không giải mã được — caller nộp bản thô thay thế. */
export async function filterRecording(raw: Blob): Promise<Blob> {
  const Ctor = audioContextCtor();
  if (!Ctor) throw new Error("Web Audio không khả dụng");
  const ctx = new Ctor();
  let decoded: AudioBuffer;
  try {
    decoded = await ctx.decodeAudioData(await raw.arrayBuffer());
  } finally {
    void ctx.close();
  }
  const length = Math.max(1, Math.ceil(decoded.duration * TARGET_RATE));
  const offline = new OfflineAudioContext(1, length, TARGET_RATE);
  const src = offline.createBufferSource();
  src.buffer = decoded;
  const highpass = offline.createBiquadFilter();
  highpass.type = "highpass";
  highpass.frequency.value = 90;
  const lowpass = offline.createBiquadFilter();
  lowpass.type = "lowpass";
  lowpass.frequency.value = 7800;
  src.connect(highpass);
  highpass.connect(lowpass);
  lowpass.connect(offline.destination);
  src.start();
  const rendered = await offline.startRendering();
  const pcm = gateBackground(rendered.getChannelData(0), TARGET_RATE);
  return new Blob([encodeWav(pcm, TARGET_RATE)], { type: "audio/wav" });
}

function gateBackground(pcm: Float32Array, rate: number): Float32Array {
  const frame = Math.round(rate * 0.02);
  const n = Math.floor(pcm.length / frame);
  if (n < 10) return pcm;
  const rms = new Float32Array(n);
  for (let f = 0; f < n; f++) {
    let s = 0;
    for (let i = f * frame; i < (f + 1) * frame; i++) s += pcm[i] * pcm[i];
    rms[f] = Math.sqrt(s / frame);
  }
  const sorted = Array.from(rms).sort((a, b) => a - b);
  const loud = sorted[Math.floor(n * 0.95)];
  const noiseFloor = sorted[Math.floor(n * 0.1)];
  if (!(loud > 0.01)) return pcm; // bản ghi quá nhỏ: không đụng vào, để bộ đo phía server quyết định
  const threshold = Math.min(noiseFloor * 4, loud * 0.04);
  const open = new Uint8Array(n);
  const pre = 10;
  const hold = 18;
  for (let f = 0; f < n; f++) {
    if (rms[f] >= threshold) {
      for (let k = Math.max(0, f - pre); k <= Math.min(n - 1, f + hold); k++) open[k] = 1;
    }
  }
  const out = new Float32Array(pcm.length);
  let gain = 1;
  for (let f = 0; f < n; f++) {
    const target = open[f] ? 1 : 0.45; // −7 dB: đủ dìm ồn phòng, không đủ để giết một từ
    for (let i = f * frame; i < (f + 1) * frame; i++) {
      gain += (target - gain) * 0.005; // chuyển mượt, không gây tiếng lách cách
      out[i] = pcm[i] * gain;
    }
  }
  for (let i = n * frame; i < pcm.length; i++) out[i] = pcm[i] * gain;
  return out;
}

function encodeWav(samples: Float32Array, rate: number): ArrayBuffer {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  const writeString = (offset: number, s: string) => {
    for (let i = 0; i < s.length; i++) view.setUint8(offset + i, s.charCodeAt(i));
  };
  writeString(0, "RIFF");
  view.setUint32(4, 36 + samples.length * 2, true);
  writeString(8, "WAVE");
  writeString(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, rate, true);
  view.setUint32(28, rate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeString(36, "data");
  view.setUint32(40, samples.length * 2, true);
  let offset = 44;
  for (let i = 0; i < samples.length; i++, offset += 2) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return buffer;
}
