package ReForm.backend.user.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserAdditionalDTO { // 소셜로그인 후 사용자가 추가로 입력해서 전달되는 객체

    private String nickname;

    private String address;

    private String phoneNumber;
}