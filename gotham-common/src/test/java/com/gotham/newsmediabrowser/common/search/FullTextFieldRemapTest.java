package com.gotham.newsmediabrowser.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FullTextFieldRemapTest {

    @Test
    void emptyFieldsUseArticleDefaults() {
        assertThat(FullTextFieldRemap.articleFields(List.of()))
                .containsExactly("title", "subtitle", "summary", "body");
        assertThat(FullTextFieldRemap.articleFields(null))
                .containsExactly("title", "subtitle", "summary", "body");
    }

    @Test
    void remapsKeywordParentsToAnalyzableTextSubfields() {
        assertThat(FullTextFieldRemap.articleFields(List.of("section", "tags", "location", "source")))
                .containsExactly("section.text", "tags.text", "location.text", "source.text");
    }

    @Test
    void ignoresNestedMultimediaCheckboxesAndUnknownNames() {
        assertThat(FullTextFieldRemap.articleFields(List.of("multimedia.title", "not_a_field", "body")))
                .containsExactly("body");
    }

    @Test
    void fallsBackToDefaultsWhenOnlyNestedMultimediaIsChecked() {
        assertThat(FullTextFieldRemap.articleFields(List.of("multimedia.caption")))
                .containsExactly("title", "subtitle", "summary", "body");
    }
}
