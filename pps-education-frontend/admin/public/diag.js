// Chẩn đoán tạm (xem CLAUDE.md task trắng trang trên Safari/iOS 16.1) — bắt lỗi JS không có
// error boundary nào bắt được và in thẳng lên trang thay vì màn hình trắng im lặng.
// File tĩnh (không phải inline) vì CSP script-src không cho phép inline script
// (xem deploy/nginx/pps-security-headers.conf). Gỡ bỏ sau khi đã xác định nguyên nhân thật.
(function () {
  function describeValue(err) {
    if (err instanceof Event) {
      // Gap thuc te tren thiet bi test: co noi "throw <Event>" thang (khong boc trong Error) nen
      // err.stack/err.message rong, in ra "[object Event]" khong ro gi - boc tach thu cong.
      var parts = ["Event.type=" + err.type];
      var t = err.target;
      if (t) {
        parts.push("target=" + (t.tagName || (t.constructor && t.constructor.name) || typeof t));
        if (t.src) parts.push("src=" + t.src);
        if (t.href) parts.push("href=" + t.href);
      }
      return parts.join(" ");
    }
    return (err && (err.stack || err.message)) || String(err);
  }

  function showDiagError(label, msg) {
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
      // Loi runtime JS (co e.error/e.message that) khac loi tai resource (script/css/img 404,
      // chan boi mang/CSP...) - truong hop sau e.error/e.message rong, phai doc tu e.target.
      if (e.error || e.message) {
        var loc = e.filename ? " (tai " + e.filename + ":" + e.lineno + ":" + e.colno + ")" : "";
        showDiagError("LOI JS" + loc, describeValue(e.error || e.message));
        return;
      }
      var t = e.target;
      if (t && (t.src || t.href)) {
        showDiagError("LOI TAI RESOURCE", t.tagName + " -> " + (t.src || t.href));
        return;
      }
      showDiagError("LOI KHONG RO (resource event)", describeValue(e));
    },
    true
  );
  window.addEventListener("unhandledrejection", function (e) {
    showDiagError("LOI PROMISE", describeValue(e.reason));
  });
})();
