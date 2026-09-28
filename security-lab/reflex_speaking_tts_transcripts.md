# Transcript cho AI Text-to-Speech — test chấm Speaking Reflex (assignment_id=14)

Dùng để tạo audio bằng công cụ TTS bất kỳ (Google/Azure/ElevenLabs...), sau đó
upload qua `POST /api/media/upload` (module `REVIEW_VIDEO_SUBMISSION`), lấy
URL đưa vào `REFLEX_AUDIO_URLS` của `loadtest_reflex_writing_speaking.py`.

## 10 dạng cố định — nhắm thẳng vào rubric Speaking thật

Rubric Speaking (`speaking-rubric-*.md`) chỉ chấm: **Ngữ pháp, Phát âm, Giao
tiếp tương tác, (Discourse Management/Fluency tuỳ khối)**. 10 dạng dưới đây
map trực tiếp vào từng tiêu chí + đúng lỗ hổng "lạc đề vẫn điểm cao" mà team
đã tự phát hiện (V147) và vá 1 phần bằng system prompt riêng — quan trọng
nhất là dạng `off_topic_full`/`off_topic_partial` để kiểm tra bản vá đó còn
hiệu quả không.

| # | Mã dạng | Mục đích |
|---|---|---|
| 1 | `excellent` | Trả lời tốt, đúng chủ đề, từ vựng phong phú, câu phức — kỳ vọng điểm cao nhất |
| 2 | `good_simple` | Đúng chủ đề, câu đơn giản nhưng đúng ngữ pháp — kỳ vọng vừa đạt ngưỡng 70% |
| 3 | `off_topic_full` | Hoàn toàn lạc đề (nói chuyện khác hẳn), ngữ pháp/từ vựng vẫn tốt — **test quan trọng nhất** |
| 4 | `off_topic_partial` | Bắt đầu đúng chủ đề rồi lái sang chuyện khác hẳn giữa chừng |
| 5 | `hesitant` | Nhiều "umm", "uh", ngắt quãng, lặp từ, câu dang dở |
| 6 | `short` | Chỉ 1 câu rất ngắn, gần như không đủ ý |
| 7 | `long_rambling` | Nói dài, lặp ý, lan man, vượt hẳn `max_recording_seconds=20s` |
| 8 | `grammar_errors` | Đúng chủ đề nhưng cố tình sai ngữ pháp rõ rệt (thì, chia động từ, số ít/nhiều) |
| 9 | `filler_only` | Gần như không nói được gì, chỉ ậm ừ — test ngưỡng tối thiểu / khả năng bị từ chối |
| 10 | `borderline` | Đúng chủ đề, đủ ý tối thiểu, ngắn gọn, ngữ pháp đúng — test sát ngưỡng 70% |

**Lưu ý về giới hạn của TTS:** không mô phỏng được "phát âm kém" (giọng đọc
máy luôn phát âm chuẩn) — nếu muốn test tiêu chí Phát âm thấp, cách duy nhất
là 1 người thật cố tình đọc sai âm đuôi/trọng âm, TTS không làm được việc
này một cách đáng tin cậy.

Off-topic dùng chung 1 trong 3 chủ đề trung lập xoay vòng (thời tiết/bữa sáng/
bài tập về nhà) — không liên quan tới hobby/gia đình/mạng xã hội/đọc sách/âm
nhạc/thể thao (chủ đề chung của cả 9 câu hỏi thật).

---

## Câu 46 — "Do you have any hobbies?"

