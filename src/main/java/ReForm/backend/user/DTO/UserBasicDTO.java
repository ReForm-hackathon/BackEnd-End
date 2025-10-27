package ReForm.backend.user.DTO;

import ReForm.backend.user.Role;
import ReForm.backend.user.SocialType;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserBasicDTO {

    // 소셜로그인 진행시 기본적으로 제공 받는 객체
    private String id; // 서비스의 user_id(VARCHAR)에 매핑되는 외부 식별자

    private String socialId;

    private String email;

    private String name;

    private String refreshToken; // 최초 로그인 시점엔 비어있을 수 있음

    private SocialType socialType;

    private Role role;
}