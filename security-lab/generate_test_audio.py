import asyncio
import os
import shutil
import zipfile
import edge_tts

# Danh sach day du 90 test cases tu cau 46 den cau 54
DATASET = [
    # Cau 46
    {"q": 46, "style": "excellent", "text": "Yes, I do. My favourite hobby is playing badminton. I usually play it with my friends after school, twice a week. I really enjoy it because it helps me stay healthy and it's a great way to relax after a long day of studying."},
    {"q": 46, "style": "good_simple", "text": "Yes, I have a hobby. I like playing badminton with my friends. It is fun and I feel happy when I play it."},
    {"q": 46, "style": "off_topic_full", "text": "This morning I had a bowl of noodles and a glass of milk for breakfast. My mother cooked it before I went to school, and it was really delicious."},
    {"q": 46, "style": "off_topic_partial", "text": "Yes, I do have hobbies... actually, speaking of that, did you know it might rain this afternoon? The sky looks really dark and I forgot my umbrella at home."},
    {"q": 46, "style": "hesitant", "text": "Umm... yes, I... I think I have a hobby. It's like... umm... playing badminton? I play it... sometimes, with my... umm... friends."},
    {"q": 46, "style": "short", "text": "Yes, badminton."},
    {"q": 46, "style": "long_rambling", "text": "Yes, I have many hobbies, actually so many that I don't even know where to start, but I guess the main one is badminton, although sometimes I also like drawing, and sometimes I like watching videos online, and my friends also have hobbies like football and swimming, and we all talk about our hobbies during break time at school every single day."},
    {"q": 46, "style": "grammar_errors", "text": "Yes, I have. I is like playing badminton with my friend. She play very good and we is happy when we playing together."},
    {"q": 46, "style": "filler_only", "text": "Umm... uh... I don't know... umm... maybe... uh..."},
    {"q": 46, "style": "borderline", "text": "Yes, I like playing badminton. I play with my friends after school."},

    # Cau 47
    {"q": 47, "style": "excellent", "text": "Not exactly. My father enjoys fishing on weekends, while my mother prefers gardening. I don't really share those hobbies, but we all enjoy watching movies together as a family every Sunday evening."},
    {"q": 47, "style": "good_simple", "text": "No, we are different. My father likes fishing. I like playing badminton. But we watch movies together sometimes."},
    {"q": 47, "style": "off_topic_full", "text": "I still have a lot of homework to finish tonight. My teacher gave us three exercises in Maths and one essay in English, so I need to start soon."},
    {"q": 47, "style": "off_topic_partial", "text": "My family members have different hobbies from me... by the way, I heard the weather will be very hot this weekend, so we might not go outside much."},
    {"q": 47, "style": "hesitant", "text": "Umm... my family... they have... umm... different hobbies, I think? My dad likes... umm... fishing, and I... umm... don't really like it."},
    {"q": 47, "style": "short", "text": "No, we are different."},
    {"q": 47, "style": "long_rambling", "text": "Well, that's a really interesting question because my whole family has so many different hobbies, my dad likes fishing, my mum likes gardening, my older brother likes playing video games all day, my little sister likes drawing, and honestly I don't think anyone in my family shares the exact same hobby as me, which is a bit funny when I think about it."},
    {"q": 47, "style": "grammar_errors", "text": "No, my family is have different hobby. My father like fishing but I doesn't like it. We is different."},
    {"q": 47, "style": "filler_only", "text": "Uh... umm... my family... umm... I'm not sure... uh..."},
    {"q": 47, "style": "borderline", "text": "No, my father likes fishing. I like badminton. We are different."},

    # Cau 48
    {"q": 48, "style": "excellent", "text": "Yes, definitely. When I was a child, I loved playing with toy cars and building blocks. Nowadays, I prefer more active hobbies like playing badminton and reading books, because I've grown up and my interests have changed a lot."},
    {"q": 48, "style": "good_simple", "text": "Yes, there are differences. When I was small, I liked toys. Now I like sports and reading. My interest changed."},
    {"q": 48, "style": "off_topic_full", "text": "I usually go to bed at ten o'clock on school nights. My mother always reminds me to sleep early so I can wake up on time for school the next morning."},
    {"q": 48, "style": "off_topic_partial", "text": "When I was a child I liked different things... actually, I just remembered I need to buy a new pair of shoes because mine are too small now."},
    {"q": 48, "style": "hesitant", "text": "Umm... yes, I think so? When I was... umm... little, I liked... umm... toys, and now I... umm... like different things, like sports."},
    {"q": 48, "style": "short", "text": "Yes, I liked toys before, sports now."},
    {"q": 48, "style": "long_rambling", "text": "Oh yes, there are so many differences, when I was really young I used to love playing with building blocks and toy cars for hours, and I also liked watching cartoons every single day, but now that I'm older I've become more interested in sports like badminton, and I also enjoy reading books, and sometimes I even like studying new subjects which is something I never expected when I was a child."},
    {"q": 48, "style": "grammar_errors", "text": "Yes, is have differences. When I is child I liked toy. Now I likes sport and reading more better."},
    {"q": 48, "style": "filler_only", "text": "Umm... uh... I... umm... don't remember... uh..."},
    {"q": 48, "style": "borderline", "text": "Yes. I liked toys before. Now I like sports and reading."},

    # Cau 49
    {"q": 49, "style": "excellent", "text": "I usually spend my days off with my friends. We often go to the park to play badminton or just hang out and chat. However, I also try to spend some time with my parents, especially having dinner together on Sunday evenings."},
    {"q": 49, "style": "good_simple", "text": "I spend my days off with my friends. We play badminton together. Sometimes I stay home with my parents too."},
    {"q": 49, "style": "off_topic_full", "text": "I really enjoy listening to music while doing my homework. It helps me concentrate better, especially soft music without too many lyrics."},
    {"q": 49, "style": "off_topic_partial", "text": "On my days off, I usually... actually, have you tried the new bubble tea shop near school? My friends and I went there last week and it was really good."},
    {"q": 49, "style": "hesitant", "text": "Umm... I usually spend it with... umm... my friends, I think. We... umm... go out sometimes, and... umm... my parents too, sometimes."},
    {"q": 49, "style": "short", "text": "With my friends, mostly."},
    {"q": 49, "style": "long_rambling", "text": "That's a good question, I think it really depends on the week, sometimes I spend my whole weekend with my friends going to the park or playing badminton or just talking about school, but other weekends I stay home with my parents and we watch movies or go out for dinner, so honestly it's a mix of both and I can't really choose just one."},
    {"q": 49, "style": "grammar_errors", "text": "I usually spends day off with my friend. We is play badminton. My parents also I spend time sometimes."},
    {"q": 49, "style": "filler_only", "text": "Uh... umm... I'm not sure... umm... maybe both... uh..."},
    {"q": 49, "style": "borderline", "text": "I usually spend days off with my friends. We play badminton together."},

    # Cau 50
    {"q": 50, "style": "excellent", "text": "Yes, I have. I sometimes post photos of my badminton matches or pictures from family trips on social media. I enjoy sharing happy moments with my friends and seeing their reactions."},
    {"q": 50, "style": "good_simple", "text": "Yes, I have posted photos before. I posted pictures from my trip with my family. My friends liked them."},
    {"q": 50, "style": "off_topic_full", "text": "My favourite subject at school is English because my teacher makes the lessons very interesting and fun with a lot of games and group activities."},
    {"q": 50, "style": "off_topic_partial", "text": "Yes, I've posted before... actually, that reminds me, I need to bring my sports shoes tomorrow because we have a P.E. lesson in the morning."},
    {"q": 50, "style": "hesitant", "text": "Umm... yes, I think I... umm... posted something once. It was... umm... a photo, I think, from... umm... a trip."},
    {"q": 50, "style": "short", "text": "Yes, once."},
    {"q": 50, "style": "long_rambling", "text": "Yes, I have posted quite a few things actually, mostly photos from family trips or sometimes pictures of food that looks really nice, and once I even posted a short video of me playing badminton with my friends, and I remember I was really happy when a lot of people liked it and left nice comments."},
    {"q": 50, "style": "grammar_errors", "text": "Yes, I posts sometimes. I posting photo from trip. My friend is liking it very much."},
    {"q": 50, "style": "filler_only", "text": "Umm... uh... maybe... I don't remember... uh..."},
    {"q": 50, "style": "borderline", "text": "Yes, I posted a photo once. It was from a family trip."},

    # Cau 51
    {"q": 51, "style": "excellent", "text": "Honestly, I think I do spend a bit too much time on social media, especially in the evenings. I'm trying to limit it now because I want to focus more on studying and spending time with my family instead."},
    {"q": 51, "style": "good_simple", "text": "Yes, maybe a little too much. I use my phone at night. I want to use it less and study more."},
    {"q": 51, "style": "off_topic_full", "text": "I really like eating fried chicken and noodles for dinner. My favourite restaurant is near my house, and I go there with my family every month."},
    {"q": 51, "style": "off_topic_partial", "text": "I think I spend too much time on it sometimes... actually, speaking of time, I need to leave early tomorrow because the bus is often late in the morning."},
    {"q": 51, "style": "hesitant", "text": "Umm... maybe? I think... umm... sometimes I use it too much, like... umm... at night, I think."},
    {"q": 51, "style": "short", "text": "Yes, too much."},
    {"q": 51, "style": "long_rambling", "text": "That's a really good question and honestly I have to admit that yes, I probably do spend too much time on social media, especially at night before I go to sleep, I just keep scrolling and scrolling even when I know I should be sleeping or studying, and sometimes my parents get worried about it and tell me to put my phone away, which I think is fair."},
    {"q": 51, "style": "grammar_errors", "text": "Yes, I think I spends too much time. I uses phone every night and is hard to stop."},
    {"q": 51, "style": "filler_only", "text": "Uh... umm... maybe... I don't know... umm..."},
    {"q": 51, "style": "borderline", "text": "Yes, a little too much. I use my phone too much at night."},

    # Cau 52
    {"q": 52, "style": "excellent", "text": "Yes, I really enjoy reading books, especially adventure stories and science fiction. Reading helps me relax and also improves my vocabulary, so I try to read at least a few pages every night before bed."},
    {"q": 52, "style": "good_simple", "text": "Yes, I like reading books. I read adventure stories. It is fun and I learn new words."},
    {"q": 52, "style": "off_topic_full", "text": "My family and I usually go to my grandmother's house on the weekend. We have lunch together and sometimes help her in the garden."},
    {"q": 52, "style": "off_topic_partial", "text": "Yes, I enjoy reading... actually, I just remembered I left my library card at home, I hope I can still borrow a book tomorrow."},
    {"q": 52, "style": "hesitant", "text": "Umm... yes, I think so. I like... umm... reading, sometimes, like... umm... adventure books, I guess."},
    {"q": 52, "style": "short", "text": "Yes, adventure books."},
    {"q": 52, "style": "long_rambling", "text": "Yes, I really do enjoy reading, I think it started when I was much younger and my parents used to read stories to me before bed, and now I like reading on my own, mostly adventure and science fiction books, and sometimes I even stay up a bit late just to finish a chapter because the story gets too exciting to stop."},
    {"q": 52, "style": "grammar_errors", "text": "Yes, I enjoys reading book. I reads adventure story and it make me happy very much."},
    {"q": 52, "style": "filler_only", "text": "Umm... uh... I'm not sure... umm..."},
    {"q": 52, "style": "borderline", "text": "Yes, I like reading adventure books. It is fun."},

    # Cau 53
    {"q": 53, "style": "excellent", "text": "Yes, I love listening to music, especially pop songs in English. It helps me feel relaxed after school, and I think it also helps me improve my listening and pronunciation skills at the same time."},
    {"q": 53, "style": "good_simple", "text": "Yes, I like music. I listen to pop songs. It makes me feel happy and relaxed."},
    {"q": 53, "style": "off_topic_full", "text": "I have a small dog at home. Every afternoon after school, I take him for a walk around the neighbourhood before I start my homework."},
    {"q": 53, "style": "off_topic_partial", "text": "Yes, I like listening to music... actually, that reminds me, I need to charge my phone tonight because the battery is almost empty."},
    {"q": 53, "style": "hesitant", "text": "Umm... yes, I... umm... like music, I think. Especially... umm... pop songs, I guess."},
    {"q": 53, "style": "short", "text": "Yes, pop songs."},
    {"q": 53, "style": "long_rambling", "text": "Yes, I really love listening to music, I listen to it almost every day, especially pop songs in English, and sometimes I even try to sing along even though my pronunciation isn't always perfect, and I also think it helps me relax after a stressful day at school with a lot of homework and tests."},
    {"q": 53, "style": "grammar_errors", "text": "Yes, I likes music very much. I listening pop song every day and it make me happy."},
    {"q": 53, "style": "filler_only", "text": "Uh... umm... maybe... I don't know... uh..."},
    {"q": 53, "style": "borderline", "text": "Yes, I like pop music. It makes me relaxed."},

    # Cau 54
    {"q": 54, "style": "excellent", "text": "Yes, I'm really into sports, especially badminton and football. I usually play with my friends after school, and I think playing sports helps me stay fit and also makes me feel more confident."},
    {"q": 54, "style": "good_simple", "text": "Yes, I like sports. I play badminton and football. I play with my friends."},
    {"q": 54, "style": "off_topic_full", "text": "Last weekend, my family and I watched a movie together at home. It was a comedy film and we all laughed a lot during the movie."},
    {"q": 54, "style": "off_topic_partial", "text": "Yes, I'm into sports... actually, speaking of that, have you tried the new noodle shop that just opened near school? It's really popular now."},
    {"q": 54, "style": "hesitant", "text": "Umm... yes, I think so. I like... umm... sports, especially... umm... badminton, I guess."},
    {"q": 54, "style": "short", "text": "Yes, badminton and football."},
    {"q": 54, "style": "long_rambling", "text": "Yes, definitely, I'm really into sports, especially badminton which I play almost every week with my friends, and I also enjoy football on weekends, and sometimes I even watch sports matches on TV with my father, and honestly I think sports are one of the most important parts of my week because they help me relax and stay healthy."},
    {"q": 54, "style": "grammar_errors", "text": "Yes, I is into sport. I plays badminton and football with my friend and is very fun."},
    {"q": 54, "style": "filler_only", "text": "Umm... uh... I don't know... maybe... uh..."},
    {"q": 54, "style": "borderline", "text": "Yes, I like badminton and football. I play with my friends."},
]