1. **excellent**: "Yes, I do. My favourite hobby is playing badminton. I usually play it with my friends after school, twice a week. I really enjoy it because it helps me stay healthy and it's a great way to relax after a long day of studying."
2. **good_simple**: "Yes, I have a hobby. I like playing badminton with my friends. It is fun and I feel happy when I play it."
3. **off_topic_full**: "This morning I had a bowl of noodles and a glass of milk for breakfast. My mother cooked it before I went to school, and it was really delicious."
4. **off_topic_partial**: "Yes, I do have hobbies... actually, speaking of that, did you know it might rain this afternoon? The sky looks really dark and I forgot my umbrella at home."
5. **hesitant**: "Umm... yes, I... I think I have a hobby. It's like... umm... playing badminton? I play it... sometimes, with my... umm... friends."
6. **short**: "Yes, badminton."
7. **long_rambling**: "Yes, I have many hobbies, actually so many that I don't even know where to start, but I guess the main one is badminton, although sometimes I also like drawing, and sometimes I like watching videos online, and my friends also have hobbies like football and swimming, and we all talk about our hobbies during break time at school every single day."
8. **grammar_errors**: "Yes, I have. I is like playing badminton with my friend. She play very good and we is happy when we playing together."
9. **filler_only**: "Umm... uh... I don't know... umm... maybe... uh..."
10. **borderline**: "Yes, I like playing badminton. I play with my friends after school."

## Câu 47 — "Do you have the same hobbies as your family members?"

1. **excellent**: "Not exactly. My father enjoys fishing on weekends, while my mother prefers gardening. I don't really share those hobbies, but we all enjoy watching movies together as a family every Sunday evening."
2. **good_simple**: "No, we are different. My father likes fishing. I like playing badminton. But we watch movies together sometimes."
3. **off_topic_full**: "I still have a lot of homework to finish tonight. My teacher gave us three exercises in Maths and one essay in English, so I need to start soon."
4. **off_topic_partial**: "My family members have different hobbies from me... by the way, I heard the weather will be very hot this weekend, so we might not go outside much."
5. **hesitant**: "Umm... my family... they have... umm... different hobbies, I think? My dad likes... umm... fishing, and I... umm... don't really like it."
6. **short**: "No, we are different."
7. **long_rambling**: "Well, that's a really interesting question because my whole family has so many different hobbies, my dad likes fishing, my mum likes gardening, my older brother likes playing video games all day, my little sister likes drawing, and honestly I don't think anyone in my family shares the exact same hobby as me, which is a bit funny when I think about it."
8. **grammar_errors**: "No, my family is have different hobby. My father like fishing but I doesn't like it. We is different."
9. **filler_only**: "Uh... umm... my family... umm... I'm not sure... uh..."
10. **borderline**: "No, my father likes fishing. I like badminton. We are different."

## Câu 48 — "Are there any differences between the activities you liked when you were a child and those you like now?"

1. **excellent**: "Yes, definitely. When I was a child, I loved playing with toy cars and building blocks. Nowadays, I prefer more active hobbies like playing badminton and reading books, because I've grown up and my interests have changed a lot."
2. **good_simple**: "Yes, there are differences. When I was small, I liked toys. Now I like sports and reading. My interest changed."
3. **off_topic_full**: "I usually go to bed at ten o'clock on school nights. My mother always reminds me to sleep early so I can wake up on time for school the next morning."
4. **off_topic_partial**: "When I was a child I liked different things... actually, I just remembered I need to buy a new pair of shoes because mine are too small now."
5. **hesitant**: "Umm... yes, I think so? When I was... umm... little, I liked... umm... toys, and now I... umm... like different things, like sports."
6. **short**: "Yes, I liked toys before, sports now."
7. **long_rambling**: "Oh yes, there are so many differences, when I was really young I used to love playing with building blocks and toy cars for hours, and I also liked watching cartoons every single day, but now that I'm older I've become more interested in sports like badminton, and I also enjoy reading books, and sometimes I even like studying new subjects which is something I never expected when I was a child."
8. **grammar_errors**: "Yes, is have differences. When I is child I liked toy. Now I likes sport and reading more better."
9. **filler_only**: "Umm... uh... I... umm... don't remember... uh..."
10. **borderline**: "Yes. I liked toys before. Now I like sports and reading."

## Câu 49 — "Do you usually spend your days off with your parents or with your friends?"

