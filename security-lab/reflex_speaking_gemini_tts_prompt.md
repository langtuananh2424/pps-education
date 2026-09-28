# Prompt cho Gemini — tạo audio test chấm Speaking Reflex (PPS Education)

> Copy toàn bộ nội dung dưới đây (từ "## Vai trò" trở xuống) đưa cho Gemini.
> Nếu Gemini giới hạn số lượng audio tạo được trong 1 lượt, chia nhỏ theo
> từng câu hỏi (mỗi câu 1 lượt riêng, 10 audio/lượt) — nội dung đã được tách
> sẵn theo từng câu bên dưới để dễ cắt.

## Vai trò

Bạn là công cụ Text-to-Speech, đóng vai 1 học sinh Việt Nam lớp 7 (khoảng
12-13 tuổi) đang nói tiếng Anh trình độ Pre-IELTS (B1) để trả lời phỏng vấn
nói (Speaking) trong 1 bài kiểm tra tiếng Anh. Nhiệm vụ: đọc thành audio từng
đoạn văn bản dưới đây, ĐÚNG theo phong cách/giọng điệu (style) đã ghi kèm mỗi
đoạn — vì mục đích của các audio này là kiểm thử 1 hệ thống AI chấm nói tự
động (không phải audio mẫu chuẩn để học sinh nghe theo), nên audio "kém" (nói
sai ngữ pháp, ngập ngừng, lạc đề...) phải nghe ĐÚNG NHƯ MÔ TẢ, không tự sửa
lại cho hay hơn.

## Giọng đọc chung

- **Giọng tiếng Anh có accent Việt Nam** (non-native, kiểu học sinh Việt Nam
  nói tiếng Anh) — KHÔNG dùng giọng bản xứ Anh/Mỹ/Úc chuẩn. Đây là điểm quan
  trọng nhất: hệ thống thật sẽ chấm học sinh Việt Nam thật, nên audio test
  phải phản ánh đúng kiểu phát âm/ngữ điệu đó để kết quả có ý nghĩa tham
  khảo — nếu dùng giọng bản xứ hoàn hảo, bài `excellent`/`good_simple` có
  thể được điểm cao giả tạo (cao hơn học sinh thật đạt được) do phát âm
  "quá chuẩn" so với đối tượng thật.
- Giọng trẻ, phù hợp học sinh 12-13 tuổi, không phải giọng người lớn/MC
  chuyên nghiệp. Có thể xen giọng nam/nữ khác nhau giữa các file, không cần
  đồng nhất 1 giọng cho cả 90 file.
- Tiếng Anh rõ ràng ở mức đủ hiểu (không đọc lí nhí/nuốt chữ) — không mô
  phỏng được lỗi phát âm CHI TIẾT (âm đuôi/trọng âm sai) một cách chủ đích,
  nhưng accent Việt Nam tự nhiên là đủ để không bị "quá hoàn hảo" so với
  thực tế.
- KHÔNG thêm nhạc nền, hiệu ứng âm thanh, không ghép nhiều đoạn thu rời
  thành 1 file — mỗi transcript là 1 lượt đọc liền mạch.

## Style theo từng mã dạng — đọc ĐÚNG phong cách này, không tự làm mượt

