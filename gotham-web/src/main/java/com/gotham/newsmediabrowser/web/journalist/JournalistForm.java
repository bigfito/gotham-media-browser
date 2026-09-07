package com.gotham.newsmediabrowser.web.journalist;

import com.gotham.newsmediabrowser.common.journalist.Journalist;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Backing bean for the journalist create/edit forms. Mutable with getters/setters because Spring MVC
 * binds request parameters into it and Thymeleaf reads it back to re-render values and field errors.
 *
 * <p>Expected validation failures (missing name, bad email) are reported in-form; they never reach
 * the global error page.
 */
public class JournalistForm {

    @NotBlank(message = "First name is required.")
    @Size(max = 128, message = "First name must be at most 128 characters.")
    private String firstName;

    @NotBlank(message = "Last name is required.")
    @Size(max = 128, message = "Last name must be at most 128 characters.")
    private String lastName;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Email must be at most 254 characters.")
    private String email;

    @Size(max = 4000, message = "Bio must be at most 4000 characters.")
    private String bio;

    /** Converts this validated form into a not-yet-persisted domain journalist. */
    public Journalist toNewJournalist() {
        return Journalist.newJournalist(trim(firstName), trim(lastName), trim(email), trim(bio));
    }

    private String trim(String value) {
        return value != null ? value.strip() : null;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
