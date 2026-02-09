package io.routepickapi.common.validation;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final String MESSAGE =
        "비밀번호는 8~20자이며 대문자, 소문자, 숫자, 특수문자를 모두 포함하고 공백을 사용할 수 없습니다.";
    public static final String REGEX =
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])(?!.*\\s).{8,20}$";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null) {
            return false;
        }
        return PATTERN.matcher(password).matches();
    }
}