| Mã dạng | Cách đọc |
|---|---|
| `excellent` | Tự nhiên, lưu loát, tốc độ vừa phải, tự tin |
| `good_simple` | Tự nhiên nhưng đơn giản, tốc độ vừa phải |
| `off_topic_full` | Tự tin, lưu loát y hệt `excellent` — CHỈ nội dung lạc đề, giọng đọc KHÔNG được có gì khác thường |
| `off_topic_partial` | Bắt đầu tự nhiên như đang trả lời đúng, rồi giọng chuyển hướng tự nhiên như vừa nhớ ra chuyện khác |
| `hesitant` | Đọc CHẬM, có khoảng dừng rõ ràng ở mỗi dấu "...", nhấn "umm"/"uh" như đang suy nghĩ, ngắt quãng giữa câu |
| `short` | Đọc nhanh gọn, như trả lời cho có, không kéo dài |
| `long_rambling` | Đọc liên tục, ít ngắt hơi, như đang nói một hơi dài không dừng |
| `grammar_errors` | Đọc tự nhiên, tự tin — giọng KHÔNG được lộ vẻ "biết mình sai", đọc y như đang nói đúng |
| `filler_only` | Đọc rất chậm, nhiều khoảng lặng dài, giọng lưỡng lự, nhỏ dần cuối câu |
| `borderline` | Tự nhiên, ngắn gọn, rõ ràng, không ngập ngừng |

## Quy ước đặt tên file + đóng gói output

- Đặt tên mỗi file theo mẫu `q<question_id>_<mã_dạng>.mp3` — VD câu 46, dạng
  `excellent` → `q46_excellent.mp3`. Giữ đúng `question_id` ghi trong tiêu đề
  mỗi câu bên dưới (46-54) để khớp với hệ thống thật.
- Gom tất cả file vào 1 thư mục con riêng cho TỪNG câu hỏi, rồi nén TOÀN BỘ
  thành 1 file `.zip` duy nhất, cấu trúc:
  ```
  reflex_speaking_audio.zip
  ├── q46/
  │   ├── q46_excellent.mp3
  │   ├── q46_good_simple.mp3
  │   ├── ... (đủ 10 file)
  ├── q47/
  │   ├── q47_excellent.mp3
  │   ├── ...
  ├── ... (hết q48-q54)
  ```
- KHÔNG tự động đẩy/lưu lên Google Drive hay bất kỳ dịch vụ nào khác — chỉ
  tạo file `.zip` để tải về thủ công từ cuộc trò chuyện với Gemini.

## Thời lượng mục tiêu

Đọc với tốc độ nói tự nhiên của 1 học sinh 12-13 tuổi (không đọc nhanh bất
thường) — không cần canh giây chính xác, cứ đọc tự nhiên theo đúng độ dài
văn bản, ngoại trừ `long_rambling` cố tình đọc dài hơn hẳn các dạng khác.

## Định dạng file (đã xác minh lại trong code, sửa lại so với trao đổi trước)

Hệ thống chấp nhận BẤT KỲ định dạng audio nào (`MediaStorageService.java:85`
chỉ kiểm tra content-type bắt đầu bằng `audio/`, không giới hạn riêng
webm/mp4) — Gemini xuất mặc định ra `.mp3`/`.wav` đều dùng được thẳng,
KHÔNG cần convert sang webm/mp4 như tôi có nhắc trước đó (thông tin đó chỉ
đúng cho audio ghi trực tiếp từ trình duyệt, không áp dụng khi upload file
có sẵn qua API).

---

## Câu 46 (question_id=46) — "Do you have any hobbies?"

1. `q46_excellent.mp3` — style `excellent`: "Yes, I do. My favourite hobby is playing badminton. I usually play it with my friends after school, twice a week. I really enjoy it because it helps me stay healthy and it's a great way to relax after a long day of studying."
2. `q46_good_simple.mp3` — style `good_simple`: "Yes, I have a hobby. I like playing badminton with my friends. It is fun and I feel happy when I play it."
3. `q46_off_topic_full.mp3` — style `off_topic_full`: "This morning I had a bowl of noodles and a glass of milk for breakfast. My mother cooked it before I went to school, and it was really delicious."
4. `q46_off_topic_partial.mp3` — style `off_topic_partial`: "Yes, I do have hobbies... actually, speaking of that, did you know it might rain this afternoon? The sky looks really dark and I forgot my umbrella at home."
5. `q46_hesitant.mp3` — style `hesitant`: "Umm... yes, I... I think I have a hobby. It's like... umm... playing badminton? I play it... sometimes, with my... umm... friends."
6. `q46_short.mp3` — style `short`: "Yes, badminton."
7. `q46_long_rambling.mp3` — style `long_rambling`: "Yes, I have many hobbies, actually so many that I don't even know where to start, but I guess the main one is badminton, although sometimes I also like drawing, and sometimes I like watching videos online, and my friends also have hobbies like football and swimming, and we all talk about our hobbies during break time at school every single day."
8. `q46_grammar_errors.mp3` — style `grammar_errors`: "Yes, I have. I is like playing badminton with my friend. She play very good and we is happy when we playing together."
9. `q46_filler_only.mp3` — style `filler_only`: "Umm... uh... I don't know... umm... maybe... uh..."
10. `q46_borderline.mp3` — style `borderline`: "Yes, I like playing badminton. I play with my friends after school."

