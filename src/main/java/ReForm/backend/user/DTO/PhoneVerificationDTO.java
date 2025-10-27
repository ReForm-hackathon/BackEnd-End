package ReForm.backend.user.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 휴대폰 인증 관련 DTO
 * 1) 인증번호 발급 요청 시: phoneNumber만 전달
 * 2) 인증번호 확인 요청 시: phoneNumber + code 전달
 */
@Getter
@NoArgsConstructor
public class PhoneVerificationDTO {

	// 인증 대상 전화번호 (예: 01012345678)
	private String phoneNumber;

	// 사용자가 입력한 인증번호 (6자리)
	private String code;
}
