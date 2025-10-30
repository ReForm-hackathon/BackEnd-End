package ReForm.backend.config;

import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;
import ReForm.backend.user.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 핸드셰이크 시 JWT를 검증하는 인터셉터
 * - 토큰 추출 우선순위: query param `token` -> Authorization 헤더(Bearer)
 * - 유효하면 userId를 세션 attribute로 저장하여 이후 메시지 처리에서 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

	private final JwtService jwtService;
	private final UserRepository userRepository;

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
									 WebSocketHandler wsHandler, Map<String, Object> attributes) {
		if (!(request instanceof ServletServerHttpRequest servletReq)) {
			return false;
		}
		HttpServletRequest http = servletReq.getServletRequest();

		String token = http.getParameter("token");
		if (token == null || token.isBlank()) {
			// 헤더 Authorization도 시도 (Postman 등에서 사용 가능)
			token = jwtService.extractAccessToken(http).orElse(null);
		}
		if (token == null || token.isBlank()) {
			log.warn("WS handshake rejected: missing token");
			return false;
		}

		if (!jwtService.isTokenValid(token)) {
			log.warn("WS handshake rejected: invalid token");
			return false;
		}

		// userId 우선, 없으면 email로 조회하여 userId 확보
		Optional<String> userIdOpt = jwtService.extractUserId(token);
		if (userIdOpt.isEmpty()) {
            userIdOpt = jwtService.extractEmail(token)
                    .flatMap(email -> userRepository.findFirstByEmailOrderByCreatedAtDesc(email).map(User::getUserId));
		}
		if (userIdOpt.isEmpty()) {
			log.warn("WS handshake rejected: cannot resolve user identity");
			return false;
		}

		attributes.put("userId", userIdOpt.get());
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
		// no-op
	}
}