## Câu 47 (question_id=47) — "Do you have the same hobbies as your family members?"

1. `q47_excellent.mp3` — style `excellent`: "Not exactly. My father enjoys fishing on weekends, while my mother prefers gardening. I don't really share those hobbies, but we all enjoy watching movies together as a family every Sunday evening."
2. `q47_good_simple.mp3` — style `good_simple`: "No, we are different. My father likes fishing. I like playing badminton. But we watch movies together sometimes."
3. `q47_off_topic_full.mp3` — style `off_topic_full`: "I still have a lot of homework to finish tonight. My teacher gave us three exercises in Maths and one essay in English, so I need to start soon."
4. `q47_off_topic_partial.mp3` — style `off_topic_partial`: "My family members have different hobbies from me... by the way, I heard the weather will be very hot this weekend, so we might not go outside much."
5. `q47_hesitant.mp3` — style `hesitant`: "Umm... my family... they have... umm... different hobbies, I think? My dad likes... umm... fishing, and I... umm... don't really like it."
6. `q47_short.mp3` — style `short`: "No, we are different."
7. `q47_long_rambling.mp3` — style `long_rambling`: "Well, that's a really interesting question because my whole family has so many different hobbies, my dad likes fishing, my mum likes gardening, my older brother likes playing video games all day, my little sister likes drawing, and honestly I don't think anyone in my family shares the exact same hobby as me, which is a bit funny when I think about it."
8. `q47_grammar_errors.mp3` — style `grammar_errors`: "No, my family is have different hobby. My father like fishing but I doesn't like it. We is different."
9. `q47_filler_only.mp3` — style `filler_only`: "Uh... umm... my family... umm... I'm not sure... uh..."
10. `q47_borderline.mp3` — style `borderline`: "No, my father likes fishing. I like badminton. We are different."

## Câu 48 (question_id=48) — "Are there any differences between the activities you liked when you were a child and those you like now?"

1. `q48_excellent.mp3` — style `excellent`: "Yes, definitely. When I was a child, I loved playing with toy cars and building blocks. Nowadays, I prefer more active hobbies like playing badminton and reading books, because I've grown up and my interests have changed a lot."
2. `q48_good_simple.mp3` — style `good_simple`: "Yes, there are differences. When I was small, I liked toys. Now I like sports and reading. My interest changed."
3. `q48_off_topic_full.mp3` — style `off_topic_full`: "I usually go to bed at ten o'clock on school nights. My mother always reminds me to sleep early so I can wake up on time for school the next morning."
4. `q48_off_topic_partial.mp3` — style `off_topic_partial`: "When I was a child I liked different things... actually, I just remembered I need to buy a new pair of shoes because mine are too small now."
5. `q48_hesitant.mp3` — style `hesitant`: "Umm... yes, I think so? When I was... umm... little, I liked... umm... toys, and now I... umm... like different things, like sports."
6. `q48_short.mp3` — style `short`: "Yes, I liked toys before, sports now."
7. `q48_long_rambling.mp3` — style `long_rambling`: "Oh yes, there are so many differences, when I was really young I used to love playing with building blocks and toy cars for hours, and I also liked watching cartoons every single day, but now that I'm older I've become more interested in sports like badminton, and I also enjoy reading books, and sometimes I even like studying new subjects which is something I never expected when I was a child."
8. `q48_grammar_errors.mp3` — style `grammar_errors`: "Yes, is have differences. When I is child I liked toy. Now I likes sport and reading more better."
9. `q48_filler_only.mp3` — style `filler_only`: "Umm... uh... I... umm... don't remember... uh..."
10. `q48_borderline.mp3` — style `borderline`: "Yes. I liked toys before. Now I like sports and reading."

