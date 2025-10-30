package ReForm.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 클라이언트(Web, iOS 등)와 주고받을 채팅 메시지 DTO
 * - messageType: 입장/대화/퇴장 구분용
 * - chatRoomId: 채팅방 식별자 (같은 방에 속한 세션에게만 전송)
 * - message: 실제 전송할 내용(시스템 안내문 포함)
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageDto {
	public enum MessageType {
		JOIN, TALK, LEAVE
	}

	private MessageType messageType;
	private Long chatRoomId;
	private String message;
	// 선택: 발신자 식별(WS에서 인증 미적용 시 iOS/Web이 명시적으로 넣어줌)
	private String senderUserId;
}


