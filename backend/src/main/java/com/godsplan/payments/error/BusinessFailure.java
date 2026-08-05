package com.godsplan.payments.error;

import lombok.Getter;

@Getter
public class BusinessFailure extends RuntimeException {
    private final ErrorCode code;

    public BusinessFailure(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}