## Câu 49 (question_id=49) — "Do you usually spend your days off with your parents or with your friends?"

1. `q49_excellent.mp3` — style `excellent`: "I usually spend my days off with my friends. We often go to the park to play badminton or just hang out and chat. However, I also try to spend some time with my parents, especially having dinner together on Sunday evenings."
2. `q49_good_simple.mp3` — style `good_simple`: "I spend my days off with my friends. We play badminton together. Sometimes I stay home with my parents too."
3. `q49_off_topic_full.mp3` — style `off_topic_full`: "I really enjoy listening to music while doing my homework. It helps me concentrate better, especially soft music without too many lyrics."
4. `q49_off_topic_partial.mp3` — style `off_topic_partial`: "On my days off, I usually... actually, have you tried the new bubble tea shop near school? My friends and I went there last week and it was really good."
5. `q49_hesitant.mp3` — style `hesitant`: "Umm... I usually spend it with... umm... my friends, I think. We... umm... go out sometimes, and... umm... my parents too, sometimes."
6. `q49_short.mp3` — style `short`: "With my friends, mostly."
7. `q49_long_rambling.mp3` — style `long_rambling`: "That's a good question, I think it really depends on the week, sometimes I spend my whole weekend with my friends going to the park or playing badminton or just talking about school, but other weekends I stay home with my parents and we watch movies or go out for dinner, so honestly it's a mix of both and I can't really choose just one."
8. `q49_grammar_errors.mp3` — style `grammar_errors`: "I usually spends day off with my friend. We is play badminton. My parents also I spend time sometimes."
9. `q49_filler_only.mp3` — style `filler_only`: "Uh... umm... I'm not sure... umm... maybe both... uh..."
10. `q49_borderline.mp3` — style `borderline`: "I usually spend days off with my friends. We play badminton together."

## Câu 50 (question_id=50) — "Have you ever posted anything on social media?"

1. `q50_excellent.mp3` — style `excellent`: "Yes, I have. I sometimes post photos of my badminton matches or pictures from family trips on social media. I enjoy sharing happy moments with my friends and seeing their reactions."
2. `q50_good_simple.mp3` — style `good_simple`: "Yes, I have posted photos before. I posted pictures from my trip with my family. My friends liked them."
3. `q50_off_topic_full.mp3` — style `off_topic_full`: "My favourite subject at school is English because my teacher makes the lessons very interesting and fun with a lot of games and group activities."
4. `q50_off_topic_partial.mp3` — style `off_topic_partial`: "Yes, I've posted before... actually, that reminds me, I need to bring my sports shoes tomorrow because we have a P.E. lesson in the morning."
5. `q50_hesitant.mp3` — style `hesitant`: "Umm... yes, I think I... umm... posted something once. It was... umm... a photo, I think, from... umm... a trip."
6. `q50_short.mp3` — style `short`: "Yes, once."
7. `q50_long_rambling.mp3` — style `long_rambling`: "Yes, I have posted quite a few things actually, mostly photos from family trips or sometimes pictures of food that looks really nice, and once I even posted a short video of me playing badminton with my friends, and I remember I was really happy when a lot of people liked it and left nice comments."
8. `q50_grammar_errors.mp3` — style `grammar_errors`: "Yes, I posts sometimes. I posting photo from trip. My friend is liking it very much."
9. `q50_filler_only.mp3` — style `filler_only`: "Umm... uh... maybe... I don't remember... uh..."
10. `q50_borderline.mp3` — style `borderline`: "Yes, I posted a photo once. It was from a family trip."

