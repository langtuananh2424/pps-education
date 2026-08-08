package vn.com.pps.education.dto;

/** Kết quả sau 1 lần ghi nhận nghe hết audio — hintUnlocked = playCount >= hintUnlockThreshold. */
public record ListeningPlayProgressResponse(
        int playCount,
        int hintUnlockThreshold,
        boolean hintUnlocked
) {}
