package ReForm.backend.user.socialLogin.userinfo;

import ReForm.backend.user.Role;
import ReForm.backend.user.SocialType;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;

@Getter
public class CustomOAuth2User extends DefaultOAuth2User { //OAuth2 인증 통해 사용자 정보 표현 OAuth2User 인터페이스 구현 예정

    private String email; // CustomOAuth2User 생성자를 만들어서 파라미터에 이메일값 추가
    private Role role; // CustomOAuth2User 생성자를 만들어서 파라미터에 관리자인지 일반 사용자인지 추가
    private SocialType socialType; // CustomOAuth2User 생성자를 만들어서 파라미터에 어떤 소셜타입인지 추가

    /**
     * CustomOAuth2User 클래스는 부모 클래스인 DefaultOAuht2User를 상속 받았으므로, super() 사용
     */

    public CustomOAuth2User(Collection<? extends GrantedAuthority> authorities, Map<String, Object> attributes,
                            String nameAttributeKey, String email, Role role, SocialType socialType) {
        super(authorities, attributes, nameAttributeKey);
        this.email = email;
        this.role = role;
        this.socialType = socialType;
    }
}