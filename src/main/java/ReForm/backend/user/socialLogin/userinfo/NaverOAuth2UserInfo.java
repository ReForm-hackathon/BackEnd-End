package ReForm.backend.user.socialLogin.userinfo;

import java.util.Map;

public class NaverOAuth2UserInfo extends OAuth2UserInfo {

    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    /**
     * attribute에서 "response"키에 해당하는 값을 꺼내서 Map 형태로 캐스팅하는 로직
     * response 키에 값이 없으면 사용자 정보가 없다고 판단해 null 반환
     */

    @Override
    public String getId() {
        @SuppressWarnings("unchecked") Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        if (response == null) {
            return null;
        }
        return (String) response.get("id");
    }

    @Override
    public String getName() {
        @SuppressWarnings("unchecked") Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        if (response == null) {
            return null;
        }
        return (String) response.get("name");
    }

    @Override
    public String getEmail() {
        @SuppressWarnings("unchecked") Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        if (response == null) {
            return null;
        }
        return (String) response.get("email");
    }
}
