package ReForm.backend.chat;

import ReForm.backend.chat.dto.ChatMessageDto;
import ReForm.backend.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket 텍스트 메시지 핸들러
 *
 * 책임
 * - 세션 연결/해제 관리 (현재 접속한 클라이언트의 소켓 세션을 메모리에 보관/정리)
 * - 메시지 타입(JOIN/TALK/LEAVE)에 따른 채팅방 세션 관리 및 방 단위 브로드캐스트
 * - TALK 수신 시 영속 저장(메시지 히스토리 유지) 및 발신자 읽음 처리
 *
 * 메모리 구조
 * - allSessions: 현재 서버 인스턴스에 연결된 모든 WebSocket 세션(모니터링 용)
 * - roomIdToSessions: 방 ID별로 세션 Set을 보관하여, 같은 방 사용자에게만 메시지를 전송
 *
 * 주의
 * - 세션 관리(접속/브로드캐스트)는 메모리 기반. 수평 확장 시 분산 세션 동기화(예: Redis Pub/Sub) 필요
 * - 메시지 저장과 읽음 처리는 ChatService를 통해 DB에 영속화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChatHandler extends TextWebSocketHandler {

	private final ObjectMapper objectMapper;
	private final ChatService chatService;

	// 전체 접속 세션 (모니터링/디버깅 용도)
	private final Set<WebSocketSession> allSessions = new HashSet<>();

	// 채팅방별 세션 목록: 방 ID -> 세션 집합
	private final Map<Long, Set<WebSocketSession>> roomIdToSessions = new HashMap<>();

	// 신규 소켓 연결 시 호출: 핸드셰이크 완료 후 서버가 세션을 등록하고 연결 안내 메시지 전송
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		log.info("WS connected: {}", session.getId());
		allSessions.add(session);
		// 초기 안내 메시지 전송 (클라이언트가 연결 성공을 확인할 수 있게 함)
		session.sendMessage(new TextMessage("WebSocket 연결 완료"));
	}

	// 텍스트 메시지 수신 시 호출:
	// 1) payload(JSON)를 ChatMessageDto로 역직렬화
	// 2) chatRoomId 기준으로 해당 방의 세션 Set을 조회/생성
	// 3) JOIN/LEAVE는 세션 Set 갱신 및 시스템 메시지 구성, TALK는 DB 저장 + 읽음 처리
	// 4) 같은 방에 연결된 세션들에게만 브로드캐스트
	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		final String payload = message.getPayload();
		log.info("WS payload from {} => {}", session.getId(), payload);

		// 인증(핸드셰이크)에서 저장한 userId 확인: 없으면 정책 위반으로 연결 종료
		final Object userIdAttr = session.getAttributes().get("userId");
		if (userIdAttr == null) {
			log.warn("Unauthenticated WS session, closing: {}", session.getId());
			session.close();
			return;
		}
		final String authenticatedUserId = userIdAttr.toString();

		final ChatMessageDto dto = objectMapper.readValue(payload, ChatMessageDto.class);
		if (dto.getChatRoomId() == null) {
			log.warn("chatRoomId is null. Ignore message.");
			return;
		}

		// 보안: 클라이언트가 보내온 senderUserId는 신뢰하지 않고 서버가 인증된 값으로 덮어씀
		dto.setSenderUserId(authenticatedUserId);

		roomIdToSessions.computeIfAbsent(dto.getChatRoomId(), k -> new HashSet<>());
		final Set<WebSocketSession> roomSessions = roomIdToSessions.get(dto.getChatRoomId());

		switch (dto.getMessageType()) {
			case JOIN -> {
				// 방에 세션 추가 및 시스템 메시지 전송 (입장 알림)
				roomSessions.add(session);
				dto.setMessage("님이 입장하셨습니다.");
				log.info("WS JOIN: userId={} roomId={}", authenticatedUserId, dto.getChatRoomId());
			}
			case LEAVE -> {
				// 방에서 세션 제거 및 시스템 메시지 전송 (퇴장 알림)
				roomSessions.remove(session);
				dto.setMessage("님이 퇴장하셨습니다.");
				log.info("WS LEAVE: userId={} roomId={}", authenticatedUserId, dto.getChatRoomId());
				try {
					chatService.leaveRoom(dto.getChatRoomId(), authenticatedUserId);
				} catch (Exception e) {
					log.warn("leaveRoom failed: userId={} roomId={} err={}", authenticatedUserId, dto.getChatRoomId(), e.getMessage());
				}
			}
			case TALK -> {
				// 일반 대화는 전달된 message 그대로 사용 (DB에는 ChatService로 영속 저장)
				log.info("WS TALK: userId={} roomId={} message={}", authenticatedUserId, dto.getChatRoomId(), dto.getMessage());
			}
		}

		// TALK 메시지는 DB에 저장하여 기록 및 lastMessageAt 갱신
		if (dto.getMessageType() == ChatMessageDto.MessageType.TALK) {
			chatService.saveMessage(dto.getChatRoomId(), dto.getSenderUserId(), dto.getMessage());
			// 발신자는 자신의 메시지까지 읽은 것으로 처리 (읽음 뱃지/미확인 수 계산의 기준 시각 업데이트)
			if (dto.getSenderUserId() != null) {
				try { chatService.markRead(dto.getChatRoomId(), dto.getSenderUserId()); } catch (Exception ignore) {}
			}
		}

		// 동일한 방에 속한 세션에게만 브로드캐스트 (다른 방 사용자에게는 전송되지 않음)
		final String outbound = objectMapper.writeValueAsString(dto);
		int delivered = 0;
		for (WebSocketSession s : roomSessions) {
			if (s.isOpen()) {
				s.sendMessage(new TextMessage(outbound));
				delivered++;
			}
		}
		log.info("WS broadcast: roomId={} receivers={} payload={}", dto.getChatRoomId(), delivered, outbound);
	}

	// 연결 종료 시 호출: 전체 세션 및 각 방에서 해당 세션 제거 (메모리 릭 방지)
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		log.info("WS disconnected: {} ({})", session.getId(), status);
		allSessions.remove(session);
		// 모든 방에서 해당 세션 삭제
		for (Set<WebSocketSession> roomSessions : roomIdToSessions.values()) {
			roomSessions.remove(session);
		}
		// 종료 알림은 클라이언트 기준으로 처리(서버는 단순 정리)
	}
}


