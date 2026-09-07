package com.gotham.newsmediabrowser.common.article;

import com.gotham.newsmediabrowser.common.journalist.Journalist;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A journalist's byline as nested inside an article document (a denormalized snapshot of the
 * {@code gotham-journalists} master record plus the byline's order and role).
 *
 * <p>The snapshot is taken at write time; if the master journalist is later edited, articles keep
 * this copy until they are reindexed by the cascade in P4-T03. {@code fullName} is derived from the
 * snapshotted first/last name, matching {@link Journalist#fullName()}.
 *
 * @param journalistId     the master {@code gotham-journalists} {@code _id} this byline points to
 * @param firstName        snapshotted given name
 * @param lastName         snapshotted family name
 * @param email            snapshotted email
 * @param bio              snapshotted biography
 * @param bylineOrder      position of this journalist in the byline (0-based)
 * @param contributionRole optional role; {@code null} when unspecified
 */
public record ArticleJournalist(
        String journalistId,
        String firstName,
        String lastName,
        String email,
        String bio,
        int bylineOrder,
        ContributionRole contributionRole) {

    /** Snapshots a master journalist into a byline at the given order and role. */
    public static ArticleJournalist fromJournalist(Journalist journalist, int bylineOrder, ContributionRole role) {
        return new ArticleJournalist(
                journalist.id(),
                journalist.firstName(),
                journalist.lastName(),
                journalist.email(),
                journalist.bio(),
                bylineOrder,
                role);
    }

    /** The display name, derived by joining the non-blank name parts with a single space. */
    public String fullName() {
        return Stream.of(firstName, lastName)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }
}
