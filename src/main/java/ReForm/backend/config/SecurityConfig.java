package ReForm.backend.config;

import ReForm.backend.user.socialLogin.handler.OAuth2LoginFailureHandler;
import ReForm.backend.user.socialLogin.handler.OAuth2LoginSuccessHandler;
import ReForm.backend.user.socialLogin.userinfo.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ReForm.backend.filter.JwtAuthenticationFilter;
import ReForm.backend.user.service.JwtService;
import ReForm.backend.user.repository.UserRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration // 스프링 설정 클래스로 등록하는 어노테이션 @Bean 등록 가능
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    // securityFilterChain : 필터 체인을 직접 등록해서 사용하는 메서드로 인증 인가 관련 설정 등록 가능
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CustomOAuth2UserService customOAuth2UserService,
                                                   OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                                                   OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                // CORS : Cross-Origin-Resource-Sharing : 현재 출처가 아닌 다른 도메인에 요청을 보내는 것을 의미
                //CORS는 스프링 시큐리티보다 먼저 실행되어야 하는데 사전 요청에 쿠키가 없고 스프링 시큐리티가 먼저 실행될 경우 요청 거부됨
                // 따라서 corsConfigurationSource() 적용 통해서 내가 설정한 경로에는 신뢰하고 허용하는 것을 명시적으로 알려줌 (밑에 구현)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable()) //
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index", "/login", "/register",
                                "/auth/**", "/oauth2/**", "/login/oauth2/**", "/oauth/callback/**",
                                "/message", "/verify"
                        ).permitAll()
                        .requestMatchers("/image/upload/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .redirectionEndpoint(redir -> redir.baseUri("/oauth/callback/*"))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )
                // OAuth2 인증 과정에는 세션이 필요하므로 기본값 사용. JWT 발급 후 API에서는 stateless 필터에서 검증
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        // JWT 필터를 빈으로 등록해 필터 체인에서 사용할 수 있게 함
        return new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() { // CorsConfigurationSource는 CORS 규칙 담고 있는 Bean 객체
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:8080")); // CORS 정책 허용할 ORIGIN을 지정 -> http://localhost:8080에서 오는 요청만 허용
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE")); // CORS 정책 허용할 메서드를 지정 -> GET, POST, PUT, DELETE 요청만 허용
        config.setAllowedHeaders(List.of("*")); // 클라이언트가 서버로 요청할 때 사용할 수 있는 HTTP 헤더를 의미하며 '*' : 모든 헤더 사용을 허용
        // 주의 : setAllowCredentials(true) 설정할 경우 allowedOrigins는 "*" 와일드카드를 사용할 수 없음. 반드시 명시된 도메인 사용
        config.setAllowCredentials(true); // 클라이언트가 쿠키, 인증 토큰 등 자격 증명 정보를 포함해서 요청하도록 허용하는 설정

        // 스프링에서 CORS 설정을 URL 경로 패턴별로 관리하기 위한 인스턴스 생성
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 모든 경로에 CORS 설정
        return source;
    }
}
