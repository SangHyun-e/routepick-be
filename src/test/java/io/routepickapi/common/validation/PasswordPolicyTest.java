package io.routepickapi.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsValidPassword() {
        assertThat(PasswordPolicy.isValid("RoutePick1!"))
            .isTrue();
    }

    @Test
    void rejectsInvalidPasswordFormats() {
        assertThat(PasswordPolicy.isValid("routepick1!"))
            .isFalse();
        assertThat(PasswordPolicy.isValid("ROUTEPICK1!"))
            .isFalse();
        assertThat(PasswordPolicy.isValid("RoutePick!!"))
            .isFalse();
        assertThat(PasswordPolicy.isValid("RoutePick12"))
            .isFalse();
        assertThat(PasswordPolicy.isValid("Route 1!"))
            .isFalse();
        assertThat(PasswordPolicy.isValid("Ab1!"))
            .isFalse();
    }
}
