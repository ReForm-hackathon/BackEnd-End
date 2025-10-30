package ReForm.backend.config;

import ReForm.backend.chat.WebSocketChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 엔드포인트 등록 설정
 * - /ws/conn 경로로 핸드셰이크 허용
 * - CORS: 모든 Origin 허용(필요 시 운영 환경에 맞게 제한)
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

	private final WebSocketChatHandler webSocketChatHandler;
	private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry
				.addHandler(webSocketChatHandler, "/ws/conn")
				.addInterceptors(jwtHandshakeInterceptor)
				// Chrome 확장(Origin: chrome-extension://...) 등 비표준 스킴 허용을 위해 패턴 사용
				.setAllowedOriginPatterns("*")
				// 일부 환경에서 Origin이 null/file 스킴으로 오는 경우를 대비하여 광범위 허용
				.setAllowedOrigins("*");
	}
}


