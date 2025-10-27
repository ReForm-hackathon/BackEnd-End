package ReForm.backend.user.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import ReForm.backend.user.SocialType;
import ReForm.backend.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
@Getter
public class JwtService { // JWT 생성 (AT, RT) , 정보 조회 판단 로직

    // @Value : 일반적으로 외부화된 속성을 주입하는데 사용 / application.yml에서 jwt 프로퍼티 주입
    // 토큰 생성 검증 과정에서 보안 및 관리상 설정 파일에서 관리해서 주입해서 사용하는 방식이 안전
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access.expiration}")
    private Long accessExpiration;

    @Value("${jwt.refresh.expiration}")
    private Long refreshExpiration;

    @Value("${jwt.access.header}")
    private String accessHeader;

    @Value("${jwt.refresh.header}")
    private String refreshHeader;

    /**
     * JWT 토큰 생성과 검증을 다룰 때 자주 사용하는 상수 값 사용
     * Subject : 데이터의 주제 또는 주체를 의미 (흔히 토큰이 어떤 사용자 어떤 대상을 위한 것인지 식별 용도)
     * Claim : 토큰에 담기는 실제 정보(속성, 데이터) 이름(Key), 값(Value)쌍으로 이뤄져 있음
     * 클레임 이름으로 이메일 값을 받음 (JWT 페이로드에 이메일 주소 정보 포함)
     * 헤더 Authorization 키에 Bearer 값이 들어가게됨
     */

    private static final String ACCESS_TOKEN_Subject = "AccessToken";
    private static final String REFRESH_TOKEN_Subject = "RefreshToken";
    private static final String EMAIL_CLAIM = "email";
    private static final String USER_ID_CLAIM = "userId";
    private static final String SOCIALTYPE_CLAIM = "socialType";
    // 표준 Authorization 헤더 접두사 (공백 포함)
    private static final String BEARER = "Bearer ";

    private final UserRepository userRepository;

    public JwtService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // AccessToken 생성 로직

    public String createAccessToken(String email, String socialType) {
        Date now = new Date(); // 토큰 생성 시간은 현재 시간으로 설정

        return JWT.create() // 토큰 생성 시작
                .withSubject(ACCESS_TOKEN_Subject) // 토큰 주제 설정
                .withExpiresAt(new Date(now.getTime() + accessExpiration)) // 토큰 만료 기간
                .withClaim(EMAIL_CLAIM, email) // 사용자 이메일을 클레임으로 추가
                .withClaim(SOCIALTYPE_CLAIM, socialType) // 소셜 로그인 타입을 클레임으로 추가 ex) 구글 , 카카오, 네이버
                .sign(Algorithm.HMAC512(secret)); // HMAC512 알고리즘과 비밀 키를 사용하여 토큰 서명
    }

    // userId 기반 토큰 생성 (권장: 내부 식별자)
    public String createAccessTokenByUserId(String userId) {
        Date now = new Date();
        return JWT.create()
                .withSubject(ACCESS_TOKEN_Subject)
                .withExpiresAt(new Date(now.getTime() + accessExpiration))
                .withClaim(USER_ID_CLAIM, userId)
                .sign(Algorithm.HMAC512(secret));
    }

    // RefreshToken 생성 로직
    public String createRefreshToken() {
        Date now = new Date();
        return JWT.create()
                .withSubject(REFRESH_TOKEN_Subject) //토큰 주제 설정
                .withExpiresAt(new Date(now.getTime() + refreshExpiration)) // 토큰 만료 기간
                .sign(Algorithm.HMAC512(secret)); // HMAC512 알고리즘과 비밀 키를 사용하여 토큰 서명
    }

    // AT 헤더에 담아서 전달하는 로직 (클라이언트가 최초 로그인해서 AT를 발급받을 때)
    public void sendAccessToken(HttpServletResponse response, String accessToken) {
        response.setStatus(HttpServletResponse.SC_OK); // HTTP 200 설정
        response.addHeader(accessHeader, BEARER + accessToken); // 응답 헤더에 AT 정보 보냄 (Bearer 접두사 포함)
        log.info("AccessToken을 재발급합니다 {}", accessToken); // log를 통해 재발급된 AT 정보 출력
    }

    // AT, RT 헤더에 담아서 전달하는 로직 (클라이언트가 로그인 했거나, RT로 AT 갱신할 때)
    public void sendRefreshToken(HttpServletResponse response,String accessToken, String refreshToken) {
        response.setStatus(HttpServletResponse.SC_OK);

        response.addHeader(accessHeader, BEARER + accessToken); // 응답 헤더에 AT 정보 보냄 (Bearer 접두사 포함)
        response.addHeader(refreshHeader, BEARER + refreshToken); // 응답 헤더에 RT 정보 보냄 (Bearer 접두사 포함)

        log.info("Header에 AccessToken과 RefreshToken 보낼 준비가 완료됐습니다.");
    }

    // 헤더에서 AT 추출 (Request)
    // Optional 사용하는 이유 : 토큰 요청이 헤더에 없거나, 형식이 올바르지 않을 경우 예외가 발생하게됨 NPE 방지 위해 안전하게 Optional 사용
    public Optional<String> extractAccessToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(accessHeader))
                .filter(header -> header.startsWith(BEARER))
                .map(header -> header.substring(BEARER.length()).trim());
    }

    // 헤더에서 RT 추출 (Request)
    public Optional<String> extractRefreshToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(refreshHeader))
                .filter(header -> header.startsWith(BEARER))
                .map(header -> header.substring(BEARER.length()).trim());
    }

    // 액세스 토큰에서 claim으로 정의해둔 이메일 추출
    public Optional<String> extractEmail(String accessToken) {
        try {
            // try-catch문 활용해서 추출값이 있으면 builder 통해 반환, 없으면 Optional.Empty() 반환
            return Optional.ofNullable(JWT.require(Algorithm.HMAC512(secret)) // JWT 시크릿키로 토큰 유효성 검사
                    .build() // 빌더 반환해서 verifier 생성
                    .verify(accessToken) // 액세스 토큰이 유효한지 검증하고 일치하지 않으면 catch 구문으로 넘어가 오류 발생
                    .getClaim(EMAIL_CLAIM)
                    .asString());
        } catch (Exception e) {
            log.error("유효하지 않은 액세스 토큰입니다.");
            return Optional.empty();
        }
    }

    // userId 추출
    public Optional<String> extractUserId(String accessToken) {
        try {
            return Optional.ofNullable(JWT.require(Algorithm.HMAC512(secret))
                    .build()
                    .verify(accessToken)
                    .getClaim(USER_ID_CLAIM)
                    .asString());
        } catch (Exception e) {
            log.error("유효하지 않은 액세스 토큰입니다.");
            return Optional.empty();
        }
    }

    // 액세스 토큰 검증하고 소셜 타입 추출
    public Optional<SocialType> extractSocialType(String accessToken) {
        try { // ENUM 같이 자바 객체 타입은 JWT 구조인 JSON에 바로 저장할 수 없으므로 스트링으로 지정한 후 토큰에서 문자열 추출
            String SocialTypeStr = JWT.require(Algorithm.HMAC512(secret))
                    .build()
                    .verify(accessToken)
                    .getClaim(SOCIALTYPE_CLAIM)
                    .asString();

            // 문자열을 ENUM으로 변환후 Optional로 감싸서 반환
            return Optional.of(SocialType.valueOf(SocialTypeStr));
        } catch (Exception e) {
            log.error("유효하지 않은 액세스 토큰입니다.");
            return Optional.empty();
        }
    }

    // 리프레시 토큰 DB에 저장(업데이트) 로직
    public void updateRefreshToken(String email, SocialType socialType, String refreshToken) {

        userRepository.findByEmailAndSocialType(email, socialType)
                .ifPresentOrElse(user -> { // Optional 처리시 사용
                            user.updateRefreshToken(refreshToken); // 사용자일 경우 리프레시 토큰 업데이트
                            userRepository.save(user); // 업데이트 된 리프레시 토큰 DB에 다시 업데이트해서 저장
                        }, () -> log.error("사용자 이메일과 소셜 타입이 존재하지 않습니다.") // 사용자 없을 경우 오류 출력
                );
    }

    // 로컬/소셜 공통: userId로 RT 업데이트
    public void updateRefreshTokenByUserId(String userId, String refreshToken) {
        userRepository.findById(userId).ifPresent(user -> {
            user.updateRefreshToken(refreshToken);
            userRepository.save(user);
        });
    }

    // 토큰 유효성 검증하는 메서드
    public boolean isTokenValid(String Token) { // 예외처리
        try {
            JWT.require(Algorithm.HMAC512(secret)).build().verify(Token);
            return true;
        } catch (Exception e) {
            log.error("유효하지 않은 토큰입니다. {}", e.getMessage()); // 객체 오류 메시지 문자열로 반환하는 메서드
            return false;
        }
    }
}
