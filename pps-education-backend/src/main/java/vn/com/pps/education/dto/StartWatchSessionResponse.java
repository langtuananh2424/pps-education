package vn.com.pps.education.dto;

/** UC-23a (V59): mở 1 lượt xem mới cho video CONNECTION — sessionId dùng cho các lần reportProgress tiếp theo của lượt này. */
public record StartWatchSessionResponse(Long sessionId) {}
