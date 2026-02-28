package com.apargo.services.message_report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateButtonResponse {

    private final String  buttonType;   // URL | QUICK_REPLY | PHONE_NUMBER | COPY_CODE | OTP …
    private final String  text;
    private final String  url;          // null unless URL button
    private final String  phoneNumber;  // null unless PHONE_NUMBER button
    private final String  otpType;      // ONE_TAP | COPY_CODE | ZERO_TAP — null unless OTP
    private final Integer buttonIndex;
}