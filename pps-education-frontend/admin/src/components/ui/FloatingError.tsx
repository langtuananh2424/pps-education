import React from "react";
import FloatingBanner, { FloatingBannerProps } from "./FloatingBanner";

/** Banner LỖI nổi (đỏ) ở giữa phía trên trang — xem FloatingBanner. */
export default function FloatingError(props: Omit<FloatingBannerProps, "variant">) {
  return <FloatingBanner {...props} variant="error" />;
}
