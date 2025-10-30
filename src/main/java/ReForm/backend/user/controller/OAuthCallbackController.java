package ReForm.backend.user.controller;

import ReForm.backend.user.Role;
import ReForm.backend.user.SocialType;
import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;
import ReForm.backend.user.service.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OAuth 인가코드 교환 → 우리 서비스 JWT 발급 컨트롤러
 * 사용 시나리오
 * 1) 프론트가 소셜 로그인 인가코드를 확보
 * 2) 본 API로 {provider} 및 code(헤더 X-OAuth-Code 또는 body.code) 전달
 * 3) 서버는 해당 소셜 토큰 엔드포인트로 code 교환 → provider access_token 획득
 * 4) provider userinfo 조회 → (socialType, socialId) 기준으로 사용자 upsert
 * 5) 우리 서비스 accessToken/refreshToken 발급 후 JSON 바디로 반환
 */
@RestController
@RequestMapping("/oauth/callback")
@RequiredArgsConstructor
@Slf4j
public class OAuthCallbackController {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // google
    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;
    @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String googleClientSecret;
    @Value("${spring.security.oauth2.client.registration.google.redirect-uri:http://localhost:8080/oauth/callback/google}")
    private String googleRedirectUri;

    // kakao
    @Value("${spring.security.oauth2.client.registration.kakao.client-id:}")
    private String kakaoClientId;
    @Value("${spring.security.oauth2.client.registration.kakao.client-secret:}")
    private String kakaoClientSecret;
    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri:http://localhost:8080/oauth/callback/kakao}")
    private String kakaoRedirectUri;
    @Value("${spring.security.oauth2.client.provider.kakao.token_uri:https://kauth.kakao.com/oauth/token}")
    private String kakaoTokenUri;

