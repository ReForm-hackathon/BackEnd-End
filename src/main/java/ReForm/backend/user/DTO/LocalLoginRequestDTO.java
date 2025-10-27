package ReForm.backend.user.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LocalLoginRequestDTO {

    // 로컬 로그인 요청 시 전달받는 최소 정보 (사용자 ID + 비밀번호)
    private String userId;

    private String password;
}