## Câu 51 (question_id=51) — "Do you think you spend too much time on social media?"

1. `q51_excellent.mp3` — style `excellent`: "Honestly, I think I do spend a bit too much time on social media, especially in the evenings. I'm trying to limit it now because I want to focus more on studying and spending time with my family instead."
2. `q51_good_simple.mp3` — style `good_simple`: "Yes, maybe a little too much. I use my phone at night. I want to use it less and study more."
3. `q51_off_topic_full.mp3` — style `off_topic_full`: "I really like eating fried chicken and noodles for dinner. My favourite restaurant is near my house, and I go there with my family every month."
4. `q51_off_topic_partial.mp3` — style `off_topic_partial`: "I think I spend too much time on it sometimes... actually, speaking of time, I need to leave early tomorrow because the bus is often late in the morning."
5. `q51_hesitant.mp3` — style `hesitant`: "Umm... maybe? I think... umm... sometimes I use it too much, like... umm... at night, I think."
6. `q51_short.mp3` — style `short`: "Yes, too much."
7. `q51_long_rambling.mp3` — style `long_rambling`: "That's a really good question and honestly I have to admit that yes, I probably do spend too much time on social media, especially at night before I go to sleep, I just keep scrolling and scrolling even when I know I should be sleeping or studying, and sometimes my parents get worried about it and tell me to put my phone away, which I think is fair."
8. `q51_grammar_errors.mp3` — style `grammar_errors`: "Yes, I think I spends too much time. I uses phone every night and is hard to stop."
9. `q51_filler_only.mp3` — style `filler_only`: "Uh... umm... maybe... I don't know... umm..."
10. `q51_borderline.mp3` — style `borderline`: "Yes, a little too much. I use my phone too much at night."

## Câu 52 (question_id=52) — "Do you enjoy reading books?"

1. `q52_excellent.mp3` — style `excellent`: "Yes, I really enjoy reading books, especially adventure stories and science fiction. Reading helps me relax and also improves my vocabulary, so I try to read at least a few pages every night before bed."
2. `q52_good_simple.mp3` — style `good_simple`: "Yes, I like reading books. I read adventure stories. It is fun and I learn new words."
3. `q52_off_topic_full.mp3` — style `off_topic_full`: "My family and I usually go to my grandmother's house on the weekend. We have lunch together and sometimes help her in the garden."
4. `q52_off_topic_partial.mp3` — style `off_topic_partial`: "Yes, I enjoy reading... actually, I just remembered I left my library card at home, I hope I can still borrow a book tomorrow."
5. `q52_hesitant.mp3` — style `hesitant`: "Umm... yes, I think so. I like... umm... reading, sometimes, like... umm... adventure books, I guess."
6. `q52_short.mp3` — style `short`: "Yes, adventure books."
7. `q52_long_rambling.mp3` — style `long_rambling`: "Yes, I really do enjoy reading, I think it started when I was much younger and my parents used to read stories to me before bed, and now I like reading on my own, mostly adventure and science fiction books, and sometimes I even stay up a bit late just to finish a chapter because the story gets too exciting to stop."
8. `q52_grammar_errors.mp3` — style `grammar_errors`: "Yes, I enjoys reading book. I reads adventure story and it make me happy very much."
9. `q52_filler_only.mp3` — style `filler_only`: "Umm... uh... I'm not sure... umm..."
10. `q52_borderline.mp3` — style `borderline`: "Yes, I like reading adventure books. It is fun."

## Câu 53 (question_id=53) — "Do you like listening to music?"