1. **excellent**: "I usually spend my days off with my friends. We often go to the park to play badminton or just hang out and chat. However, I also try to spend some time with my parents, especially having dinner together on Sunday evenings."
2. **good_simple**: "I spend my days off with my friends. We play badminton together. Sometimes I stay home with my parents too."
3. **off_topic_full**: "I really enjoy listening to music while doing my homework. It helps me concentrate better, especially soft music without too many lyrics."
4. **off_topic_partial**: "On my days off, I usually... actually, have you tried the new bubble tea shop near school? My friends and I went there last week and it was really good."
5. **hesitant**: "Umm... I usually spend it with... umm... my friends, I think. We... umm... go out sometimes, and... umm... my parents too, sometimes."
6. **short**: "With my friends, mostly."
7. **long_rambling**: "That's a good question, I think it really depends on the week, sometimes I spend my whole weekend with my friends going to the park or playing badminton or just talking about school, but other weekends I stay home with my parents and we watch movies or go out for dinner, so honestly it's a mix of both and I can't really choose just one."
8. **grammar_errors**: "I usually spends day off with my friend. We is play badminton. My parents also I spend time sometimes."
9. **filler_only**: "Uh... umm... I'm not sure... umm... maybe both... uh..."
10. **borderline**: "I usually spend days off with my friends. We play badminton together."

## Câu 50 — "Have you ever posted anything on social media?"

1. **excellent**: "Yes, I have. I sometimes post photos of my badminton matches or pictures from family trips on social media. I enjoy sharing happy moments with my friends and seeing their reactions."
2. **good_simple**: "Yes, I have posted photos before. I posted pictures from my trip with my family. My friends liked them."
3. **off_topic_full**: "My favourite subject at school is English because my teacher makes the lessons very interesting and fun with a lot of games and group activities."
4. **off_topic_partial**: "Yes, I've posted before... actually, that reminds me, I need to bring my sports shoes tomorrow because we have a P.E. lesson in the morning."
5. **hesitant**: "Umm... yes, I think I... umm... posted something once. It was... umm... a photo, I think, from... umm... a trip."
6. **short**: "Yes, once."
7. **long_rambling**: "Yes, I have posted quite a few things actually, mostly photos from family trips or sometimes pictures of food that looks really nice, and once I even posted a short video of me playing badminton with my friends, and I remember I was really happy when a lot of people liked it and left nice comments."
8. **grammar_errors**: "Yes, I posts sometimes. I posting photo from trip. My friend is liking it very much."
9. **filler_only**: "Umm... uh... maybe... I don't remember... uh..."
10. **borderline**: "Yes, I posted a photo once. It was from a family trip."

## Câu 51 — "Do you think you spend too much time on social media?"

1. **excellent**: "Honestly, I think I do spend a bit too much time on social media, especially in the evenings. I'm trying to limit it now because I want to focus more on studying and spending time with my family instead."
2. **good_simple**: "Yes, maybe a little too much. I use my phone at night. I want to use it less and study more."
3. **off_topic_full**: "I really like eating fried chicken and noodles for dinner. My favourite restaurant is near my house, and I go there with my family every month."
4. **off_topic_partial**: "I think I spend too much time on it sometimes... actually, speaking of time, I need to leave early tomorrow because the bus is often late in the morning."
5. **hesitant**: "Umm... maybe? I think... umm... sometimes I use it too much, like... umm... at night, I think."
6. **short**: "Yes, too much."
7. **long_rambling**: "That's a really good question and honestly I have to admit that yes, I probably do spend too much time on social media, especially at night before I go to sleep, I just keep scrolling and scrolling even when I know I should be sleeping or studying, and sometimes my parents get worried about it and tell me to put my phone away, which I think is fair."
8. **grammar_errors**: "Yes, I think I spends too much time. I uses phone every night and is hard to stop."
9. **filler_only**: "Uh... umm... maybe... I don't know... umm..."
10. **borderline**: "Yes, a little too much. I use my phone too much at night."

## Câu 52 — "Do you enjoy reading books?"

