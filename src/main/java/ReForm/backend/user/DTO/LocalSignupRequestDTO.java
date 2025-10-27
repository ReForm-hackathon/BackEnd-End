package ReForm.backend.user.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LocalSignupRequestDTO {

    // 로컬 회원가입 시 필요한 필수 및 선택 정보
    private String userId;    // 필수 (서비스 로그인 ID)

    private String email;     // 필수

    private String password;  // 필수 (서버에서 해시 처리)

    private String userName;  // 표시 이름

    private String phoneNumber;

    private String verificationCode; // SMS 인증번호 (회원가입 시 최종 검증)
}


