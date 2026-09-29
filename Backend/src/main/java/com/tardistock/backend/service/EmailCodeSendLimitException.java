package com.tardistock.backend.service;

/**
 * The address reached its 24-hour code send limit. Controllers map this to
 * 429 instead of the 503 used for mail delivery failures.
 */
public class EmailCodeSendLimitException extends IllegalStateException {

    public EmailCodeSendLimitException() {
        super("오늘 이 이메일로 보낼 수 있는 인증번호 횟수를 초과했습니다. "
                + "24시간 후 다시 시도해주세요.");
    }
}