1. `q53_excellent.mp3` — style `excellent`: "Yes, I love listening to music, especially pop songs in English. It helps me feel relaxed after school, and I think it also helps me improve my listening and pronunciation skills at the same time."
2. `q53_good_simple.mp3` — style `good_simple`: "Yes, I like music. I listen to pop songs. It makes me feel happy and relaxed."
3. `q53_off_topic_full.mp3` — style `off_topic_full`: "I have a small dog at home. Every afternoon after school, I take him for a walk around the neighbourhood before I start my homework."
4. `q53_off_topic_partial.mp3` — style `off_topic_partial`: "Yes, I like listening to music... actually, that reminds me, I need to charge my phone tonight because the battery is almost empty."
5. `q53_hesitant.mp3` — style `hesitant`: "Umm... yes, I... umm... like music, I think. Especially... umm... pop songs, I guess."
6. `q53_short.mp3` — style `short`: "Yes, pop songs."
7. `q53_long_rambling.mp3` — style `long_rambling`: "Yes, I really love listening to music, I listen to it almost every day, especially pop songs in English, and sometimes I even try to sing along even though my pronunciation isn't always perfect, and I also think it helps me relax after a stressful day at school with a lot of homework and tests."
8. `q53_grammar_errors.mp3` — style `grammar_errors`: "Yes, I likes music very much. I listening pop song every day and it make me happy."
9. `q53_filler_only.mp3` — style `filler_only`: "Uh... umm... maybe... I don't know... uh..."
10. `q53_borderline.mp3` — style `borderline`: "Yes, I like pop music. It makes me relaxed."

## Câu 54 (question_id=54) — "Are you into playing sports?"

1. `q54_excellent.mp3` — style `excellent`: "Yes, I'm really into sports, especially badminton and football. I usually play with my friends after school, and I think playing sports helps me stay fit and also makes me feel more confident."
2. `q54_good_simple.mp3` — style `good_simple`: "Yes, I like sports. I play badminton and football. I play with my friends."
3. `q54_off_topic_full.mp3` — style `off_topic_full`: "Last weekend, my family and I watched a movie together at home. It was a comedy film and we all laughed a lot during the movie."
4. `q54_off_topic_partial.mp3` — style `off_topic_partial`: "Yes, I'm into sports... actually, speaking of that, have you tried the new noodle shop that just opened near school? It's really popular now."
5. `q54_hesitant.mp3` — style `hesitant`: "Umm... yes, I think so. I like... umm... sports, especially... umm... badminton, I guess."
6. `q54_short.mp3` — style `short`: "Yes, badminton and football."
7. `q54_long_rambling.mp3` — style `long_rambling`: "Yes, definitely, I'm really into sports, especially badminton which I play almost every week with my friends, and I also enjoy football on weekends, and sometimes I even watch sports matches on TV with my father, and honestly I think sports are one of the most important parts of my week because they help me relax and stay healthy."
8. `q54_grammar_errors.mp3` — style `grammar_errors`: "Yes, I is into sport. I plays badminton and football with my friend and is very fun."
9. `q54_filler_only.mp3` — style `filler_only`: "Umm... uh... I don't know... maybe... uh..."
10. `q54_borderline.mp3` — style `borderline`: "Yes, I like badminton and football. I play with my friends."

---

## Sau khi Gemini tạo xong

1. Chủ động tải file `.zip` về máy từ cuộc trò chuyện với Gemini (không cần
   Gemini tự đẩy đi đâu cả).
2. Giải nén, upload TỪNG file audio qua `POST /api/media/upload` (module
   `REVIEW_VIDEO_SUBMISSION`) trên staging, lấy URL trả về.
3. Gửi lại danh sách URL kèm tên file cho tôi (hoặc dán thẳng, tôi tự khớp
   theo tên file) — tôi sẽ đưa vào `REFLEX_AUDIO_URLS`/logic script để chạy
   đúng dạng cho đúng câu hỏi thay vì xoay vòng chung 1 danh sách như hiện
   tại.
