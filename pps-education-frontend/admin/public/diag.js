// Chẩn đoán tạm (xem CLAUDE.md task trắng trang trên Safari/iOS 16.1) — bắt lỗi JS không có
// error boundary nào bắt được và in thẳng lên trang thay vì màn hình trắng im lặng.
// File tĩnh (không phải inline) vì CSP script-src không cho phép inline script
// (xem deploy/nginx/pps-security-headers.conf). Gỡ bỏ sau khi đã xác định nguyên nhân thật.
(function () {
  function showDiagError(label, err) {
    var msg = (err && (err.stack || err.message)) || String(err);
    var el = document.createElement("pre");
    el.style.cssText =
      "white-space:pre-wrap;padding:16px;font-size:14px;line-height:1.4;color:#000;background:#fff;" +
      "position:fixed;inset:0;z-index:999999;overflow:auto;margin:0;";
    el.textContent = label + ":\n" + msg;
    document.body.appendChild(el);
  }
  window.addEventListener(
    "error",
    function (e) {
      showDiagError(e.message && e.filename ? "LOI TAI RESOURCE/JS" : "LOI JS", e.error || e.message || e);
    },
    true
  );
  window.addEventListener("unhandledrejection", function (e) {
    showDiagError("LOI PROMISE", e.reason);
  });
})();
