package vn.com.pps.education.lms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Đường dẫn tương đối trong Excel import được ghép với public-base-url của môi trường. */
class ImportMediaUrlResolverTest {

    private static final String BASE = "https://files.ppsvietnam.edu.vn";
    private final ImportMediaUrlResolver resolver = new ImportMediaUrlResolver(BASE + "/");

    @Test
    void absoluteUrlIsKeptAsIs() {
        String url = "https://pub-old.r2.dev/lms/questions/images/a.png";
        assertEquals(url, resolver.resolveImage(url));
        assertEquals("http://example.com/a.mp3", resolver.resolveAudio("http://example.com/a.mp3"));
    }

    @Test
    void blankAndNullAreUntouched() {
        assertNull(resolver.resolveImage(null));
        assertEquals("", resolver.resolveImage(""));
        assertEquals("  ", resolver.resolveAudio("  "));
    }

    @Test
    void fullKeyIsPrefixedWithBaseUrl() {
        assertEquals(BASE + "/lms/questions/images/a.png", resolver.resolveImage("lms/questions/images/a.png"));
        assertEquals(BASE + "/lms/questions/images/a.png", resolver.resolveImage("/lms/questions/images/a.png"));
    }

    @Test
    void subfolderPathGetsQuestionFolder() {
        assertEquals(BASE + "/lms/questions/images/a.png", resolver.resolveImage("images/a.png"));
        assertEquals(BASE + "/lms/questions/audio/b.mp3", resolver.resolveAudio("audio/b.mp3"));
    }

    @Test
    void bareFileNameUsesColumnDefaultFolder() {
        assertEquals(BASE + "/lms/questions/images/a.png", resolver.resolveImage("a.png"));
        assertEquals(BASE + "/lms/questions/audio/b.mp3", resolver.resolveAudio("b.mp3"));
    }

    @Test
    void pipeSeparatedImagesAreResolvedIndividuallyKeepingBlanks() {
        assertEquals(BASE + "/lms/questions/images/a.png||https://x.test/c.png",
                resolver.resolveImage("a.png||https://x.test/c.png"));
        assertEquals(BASE + "/lms/questions/images/a.png|" + BASE + "/lms/questions/images/b.png",
                resolver.resolveImage(" a.png | images/b.png "));
    }
}
