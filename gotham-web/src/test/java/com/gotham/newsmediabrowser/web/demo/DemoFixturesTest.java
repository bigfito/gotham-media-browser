package com.gotham.newsmediabrowser.web.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.gotham.newsmediabrowser.common.article.ArticleStatus;
import com.gotham.newsmediabrowser.common.media.MediaType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Integrity checks for the committed static demo fixtures ({@code docs/demo/fixtures}, P9-T01):
 * the TSVs are well-formed, article bylines reference journalists that exist, and each media sample
 * is classifiable and within its media type's size limit. Runs under {@code mvn test} (no live deps).
 */
class DemoFixturesTest {

    /** Prototype upload ceilings (mirror {@code application.properties} / MediaLimitsProperties). */
    private static final Map<MediaType, Long> MAX_BYTES = Map.of(
            MediaType.IMAGE, 10L * 1024 * 1024,
            MediaType.AUDIO, 20L * 1024 * 1024,
            MediaType.VIDEO, 50L * 1024 * 1024);

    /** File extension → MIME, so the fixtures classify the same way an HTTP upload would. */
    private static final Map<String, String> EXT_MIME = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "wav", "audio/wav", "mp3", "audio/mpeg", "mp4", "video/mp4");

    private final Path fixtures = locateFixtures();

    @Test
    void journalistsTsvIsWellFormedWithUniqueEmails() {
        List<String[]> rows = readTsv(fixtures.resolve("journalists.tsv"), 4);
        assertThat(rows).isNotEmpty();

        Set<String> emails = new LinkedHashSet<>();
        for (String[] row : rows) {
            assertThat(row[0]).as("first_name").isNotBlank();
            assertThat(row[1]).as("last_name").isNotBlank();
            assertThat(row[2]).as("email").contains("@");
            assertThat(emails.add(row[2])).as("duplicate email %s", row[2]).isTrue();
        }
    }

    @Test
    void articlesTsvIsWellFormedAndBylinesResolve() {
        Set<String> journalistEmails = new LinkedHashSet<>();
        for (String[] row : readTsv(fixtures.resolve("journalists.tsv"), 4)) {
            journalistEmails.add(row[2]);
        }

        List<String[]> rows = readTsv(fixtures.resolve("articles.tsv"), 9);
        assertThat(rows).isNotEmpty();

        for (String[] row : rows) {
            String title = row[0];
            assertThat(title).as("title").isNotBlank();
            assertThat(row[1]).as("section of %s", title).isNotBlank();
            // status must be a real ArticleStatus.
            ArticleStatus.fromValue(row[3]);
            assertThat(row[7]).as("summary of %s", title).isNotBlank();
            assertThat(row[8]).as("body of %s", title).isNotBlank();

            for (String email : splitCsv(row[5])) {
                assertThat(journalistEmails).as("byline %s of '%s'", email, title).contains(email);
            }

            String media = row[6];
            if (!"-".equals(media) && !media.isBlank()) {
                assertMediaWithinLimits(media);
            }
        }
    }

    @Test
    void everyMediaFileIsClassifiableAndWithinLimits() throws IOException {
        Path mediaDir = fixtures.resolve("media");
        assertThat(Files.isDirectory(mediaDir)).isTrue();
        try (var stream = Files.list(mediaDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            assertThat(files).as("at least one media sample").isNotEmpty();
            for (Path file : files) {
                assertMediaWithinLimits(file.getFileName().toString());
            }
        }
    }

    private void assertMediaWithinLimits(String filename) {
        Path file = fixtures.resolve("media").resolve(filename);
        assertThat(Files.isRegularFile(file)).as("media file %s exists", filename).isTrue();

        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        String mime = EXT_MIME.get(ext);
        assertThat(mime).as("known media extension for %s", filename).isNotNull();
        MediaType type = MediaType.fromContentType(mime); // throws if not image/audio/video

        long size = sizeOf(file);
        assertThat(size).as("%s (%s) within %s limit", filename, type, type)
                .isPositive()
                .isLessThanOrEqualTo(MAX_BYTES.get(type));
    }

    private static List<String[]> readTsv(Path path, int expectedColumns) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read fixture " + path, e);
        }
        List<String[]> rows = new ArrayList<>();
        boolean header = true;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (header) {
                header = false;
                continue;
            }
            assertThat(cols.length).as("row has %d columns: %s", expectedColumns, line)
                    .isEqualTo(expectedColumns);
            rows.add(cols);
        }
        return rows;
    }

    private static List<String> splitCsv(String value) {
        List<String> out = new ArrayList<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                out.add(part.strip());
            }
        }
        return out;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Walks up from the module working directory to the repo root that holds docs/demo/fixtures. */
    private static Path locateFixtures() {
        Path dir = Paths.get("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            Path candidate = p.resolve("docs/demo/fixtures");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate docs/demo/fixtures from " + dir);
    }
}