1. **excellent**: "Yes, I really enjoy reading books, especially adventure stories and science fiction. Reading helps me relax and also improves my vocabulary, so I try to read at least a few pages every night before bed."
2. **good_simple**: "Yes, I like reading books. I read adventure stories. It is fun and I learn new words."
3. **off_topic_full**: "My family and I usually go to my grandmother's house on the weekend. We have lunch together and sometimes help her in the garden."
4. **off_topic_partial**: "Yes, I enjoy reading... actually, I just remembered I left my library card at home, I hope I can still borrow a book tomorrow."
5. **hesitant**: "Umm... yes, I think so. I like... umm... reading, sometimes, like... umm... adventure books, I guess."
6. **short**: "Yes, adventure books."
7. **long_rambling**: "Yes, I really do enjoy reading, I think it started when I was much younger and my parents used to read stories to me before bed, and now I like reading on my own, mostly adventure and science fiction books, and sometimes I even stay up a bit late just to finish a chapter because the story gets too exciting to stop."
8. **grammar_errors**: "Yes, I enjoys reading book. I reads adventure story and it make me happy very much."
9. **filler_only**: "Umm... uh... I'm not sure... umm..."
10. **borderline**: "Yes, I like reading adventure books. It is fun."

## Câu 53 — "Do you like listening to music?"

1. **excellent**: "Yes, I love listening to music, especially pop songs in English. It helps me feel relaxed after school, and I think it also helps me improve my listening and pronunciation skills at the same time."
2. **good_simple**: "Yes, I like music. I listen to pop songs. It makes me feel happy and relaxed."
3. **off_topic_full**: "I have a small dog at home. Every afternoon after school, I take him for a walk around the neighbourhood before I start my homework."
4. **off_topic_partial**: "Yes, I like listening to music... actually, that reminds me, I need to charge my phone tonight because the battery is almost empty."
5. **hesitant**: "Umm... yes, I... umm... like music, I think. Especially... umm... pop songs, I guess."
6. **short**: "Yes, pop songs."
7. **long_rambling**: "Yes, I really love listening to music, I listen to it almost every day, especially pop songs in English, and sometimes I even try to sing along even though my pronunciation isn't always perfect, and I also think it helps me relax after a stressful day at school with a lot of homework and tests."
8. **grammar_errors**: "Yes, I likes music very much. I listening pop song every day and it make me happy."
9. **filler_only**: "Uh... umm... maybe... I don't know... uh..."
10. **borderline**: "Yes, I like pop music. It makes me relaxed."

## Câu 54 — "Are you into playing sports?"

1. **excellent**: "Yes, I'm really into sports, especially badminton and football. I usually play with my friends after school, and I think playing sports helps me stay fit and also makes me feel more confident."
2. **good_simple**: "Yes, I like sports. I play badminton and football. I play with my friends."
3. **off_topic_full**: "Last weekend, my family and I watched a movie together at home. It was a comedy film and we all laughed a lot during the movie."
4. **off_topic_partial**: "Yes, I'm into sports... actually, speaking of that, have you tried the new noodle shop that just opened near school? It's really popular now."
5. **hesitant**: "Umm... yes, I think so. I like... umm... sports, especially... umm... badminton, I guess."
6. **short**: "Yes, badminton and football."
7. **long_rambling**: "Yes, definitely, I'm really into sports, especially badminton which I play almost every week with my friends, and I also enjoy football on weekends, and sometimes I even watch sports matches on TV with my father, and honestly I think sports are one of the most important parts of my week because they help me relax and stay healthy."
8. **grammar_errors**: "Yes, I is into sport. I plays badminton and football with my friend and is very fun."
9. **filler_only**: "Umm... uh... I don't know... maybe... uh..."
10. **borderline**: "Yes, I like badminton and football. I play with my friends."

---

## Gợi ý dùng khi tạo audio bằng TTS

- Chọn giọng đọc tự nhiên, tốc độ vừa phải (không quá nhanh) — hầu hết TTS
  hiện đại (Google Cloud TTS, Azure Speech, ElevenLabs) đọc được dấu "..."
  như 1 khoảng ngừng ngắn, phù hợp cho dạng `hesitant`/`filler_only`.
- Dạng `long_rambling` cố tình dài hơn `max_recording_seconds=20s` của các
  câu hỏi này — kiểm tra xem hệ thống có chặn/cắt hay chấm nguyên văn.
- Không bắt buộc tạo đủ cả 90 file (9 câu × 10 dạng) ngay từ đầu — có thể ưu
  tiên tạo 10 dạng cho 2-3 câu trước (VD câu 46, 50, 52) để chạy thử, xác
  nhận pipeline hoạt động đúng rồi mới tạo hết cho cả 9 câu.
