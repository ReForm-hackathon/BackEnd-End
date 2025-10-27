package ReForm.backend.user.socialLogin.userinfo;

import ReForm.backend.user.SocialType;
import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    private static final String NAVER = "naver";
    private static final String KAKAO = "kakao";

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("CustomOAuth2UserService loadUser() 실행 => oauth2 로그인 요청 진행");

        /**
         * DefaultOAuth2UserService 객체를 생성하여, loadUser(userRequest)를 통해 DefaultOAuth2User 객체를 생성 후 반환
         * DefaultOAuth2UserService의 loadUser()는 소셜 로그인 API의 사용자 정보 제공 URI로 요청을 보내서
         * 사용자 정보를 얻은 후, 이를 통해 DefaultOAuth2User 객체를 생성 후 반환한다.
         * 결과적으로, OAuth2User는 OAuth 서비스에서 가져온 유저 정보를 담고 있는 유저
         */

        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        // userRequest로 registrationId 추출 후 소셜 타입에 저장
        // 사용자가 어떤 소셜로그인으로 접속했는지 확인
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        SocialType socialType = getSocialType(registrationId);

        // OAuthProvider가 식별하는 고유 key
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        Map<String, Object> attributes = oAuth2User.getAttributes(); // API에서 받은 사용자 정보

        // 소셜 타입따라 user 정보 통해 객체 생성
        OAuthDTO extractDTO = OAuthDTO.of(socialType, userNameAttributeName, attributes);

        // db에 회원 조회하고 없으면 저장하는 로직
        User createUser = getUser(extractDTO, socialType);

        // DefaultOAuth2User를 구현한 CustomOAuth2User 객체를 생성해서 반환
        return new CustomOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(createUser.getRole().getKey())),
                attributes,
                extractDTO.getNameAttributeKey(),
                createUser.getEmail(),
                createUser.getRole(),
                socialType
        );
    }

    private SocialType getSocialType(String registrationId) {
        if(NAVER.equals(registrationId)) {
            return SocialType.NAVER;
        }
        if(KAKAO.equals(registrationId)) {
            return SocialType.KAKAO;
        }
        return SocialType.GOOGLE;
    }

    /**
     * SocialType과 attributes에 들어있는 소셜 로그인의 식별값 id를 통해 회원을 찾아 반환하는 메소드
     * 만약 찾은 회원이 있다면, 그대로 반환하고 없다면 saveUser()를 호출하여 회원을 저장한다.
     */
    private User getUser(OAuthDTO attributes, SocialType socialType) {
        User findUser = userRepository.findBySocialTypeAndSocialId(socialType,
                attributes.getOAuth2UserInfo().getId()).orElse(null);

        if(findUser == null) {
            return saveUser(attributes, socialType);
        }
        return findUser;
    }

    /**
     * OAuthAttributes의 toEntity() 메소드를 통해 빌더로 User 객체 생성 후 반환
     * 생성된 User 객체를 DB에 저장 : socialType, socialId, email, role 값만 있는 상태
     */
    private User saveUser(OAuthDTO attributes, SocialType socialType) {
        User createdUser = attributes.toEntity(socialType, attributes.getOAuth2UserInfo());
        log.info("새 유저 생성: {}", createdUser);
        User savedUser = userRepository.save(createdUser);
        log.info("DB 저장 완료: {}", savedUser);
        return savedUser;
    }
}