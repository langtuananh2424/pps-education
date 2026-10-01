<!--
UC-74 — RUBRIC NHẬN XÉT HẰNG NGÀY cho trợ lý AI (bổ sung ngoài SDD gốc, 2026-09-28; bổ sung mục 1–8 ngày 2026-09-29).
File này được chèn nguyên văn vào prompt của cả 3 bước (tách ý / viết / sửa) tại chỗ {{RUBRIC}}.
Học vụ/giáo viên cứ bổ sung trực tiếp tại đây — sửa xong cần build và deploy lại backend mới có hiệu lực.
Giữ file NGẮN GỌN (dưới ~1–2 trang): rubric đi kèm MỌI lượt gọi AI, càng dài càng tốn token.
Phần trong thẻ comment này KHÔNG gửi cho AI.
Mỗi bước chỉ nhận các mục nó cần (bổ sung 2026-10-01) — vì vậy GIỮ NGUYÊN dạng tiêu đề "## 1." … "## 6."
và đặt quy tắc mới vào đúng mục: tách ý ← mục 1; viết câu ← mục 2–6; sửa theo yêu cầu của giáo viên ← cả rubric;
soát lỗi / lý do từ chối (Quản lý) ← mục 1–4; đề xuất sửa / sửa theo yêu cầu của Quản lý ← mục 2–4.
Thêm mục mới (## 7.) thì phải khai báo thêm trong code (CommentAiDraftService / CommentAiReviewService) mới được gửi đi.
Những việc code đã tự chặn, KHÔNG cần viết vào đây: AI chỉ điền Thái độ + Nhận xét; không ghi chữ số;
đại từ thầy/cô; chống trùng lặp giữa các học sinh và với các buổi trước.
-->
# Rubric nhận xét hằng ngày — PPS English

## 1. Tiêu chí xác định Thái độ học tập (5 mức)
Chỉ chọn mức khi lời giáo viên có thông tin tương ứng; không đủ thông tin thì để trống.
Bảng này CHỈ dùng để chọn mức nội bộ — tuyệt đối không viết "nhất lớp", "hơn các bạn" hay so sánh với bạn khác vào nhận xét.

| Mức | Mã | Dấu hiệu trong lời giáo viên |
|---|---|---|
| Xuất sắc | EXCELLENT | Nổi bật trong lớp, được tuyên dương riêng, chủ động dẫn dắt/giúp bạn, hoàn thành vượt yêu cầu. |
| Tốt | GOOD | Tập trung, tích cực phát biểu, hợp tác tốt, hoàn thành đầy đủ nhiệm vụ trên lớp. |
| Khá | FAIR | Nhìn chung ổn nhưng chưa thật chủ động; đôi lúc cần nhắc nhở nhẹ. |
| Trung bình | AVERAGE | Hay mất tập trung, nói chuyện riêng, ít tham gia xây dựng bài, phải nhắc nhở nhiều lần. |
| Yếu | WEAK | Không tham gia, gây ảnh hưởng tới lớp, không hoàn thành nhiệm vụ dù đã được nhắc. |

**Thận trọng với Yếu/Trung bình:** mức Thái độ được quy ra % để tính điểm thái độ trung bình trên Portal phụ huynh, và Yếu/Trung bình 3 buổi liên tiếp sẽ sinh cảnh báo gửi phụ huynh. Vì vậy:
- Chỉ chọn Yếu/Trung bình khi giáo viên nói RÕ hành vi tiêu cực (VD "nói chuyện riêng nhiều lần", "không làm bài dù đã nhắc").
- Lời nói mơ hồ, nhẹ ("chưa tích cực lắm", "hơi trầm", "cần cố gắng thêm") → chọn Khá hoặc để trống, KHÔNG hạ xuống Trung bình.
- Còn phân vân giữa 2 mức → để trống cho giáo viên tự chọn.
- Câu ví dụ cho từng mức: xem mục 6.

## 2. Cấu trúc một nhận xét
- 2–4 câu, tối đa khoảng 400 ký tự.
1. Tình hình chung/tinh thần học của học sinh trong buổi.
2. Điểm làm tốt (nếu giáo viên có nói).
3. Điểm cần cải thiện (chỉ khi giáo viên có nói) — nêu nhẹ nhàng, mang tính xây dựng.
4. Lời động viên hoặc định hướng ngắn cho buổi sau.

## 3. Văn phong
- Người đọc là phụ huynh: lịch sự, gần gũi, tích cực; không phán xét.
- Nhận xét tiêu cực phải đi kèm hướng khắc phục hoặc lời động viên.
- Tránh từ nặng nề: "hư", "lười biếng", "kém cỏi", "không chịu học", "ngỗ nghịch".
- Không dùng tiếng lóng, emoji. Tối đa 1 dấu "!" cho cả nhận xét, không dùng "!!"/"!!!".
- Kỹ năng tiếng Anh dùng thống nhất: nghe, nói, đọc, viết, phát âm, từ vựng, ngữ pháp. Giữ nguyên thuật ngữ tiếng Anh của trung tâm (Speaking, Writing, Reflex…), không tự dịch.
- ĐƯỢC PHÉP: tên kỹ năng/hoạt động (Speaking, Writing, Reflex, Phonics…). CẤM: tên Unit, tên chủ đề hay tên bài học cụ thể của buổi.
- Xưng hô: câu đầu gọi tên (tên cuối trong họ tên), các câu sau dùng "con". Không rõ giáo viên là thầy hay cô thì không tự xưng — viết câu không chủ ngữ ("Mong con…"), KHÔNG viết "thầy/cô".
- Nhịp câu: dài ngắn xen kẽ, nên có ít nhất 1 câu ngắn (khoảng 8 từ trở xuống).

### Cách diễn đạt hành vi cần cải thiện
| Giáo viên nói | Viết cho phụ huynh |
|---|---|
| nói chuyện riêng | cần tập trung hơn trong giờ học |
| mất trật tự, nghịch | cần chú ý giữ nề nếp lớp học |
| không làm bài / làm chưa xong | chưa hoàn thành nhiệm vụ trên lớp |
| lười phát biểu, ít tham gia | cần mạnh dạn tham gia xây dựng bài hơn |
| hay quên đồ dùng, sách vở | cần chuẩn bị đồ dùng học tập đầy đủ hơn |
| đến muộn | cần chú ý đến lớp đúng giờ |

## 4. Những điều KHÔNG được viết
- **Không nhắc tên, lỗi hay hành vi của học sinh khác** trong nhận xét của 1 bạn (VD giáo viên nói "An với Bình nói chuyện riêng" → nhận xét của An chỉ viết "con còn nói chuyện riêng trong giờ", không có tên Bình; SAI: "con nói chuyện riêng với Bình", "con và Bình còn làm việc riêng"). Mỗi nhận xét chỉ nói về đúng học sinh đó.
- Không so sánh, xếp hạng với bạn khác hay cả lớp ("kém nhất lớp", "giỏi hơn các bạn"). ĐƯỢC so sánh với CHÍNH học sinh đó ở buổi trước (VD "lần này con đã mạnh dạn hơn") nhưng CHỈ khi giáo viên có nói về sự tiến bộ/thụt lùi đó.
- Không ghi con số điểm/phần trăm, không nhắc hạn nộp hay giao bài mới — các thông tin này đã có ô riêng trên form. Cấm mọi dạng lộ điểm, kể cả viết bằng chữ: "4/5", "12/14", "đúng 41/49 câu", "được 8 điểm", "3 sao", "80%", "đúng mười hai trên mười bốn câu", "điểm tối đa", điểm thập phân ("6.5"), số lần xung phong, bảng xếp hạng xung phong.
- Nhận xét là ĐOẠN VĂN, không viết dạng báo cáo/liệt kê ("Điểm thể hiện trên lớp: …", "Điểm nói: …", "Điểm nghe: …", "Xung phong: … lần", "Kiểm tra đầu giờ: …").
- Kết quả BTVN buổi trước: chỉ nhắc khi học sinh có trường "homework" (do hệ thống tính, đã chỉ giữ kỹ năng nổi bật hoặc tăng/giảm rõ). Dạng dữ liệu: "BTVN buổi trước theo kỹ năng: <kỹ năng> — <mức>; …" (kỹ năng là nghe / đọc / viết / ngữ pháp / từ vựng / phản xạ nói), có thể kèm "nhìn chung tiến bộ rõ/giảm rõ so với buổi trước". Luôn nêu ĐÚNG TÊN KỸ NĂNG, không viết chung chung "bài tập về nhà". Các mức và cách viết (VD với học sinh tên Thủy):
  - "cần cố gắng" (từ 50% trở xuống) → câu động viên, chỉ rõ kỹ năng cần luyện: "Thủy cần luyện tập thêm về kỹ năng nghe, con cố gắng nghe lại bài ở nhà nhé." / "Phần ngữ pháp con còn nhầm lẫn, cần ôn lại thêm."
  - "làm tốt" (từ 85%) → khen đúng kỹ năng: "Con làm bài đọc ở nhà rất tốt." / "Phần từ vựng con ôn tập chăm chỉ, đáng khen."
  - "chưa hoàn thành" (chưa làm bài) → nhắc nhẹ hoàn thành đúng bài đó: "Con nhớ hoàn thành bài luyện viết ở nhà nhé."
  - Một kỹ năng có 2 mức kèm nguồn (VD "đọc — làm tốt (bài online), chưa hoàn thành (bài trên giấy)") → nêu cả hai bằng lời, không nhắc chữ "online/trên giấy" nếu không cần.
  - "nhìn chung tiến bộ rõ"/"giảm rõ so với buổi trước" → nhắc nhẹ bằng lời ("bài tập về nhà của con tiến bộ rõ"), không so số.
  - Không có trường "homework" → KHÔNG nhắc BTVN.
- Thống kê BTVN nhiều buổi: trường "homeworkDetails" (nếu có) là danh sách ý do hệ thống tính, ĐÃ XẾP THEO THỨ TỰ ƯU TIÊN (ý đầu quan trọng nhất). Các loại ý và cách viết:
  - "chưa làm BTVN kỹ năng X hai lần liền" → nhắc nhẹ: "Mấy buổi gần đây con chưa làm bài luyện viết, con nhớ hoàn thành nhé."
  - "nộp BTVN muộn nhiều lần gần đây" → nhắc nhẹ: "Con chú ý nộp bài tập đúng hạn hơn nhé."
  - "điểm cần cải thiện cụ thể: <dạng câu / độ khó / tiêu chí> trong bài <kỹ năng>" → chỉ rõ chỗ cần luyện: "Ở bài ngữ pháp, con còn sai nhiều dạng câu điền từ." / "Bài nói của con cần chú ý thêm phần phát âm." / "Con làm được câu dễ, câu khó trong bài đọc còn lúng túng."
  - "kỹ năng X tiến bộ đều / đi xuống qua các buổi gần đây" → "Kỹ năng nghe của con tiến bộ rõ qua các buổi gần đây." / "Phần đọc mấy buổi nay có phần chững lại, con cần ôn thêm."
  - "chăm làm lại bài để cải thiện điểm", "làm đủ BTVN đều đặn nhiều buổi liên tiếp", "làm tốt <…>" → khen đúng việc đó.
  Tên tiêu chí trong ngoặc kép (VD "Fluency and Coherence") thì diễn đạt lại bằng tiếng Việt dễ hiểu cho phụ huynh (độ trôi chảy, phát âm, ngữ pháp, từ vựng…).
- Điểm danh & chuyên cần: trường "attendance" (nếu có) — VD "hôm nay đến lớp muộn", "hay đến lớp muộn trong các buổi gần đây", "nghỉ học không phép nhiều buổi gần đây", "đi học đầy đủ, đúng giờ nhiều buổi liên tiếp". Viết TỐI ĐA 1 CÂU, nhẹ nhàng, không trách móc, không suy đoán lý do, không ghi số buổi/số phút: "Con chú ý đến lớp đúng giờ hơn nhé." / "Con đi học rất chuyên cần, đáng khen." Không có trường này thì KHÔNG nhắc chuyện đi học/đi muộn.
- Gợi ý giọng văn: trường "toneHints" (nếu có) CHỈ để điều chỉnh cách viết, TUYỆT ĐỐI KHÔNG đưa nội dung của nó vào nhận xét — không nhắc chuyện trung tâm mời họp/trao đổi với phụ huynh, không ghi tuổi. VD học sinh nhỏ tuổi → câu ngắn, từ ngữ đơn giản, ấm áp; đang có trao đổi với phụ huynh về BTVN → không khen phần BTVN đó, nhắc nhở nhẹ nhàng mang tính đồng hành.
- Thông tin học sinh: trường "studentInfo" (nếu có, VD "mới vào lớp gần đây") được nhắc tối đa 1 vế và phải dùng đúng cụm "mới vào lớp" (VD "Con mới vào lớp nhưng đã bắt nhịp rất nhanh."). Dữ liệu này có thể chưa chính xác nên hệ thống sẽ gắn cảnh báo để giáo viên kiểm tra. Không có trường này thì KHÔNG tự suy ra học sinh mới/cũ.
- Nhận xét buổi khác loại giáo viên: trường "otherTeacherComment" (nếu có) là nhận xét gần nhất ở buổi của loại giáo viên kia (VD buổi giáo viên nước ngoài). CHỈ dùng để giữ nhận xét nhất quán và KHÔNG lặp lại câu chữ của nó; không chép ý, không nhắc tới giáo viên/buổi học đó (xem quy tắc không nhắc giáo viên khác ở trên).
- Giới hạn chung cho BTVN (gộp cả "homework" và "homeworkDetails"): vẫn giữ nguyên các ý giáo viên đã nói, THÊM TỐI ĐA 2 CÂU về BTVN. Chọn theo thứ tự ưu tiên: (1) bỏ bài / nộp muộn, (2) điểm yếu cụ thể và kỹ năng "cần cố gắng"/"chưa hoàn thành" trong "homework", (3) xu hướng, (4) lời khen. Có thể gộp 2 ý vào 1 câu (VD "Con làm bài đọc tốt nhưng cần luyện thêm kỹ năng nghe."). Không cần dùng hết mọi ý. Nếu giáo viên có nói riêng về BTVN của đúng kỹ năng đó mà khác dữ liệu thì theo lời giáo viên.
- Không nhắc tên bài học/Unit của buổi (VD "Unit 1: Hello Friend", "bài Past simple", "bài Friendship") hay số trang/tên dạng bài cụ thể ("trang 18 phần B", "đề cương ôn tập") — kể cả khi giáo viên đọc tên bài trong lời nói.
- Không nhắc sức khoẻ, hoàn cảnh gia đình, không dán nhãn tính cách ("con hay nóng tính", "con nhút nhát").
- Không đe doạ hay nhắc hình thức kỷ luật/phạt: bản tường trình, bảng kiểm điểm, chép phạt, quay video phạt, kiểm tra đột xuất, mời phụ huynh lên làm việc.
- Không nhắc tên hay việc của giáo viên khác (giáo viên nước ngoài, trợ giảng, giáo viên buổi trước).
- Không nhắc hoạt động ngoài giờ học tiếng Anh (tập văn nghệ, đá bóng, sự kiện, dã ngoại), đi thi hay thông báo lịch học. Việc nghỉ học, đi muộn, về sớm CHỈ được nhắc khi có trường "attendance" (dữ liệu điểm danh của hệ thống) hoặc khi giáo viên tự nói ra; không ghi lý do nghỉ/đi muộn (kể cả khi giáo viên kể lý do trong lời nói).

## 5. Gợi ý đa dạng câu mở đầu và câu kết
Trong mỗi lượt viết: không dùng cùng 1 kiểu mở đầu (hoặc câu kết) cho 2 học sinh LIỀN KỀ; có ít nhất 4 kiểu mở đầu và 3 kiểu kết khác nhau. Chọn luân phiên, diễn đạt lại theo ý giáo viên — không chép nguyên câu gợi ý.

Cụm sáo mòn hay bị lặp cho cả lớp — mỗi cụm dùng tối đa 1–2 lần mỗi lượt viết: "Hơn thế nữa", "Về nhà (con) luyện nói…", "…mong con…", "Con ngoan…", "…rất vui…", "…rất ấn tượng…", "Con đã nắm được…", "Con có cố gắng…", "Con cần chú ý…", "…nhờ bố mẹ…", "Tiếp tục phát huy…".

Mở đầu:
1. Tên + hành động: "An tập trung nghe giảng suốt buổi."
2. Điểm nổi bật: "Điểm đáng khen nhất buổi này là…"
3. Tinh thần học: "Tinh thần học của An hôm nay rất tích cực."
4. Hoạt động cụ thể: "Ở phần hoạt động nhóm, An…"
5. Điều cần lưu ý (khi ý chính là cần cải thiện): "An cần chú ý hơn…"
6. Kỹ năng: "Phần Speaking hôm nay, An…"
7. Thái độ với bạn bè: "An hợp tác rất tốt với các bạn trong nhóm."
8. Sự thay đổi của chính học sinh (chỉ khi giáo viên có nói): "So với buổi trước, An đã mạnh dạn hơn."
9. Câu ngắn gọn khởi đầu: "Một buổi học tích cực của An."
10. Lời ghi nhận của giáo viên — CHỈ khi teacherPronoun có giá trị, dùng đúng đại từ đó (VD "[thầy/cô theo teacherPronoun] ghi nhận An…").

Câu kết:
1. Lời hẹn: "Hẹn gặp con ở buổi sau nhé."
2. Ghi nhận: "Sự cố gắng của con rất đáng ghi nhận."
3. Định hướng cụ thể: "Buổi sau con thử giơ tay phát biểu trước nhé."
4. Mong muốn: "Mong con giữ vững tinh thần này."
5. Động viên ngắn: "Cố lên con!"
6. Gợi ý ôn luyện: "Con ôn lại phần vừa học để buổi sau tự tin hơn nhé."
7. Niềm tin: "Chắc chắn con sẽ tiến bộ nhanh."
8. Giữ phong độ: "Hãy giữ vững phong độ này nhé."
9. Không cần câu kết riêng khi nhận xét đã trọn ý (với nhận xét ngắn).

## 6. Mẫu câu tham khảo theo mức Thái độ
Tổng hợp từ nhận xét cũ của trung tâm — chỉ lấy VĂN PHONG, KHÔNG chép nguyên văn. Mẫu viết "Con" cho gọn; khi viết thật, câu đầu dùng tên gọi. Mẫu cố ý KHÔNG có đại từ "thầy/cô" (đại từ chỉ dùng theo teacherPronoun).

Xuất sắc (EXCELLENT):
- "Con sẵn sàng giúp các bạn chỉnh sửa khi phát âm chưa chính xác — tinh thần hợp tác này rất đáng khen."
- "Con chủ động đặt câu hỏi khi chưa hiểu bài, cho thấy con thực sự muốn nắm chắc kiến thức."
- "Con đọc bài tự tin, giọng rõ ràng, các âm khó đều phát âm dứt khoát và nhịp đọc trôi chảy."
- "Con nói lưu loát, diễn đạt vượt ra ngoài phần gợi ý với nhiều ý tưởng sáng tạo."
- "Con tự nhận ra lỗi ngữ pháp và tự chỉnh sửa."

Tốt (GOOD):
- "Con tập trung nghe giảng và hăng hái phát biểu, góp phần làm lớp học sôi nổi."
- "Con hoàn thành đầy đủ nhiệm vụ trên lớp và hợp tác tốt với bạn bè."
- "Con nhận ra từ vựng nhanh khi chỉ cần nghe gợi ý bằng định nghĩa."
- "Con đọc hiểu tốt, nắm được ý chính từng đoạn và tìm thông tin chi tiết khá nhanh."

Khá (FAIR):
- "Con ngoan và hoàn thành bài, tuy đôi lúc còn cần nhắc nhở để tập trung hơn."
- "Nhìn chung con ổn, chỉ cần mạnh dạn phát biểu thêm là sẽ tiến bộ nhanh."
- "Phát âm của con bước đầu có tiến bộ, nhưng vẫn còn lẫn ở một số âm khó, nhất là âm cuối."

Trung bình (AVERAGE):
- "Con còn nói chuyện riêng trong giờ, cần tập trung hơn để không bỏ lỡ bài học. Mong con chú ý hơn ở buổi sau."
- "Con chưa tham gia xây dựng bài và còn làm việc riêng. Buổi sau con thử giơ tay phát biểu trước nhé."

Yếu (WEAK):
- "Con chưa hoàn thành nhiệm vụ dù đã được nhắc. Mong con nghiêm túc hơn với việc học, bắt đầu từ việc chuẩn bị bài đầy đủ."
- "Con cần tập trung ngay từ đầu giờ, tránh để việc riêng ảnh hưởng đến lớp. Hy vọng buổi sau con sẽ thay đổi."
- "Con còn gặp nhiều khó khăn khi phát âm và chưa thật tự tin, cần luyện đọc thêm mỗi ngày."

Góp ý mềm (dùng thay vì chê trực tiếp):
- "Nếu con ôn kỹ hơn phần ngữ pháp, kết quả sẽ còn tiến bộ hơn nữa."
- "Chỉ cần cẩn thận hơn khi làm bài, con sẽ tránh được lỗi nhỏ."
- "Con thử chú ý hơn đến các từ nối giữa các đoạn để nắm mạch bài nhé."
- "Con cố gắng nói to hơn một chút để cả lớp nghe rõ hơn nhé."
- "Với sự chăm chỉ rèn luyện, con hoàn toàn có thể nói lưu loát hơn nữa."

Góp ý theo lỗi cụ thể (chỉ khi giáo viên nói đúng lỗi đó):
- "Con cần chú ý phân biệt is/am/are và have/has khi nói."
- "Con nhớ bật rõ âm cuối và thêm s cho danh từ số nhiều."
- "Con ôn lại các từ mới để dùng chính xác hơn."

Khen có bằng chứng (khen đúng việc giáo viên nêu, theo kỹ năng/tiêu chí):
- "Con phát âm rõ ràng, giọng đọc dễ nghe."
- "Con biết dùng từ nối để câu trả lời trôi chảy hơn."
- "Con biết kéo dài câu trả lời và dùng từ vựng hợp chủ đề."
- "Con bắt đầu dùng được mệnh đề quan hệ khi nói." (lớp IELTS có thể giữ tên tiêu chí: Pronunciation, Fluency, Grammatical Range, Lexical Resource, Coherence)

Ghi nhận tiến bộ so với CHÍNH học sinh (chỉ khi giáo viên có nói):
- "So với buổi trước, con đã mạnh dạn phát biểu hơn."
- "Từ chỗ còn rụt rè, nay con đã tự tin hơn nhiều."
- "Hôm nay con xung phong nhiều hơn hẳn buổi trước."
- "Phần phát âm của con có tiến bộ rõ."

Gợi ý cách luyện (tối đa 1 gợi ý, đúng điểm cần cải thiện giáo viên đã nêu):
- "Con thử nghe và nhại lại (shadowing) các đoạn hội thoại ngắn."
- "Con đặt mục tiêu mỗi buổi xung phong ít nhất một lần nhé."
- "Con ghi âm lại phần nói để tự kiểm tra phát âm."
- "Con chuẩn bị trước vài ý về chủ đề buổi sau."
- "Con luyện phát âm theo bảng IPA hoặc tra cách đọc trong từ điển."
- "Con luyện thêm với video phản xạ để trả lời nhanh hơn."
<!-- Học vụ có thể thay/bổ sung bằng câu thật đã được Quản lý duyệt. Quy tắc: câu mẫu KHÔNG chứa "thầy"/"cô" (có test tự động kiểm tra — CommentAiRubricTest). -->
