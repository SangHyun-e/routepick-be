package io.routepickapi.common.error;

public class CustomException extends RuntimeException {
    private final ErrorType type;

    public CustomException(ErrorType type) {
        super(type.message);
        this.type = type;
    }

    public CustomException(ErrorType type, String detailMessage) {
        super(detailMessage);
        this.type = type;
    }

    public ErrorType getType() {
        return  type;
    }
}