BASE_DIR = "reflex_audio_temp"
ZIP_NAME = "reflex_speaking_audio.zip"


def get_style_params(style: str):
    # Cau hinh rate, pitch mo phong hoc sinh 12-13 tuoi
    pitch = "+12Hz"
    rate = "+0%"

    if style in ["hesitant", "filler_only"]:
        rate = "-22%"
    elif style == "long_rambling":
        rate = "+25%"
    elif style == "short":
        rate = "+10%"
    elif style in ["excellent", "off_topic_full", "grammar_errors", "good_simple", "borderline"]:
        rate = "+2%"

    return rate, pitch


async def generate_file(item: dict, semaphore: asyncio.Semaphore, max_retries: int = 5):
    async with semaphore:
        q_id = item["q"]
        style = item["style"]
        text = item["text"]

        folder = os.path.join(BASE_DIR, f"q{q_id}")
        os.makedirs(folder, exist_ok=True)
        file_name = f"q{q_id}_{style}.mp3"
        file_path = os.path.join(folder, file_name)

        if os.path.exists(file_path) and os.path.getsize(file_path) > 0:
            print(f"[Skip - da co] {file_name}")
            return

        rate, pitch = get_style_params(style)

        # Xen ke giong nu (HoaiMy) va giong nam (NamMinh) theo so cau de da dang mau
        voice = "vi-VN-HoaiMyNeural" if q_id % 2 == 0 else "vi-VN-NamMinhNeural"

        # Thay the dau ba cham bang dau phay ngat quang de TTS doc dung nhip ngap ngung
        processed_text = text.replace("...", ", ")

        last_error = None
        for attempt in range(1, max_retries + 1):
            try:
                communicate = edge_tts.Communicate(text=processed_text, voice=voice, rate=rate, pitch=pitch)
                await communicate.save(file_path)
                print(f"[Done] {file_name}")
                return
            except Exception as e:  # noqa: BLE001 -- dich vu TTS mien phi hay loi mang thoang qua, thu lai
                last_error = e
                wait_seconds = 2 * attempt
                print(f"[Retry {attempt}/{max_retries}] {file_name} loi ({e}) -- cho {wait_seconds}s roi thu lai.")
                if os.path.exists(file_path):
                    os.remove(file_path)  # xoa file rong/loi truoc khi thu lai
                await asyncio.sleep(wait_seconds)

        print(f"[FAILED sau {max_retries} lan] {file_name}: {last_error}")


async def main():
    if os.path.exists(BASE_DIR):
        shutil.rmtree(BASE_DIR)
    os.makedirs(BASE_DIR, exist_ok=True)

    semaphore = asyncio.Semaphore(3)  # giam tu 5 -> 3 de bot ap luc len dich vu TTS mien phi, giam ty le loi
    tasks = [generate_file(item, semaphore) for item in DATASET]

    print("Bat dau sinh 90 file audio...")
    await asyncio.gather(*tasks)

    print("\nDang dong goi thanh reflex_speaking_audio.zip...")
    with zipfile.ZipFile(ZIP_NAME, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(BASE_DIR):
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, BASE_DIR)
                zipf.write(full_path, rel_path)

    shutil.rmtree(BASE_DIR)
    print(f"\nHoan tat! File da duoc luu tai: {os.path.abspath(ZIP_NAME)}")


if __name__ == "__main__":
    asyncio.run(main())