    // naver
    @Value("${spring.security.oauth2.client.registration.naver.client-id:}")
    private String naverClientId;
    @Value("${spring.security.oauth2.client.registration.naver.client-secret:}")
    private String naverClientSecret;
    @Value("${spring.security.oauth2.client.registration.naver.redirect-uri:http://localhost:8080/oauth/callback/naver}")
    private String naverRedirectUri;
    @Value("${spring.security.oauth2.client.provider.naver.token_uri:https://nid.naver.com/oauth2.0/token}")
    private String naverTokenUri;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 소셜 리다이렉트(GET) 수신용: 인가 코드 콘솔 출력 및 응답으로 에코
     * - 실제 토큰 교환은 아래 POST /oauth/callback/{provider}로 수행
     */
    @GetMapping("/{provider}")
    public ResponseEntity<?> echoAuthorizationCode(
            @PathVariable String provider,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "redirectUri", required = false) String redirectOverride,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        // 1) 헤더 X-OAuth-Code가 있으면: GET으로도 교환 수행 → 토큰 JSON 반환
        String headerCode = request.getHeader("X-OAuth-Code");
        if (headerCode != null && !headerCode.isBlank()) {
            log.info("[OAUTH-EXCHANGE-GET] provider={} codeSource=header code={}", provider, headerCode);
            switch (provider.toLowerCase()) {
                case "kakao" -> {
                    try {
                        TokenResponse tr = handleKakao(headerCode, redirectOverride);
                        log.info("[TOKENS] AT={} RT={} ATexpMs={} RTexpMs={} newUser={} msg={}",
                                tr.getAccessToken(), tr.getRefreshToken(), tr.getAccessTokenExpiresInMs(), tr.getRefreshTokenExpiresInMs(), tr.isNewUser(), tr.getMessage());
                        return ResponseEntity.ok(tr);
                    } catch (Exception e) {
                        log.error("GET exchange failed(kakao): {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_grant"));
                    }
                }
                case "naver" -> {
                    try {
                        TokenResponse tr = handleNaver(headerCode, redirectOverride);
                        log.info("[TOKENS] AT={} RT={} ATexpMs={} RTexpMs={} newUser={} msg={}",
                                tr.getAccessToken(), tr.getRefreshToken(), tr.getAccessTokenExpiresInMs(), tr.getRefreshTokenExpiresInMs(), tr.isNewUser(), tr.getMessage());
                        return ResponseEntity.ok(tr);
                    } catch (Exception e) {
                        log.error("GET exchange failed(naver): {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_grant"));
                    }
                }
                case "google" -> {
                    try {
                        TokenResponse tr = handleGoogle(headerCode, redirectOverride);
                        log.info("[TOKENS] AT={} RT={} ATexpMs={} RTexpMs={} newUser={} msg={}",
                                tr.getAccessToken(), tr.getRefreshToken(), tr.getAccessTokenExpiresInMs(), tr.getRefreshTokenExpiresInMs(), tr.isNewUser(), tr.getMessage());
                        return ResponseEntity.ok(tr);
                    } catch (Exception e) {
                        log.error("GET exchange failed(google): {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_grant"));
                    }
                }
                default -> {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "unsupported_provider"));
                }
            }
        }

        // 2) 그 외: 소셜 리다이렉트로 전달된 code를 에코하여 프론트가 확인 가능하게 함
        log.info("인가코드가 발급되었습니다. provider={} code={} state={}", provider, code, state);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "인가코드가 발급되었습니다.",
                "provider", provider,
                "code", code == null ? "" : code
        ));
    }

    /**
     * 인가코드 교환 엔드포인트
     * - Path: /oauth/callback/{provider} (kakao|naver|google)
     * - Input: 헤더 X-OAuth-Code 또는 바디 { code, redirectUri? }
     * - Output: 우리 서비스 acces클라라sToken/refreshToken 및 만료시간(ms)
     */
    @PostMapping("/{provider}")
    public ResponseEntity<TokenResponse> exchangeCode(
            @PathVariable String provider,
            @RequestHeader(value = "X-OAuth-Code", required = false) String codeHeader,
            @RequestBody(required = false) Map<String, String> body
    ) {
        try {
            // 우선순위: 헤더 X-OAuth-Code → 바디 code (둘 다 제공돼도 헤더 우선)
            String code = codeHeader != null ? codeHeader : (body != null ? body.get("code") : null);
            String overrideRedirectUri = body != null ? body.get("redirectUri") : null;
            log.info("[OAUTH-EXCHANGE] provider={} codeSource={} code={}", provider, (codeHeader != null ? "header" : (body != null && body.get("code") != null ? "body" : "none")), code);
            if (code == null || code.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        new TokenResponse(null, null, null, null, false, "인가코드가 필요합니다. 헤더 X-OAuth-Code 또는 바디 code로 전달하세요.")
                );
            }

            // provider별 토큰 교환 + 사용자 정보 조회 + JWT 발급
            switch (provider.toLowerCase()) {
                case "kakao" -> {
                    return ResponseEntity.ok(handleKakao(code, overrideRedirectUri));
                }
                case "naver" -> {
                    return ResponseEntity.ok(handleNaver(code, overrideRedirectUri));
                }
                case "google" -> {
                    return ResponseEntity.ok(handleGoogle(code, overrideRedirectUri));
                }
                default -> {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
            }
        } catch (Exception e) {
            log.error("OAuth code exchange failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Provider Access Token 직접 로그인 엔드포인트
     * - Path: /oauth/callback/{provider}/token (kakao|naver|google)
     * - Input: 헤더 X-Provider-Access 또는 바디 { accessToken }
     * - Output: 우리 서비스 accessToken/refreshToken 및 만료시간(ms)
     */
    @PostMapping("/{provider}/token")
    public ResponseEntity<TokenResponse> loginWithProviderToken(
            @PathVariable String provider,
            @RequestHeader(value = "X-Provider-Access", required = false) String providerAccessHeader,
            @RequestBody(required = false) Map<String, String> body
    ) {
        try {
            String providerAccess = providerAccessHeader != null ? providerAccessHeader : (body != null ? body.get("accessToken") : null);
            if (providerAccess == null || providerAccess.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        new TokenResponse(null, null, null, null, false, "소셜 access token이 필요합니다. 헤더 X-Provider-Access 또는 바디 accessToken으로 전달하세요.")
                );
            }

            switch (provider.toLowerCase()) {
                case "kakao" -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(providerAccess);
                    ResponseEntity<String> infoResp = restTemplate.exchange(
                            "https://kapi.kakao.com/v2/user/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    JsonNode info = objectMapper.readTree(infoResp.getBody());
                    String socialId = info.path("id").asText();
                    String email = info.path("kakao_account").path("email").asText("");
                    String name = info.path("properties").path("nickname").asText("");
                    UpsertResult res = upsertUser(SocialType.KAKAO, socialId, email, name);
                    return ResponseEntity.ok(issueOurTokens(res.user(), res.isNew()));
                }
                case "naver" -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(providerAccess);
                    ResponseEntity<String> infoResp = restTemplate.exchange(
                            "https://openapi.naver.com/v1/nid/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    JsonNode infoRoot = objectMapper.readTree(infoResp.getBody());
                    JsonNode info = infoRoot.path("response");
                    String socialId = info.path("id").asText();
                    String email = info.path("email").asText("");
                    String name = info.path("name").asText("");
                    UpsertResult res = upsertUser(SocialType.NAVER, socialId, email, name);
                    return ResponseEntity.ok(issueOurTokens(res.user(), res.isNew()));
                }
                case "google" -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(providerAccess);
                    ResponseEntity<String> infoResp = restTemplate.exchange(
                            "https://www.googleapis.com/oauth2/v2/userinfo", HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    JsonNode info = objectMapper.readTree(infoResp.getBody());
                    String socialId = info.path("id").asText();
                    String email = info.path("email").asText("");
                    String name = info.path("name").asText("");
                    UpsertResult res = upsertUser(SocialType.GOOGLE, socialId, email, name);
                    return ResponseEntity.ok(issueOurTokens(res.user(), res.isNew()));
                }
                default -> {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                            new TokenResponse(null, null, null, null, false, "지원하지 않는 provider 입니다.")
                    );
                }
            }
        } catch (Exception e) {
            log.error("OAuth token login failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new TokenResponse(null, null, null, null, false, "소셜 access token 처리 중 오류가 발생했습니다.")
            );
        }
    }

    /** Kakao: code → token → userinfo → upsert → JWT 발급 */
    private TokenResponse handleKakao(String code, String redirectOverride) throws Exception {
        String tokenEndpoint = kakaoTokenUri != null && !kakaoTokenUri.isBlank() ? kakaoTokenUri : "https://kauth.kakao.com/oauth/token";
        String redirectUri = redirectOverride != null && !redirectOverride.isBlank() ? redirectOverride : kakaoRedirectUri;

        JsonNode tokenJson = requestToken(tokenEndpoint, kakaoClientId, kakaoClientSecret, code, redirectUri);
        String providerAccess = tokenJson.path("access_token").asText();

        // userinfo 조회 (Bearer provider access token)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(providerAccess);
        ResponseEntity<String> infoResp = restTemplate.exchange(
                "https://kapi.kakao.com/v2/user/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        JsonNode info = objectMapper.readTree(infoResp.getBody());
        String socialId = info.path("id").asText();
        String email = info.path("kakao_account").path("email").asText("");
        String name = info.path("properties").path("nickname").asText("");

        UpsertResult res = upsertUser(SocialType.KAKAO, socialId, email, name);
        return issueOurTokens(res.user(), res.isNew());
    }

    /** Naver: code → token → userinfo → upsert → JWT 발급 */
    private TokenResponse handleNaver(String code, String redirectOverride) throws Exception {
        String tokenEndpoint = naverTokenUri != null && !naverTokenUri.isBlank() ? naverTokenUri : "https://nid.naver.com/oauth2.0/token";
        String redirectUri = redirectOverride != null && !redirectOverride.isBlank() ? redirectOverride : naverRedirectUri;

        JsonNode tokenJson = requestToken(tokenEndpoint, naverClientId, naverClientSecret, code, redirectUri);
        String providerAccess = tokenJson.path("access_token").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(providerAccess);
        ResponseEntity<String> infoResp = restTemplate.exchange(
                "https://openapi.naver.com/v1/nid/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        JsonNode infoRoot = objectMapper.readTree(infoResp.getBody());
        JsonNode info = infoRoot.path("response");
        String socialId = info.path("id").asText();
        String email = info.path("email").asText("");
        String name = info.path("name").asText("");

        UpsertResult res = upsertUser(SocialType.NAVER, socialId, email, name);
        return issueOurTokens(res.user(), res.isNew());
    }

    /** Google: code → token → userinfo → upsert → JWT 발급 */
    private TokenResponse handleGoogle(String code, String redirectOverride) throws Exception {
        String tokenEndpoint = "https://oauth2.googleapis.com/token";
        String redirectUri = redirectOverride != null && !redirectOverride.isBlank() ? redirectOverride : googleRedirectUri;

        JsonNode tokenJson = requestToken(tokenEndpoint, googleClientId, googleClientSecret, code, redirectUri);
        String providerAccess = tokenJson.path("access_token").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(providerAccess);
        ResponseEntity<String> infoResp = restTemplate.exchange(
                "https://www.googleapis.com/oauth2/v2/userinfo", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        JsonNode info = objectMapper.readTree(infoResp.getBody());
        String socialId = info.path("id").asText();
        String email = info.path("email").asText("");
        String name = info.path("name").asText("");

        UpsertResult res = upsertUser(SocialType.GOOGLE, socialId, email, name);
        return issueOurTokens(res.user(), res.isNew());
    }

    /**
     * 소셜 토큰 엔드포인트에 authorization_code 교환 요청
     * - 표준 파라미터(grant_type=authorization_code, code, client_id, client_secret?, redirect_uri)
     */
    private JsonNode requestToken(String tokenUri, String clientId, String clientSecret, String code, String redirectUri) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(params, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(tokenUri, req, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Token endpoint error: " + resp.getStatusCode());
        return objectMapper.readTree(resp.getBody());
    }

    /** (socialType, socialId) 기준 사용자 없으면 생성, 있으면 기존 사용자 반환 */
    private UpsertResult upsertUser(SocialType type, String socialId, String email, String name) {
        return userRepository.findBySocialTypeAndSocialId(type, socialId)
                .map(u -> new UpsertResult(u, false))
                .orElseGet(() -> {
                    String generatedUserId = type.name().toLowerCase() + "_" + socialId;
                    User u = User.builder()
                            .userId(generatedUserId)
                            .email(email)
                            .userName(name)
                            .socialType(type)
                            .socialId(socialId)
                            .role(Role.USER)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .profileImageUrl("/basicProfile/basicUSerImage.png")
                            .build();
                    User saved = userRepository.save(u);
                    return new UpsertResult(saved, true);
                });
    }

    /** 우리 서비스용 JWT 발급(AT/RT) 및 만료시간(ms) 응답 모델 구성 */
    private TokenResponse issueOurTokens(User user, boolean isNew) {
        String at = jwtService.createAccessTokenByUserId(user.getUserId());
        String rt = jwtService.createRefreshToken();
        jwtService.updateRefreshTokenByUserId(user.getUserId(), rt);
        long atExp = jwtService.getAccessExpiration();
        long rtExp = jwtService.getRefreshExpiration();
        // 신규 사용자라면 최초 추가정보 입력 안내 메시지 포함
        String message = isNew ? "신규 사용자입니다. /api/users/add/status로 닉네임/주소를 등록하세요." : "로그인에 성공했습니다.";
        return new TokenResponse(at, rt, atExp, rtExp, isNew, message);
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private Long accessTokenExpiresInMs;
        private Long refreshTokenExpiresInMs;
        private boolean newUser;      // true면 DB에 없던 신규 가입자
        private String message;       // 프론트 안내 메시지
    }

    /** upsert 결과 묶음 */
    private record UpsertResult(User user, boolean isNew) {}
}


