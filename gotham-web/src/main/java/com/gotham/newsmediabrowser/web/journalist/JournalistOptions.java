package com.gotham.newsmediabrowser.web.journalist;

import com.gotham.newsmediabrowser.common.journalist.Journalist;
import com.gotham.newsmediabrowser.common.journalist.JournalistRepository;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Supplies the newsroom roster for the "Journalist" filter dropdowns on the landing panel and the
 * article results page.
 *
 * <p>Each option carries the journalist's Elasticsearch {@code _id} as its value and the full name as
 * its label. The id matters: {@code ArticleFullTextService} recognises an id and filters with an exact
 * nested {@code term}, whereas a name goes through a text {@code match} that would also return the
 * articles of anyone with a similar name. Selecting from a list is therefore not just easier than
 * typing — it is a more precise filter.
 */
@Component
public class JournalistOptions {

    private static final Logger log = LoggerFactory.getLogger(JournalistOptions.class);

    /**
     * How many journalists a dropdown will hold. The prototype's newsroom is 15; this ceiling exists so
     * a runaway index cannot render a select with thousands of options. Past this size the right
     * control is a type-ahead, not a longer list.
     */
    static final int MAX_OPTIONS = 500;

    private final JournalistRepository journalistRepository;

    public JournalistOptions(JournalistRepository journalistRepository) {
        this.journalistRepository = journalistRepository;
    }

    /**
     * The roster, A–Z by display name.
     *
     * <p>A filter control must never take the page down with it: if Elasticsearch cannot answer, this
     * logs and returns an empty list, and the template falls back to the "Any journalist" option alone.
     * The rest of the search page keeps working.
     */
    public List<Option> all() {
        try {
            Collator byName = Collator.getInstance(Locale.US);
            byName.setStrength(Collator.PRIMARY);
            return journalistRepository.findAll(0, MAX_OPTIONS).items().stream()
                    .map(Option::from)
                    .sorted(Comparator.comparing(Option::label, byName))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Could not load the journalist filter options: {}", e.toString());
            return List.of();
        }
    }

    /**
     * One entry in the dropdown.
     *
     * @param value the journalist's Elasticsearch {@code _id}, submitted as the {@code journalist} param
     * @param label the name shown to the reader
     */
    public record Option(String value, String label) {

        static Option from(Journalist journalist) {
            String name = journalist.fullName();
            return new Option(journalist.id(), name != null && !name.isBlank() ? name : journalist.id());
        }
    }
}
