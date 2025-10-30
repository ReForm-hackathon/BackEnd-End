package ReForm.backend.chat.service;

import ReForm.backend.chat.entity.ChatMessage;
import ReForm.backend.chat.entity.ChatParticipant;
import ReForm.backend.chat.entity.ChatRoom;
import ReForm.backend.chat.repository.ChatMessageRepository;
import ReForm.backend.chat.repository.ChatParticipantRepository;
import ReForm.backend.chat.repository.ChatRoomRepository;
import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 채팅 도메인 서비스
 * 책임
 * - 채팅방 수명주기: 생성, 내 방 조회, 나가기
 * - 메시지 영속화: TALK 수신 시 DB 저장, 최근 내역 조회
 * - 읽음 처리: 사용자별 마지막 읽은 시각(lastReadAt) 업데이트 및 미확인 수 계산
 */
@Service
@RequiredArgsConstructor
public class ChatService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final UserRepository userRepository;

	/**
	 * 채팅방 생성 및 참가자 등록
	 * @param creatorUserId 방 생성자 사용자 ID
	 * @param participantUserIds 함께 초대할 사용자 ID 목록(선택)
	 * @param title 방 제목(선택)
	 * @return 생성된 ChatRoom
	 */
	@Transactional
	public ChatRoom createRoom(String creatorUserId, List<String> participantUserIds, String title) {
		// 생성자 유효성 검증
		requireUser(creatorUserId);
		ChatRoom room = ChatRoom.builder()
				.title(title)
				.createdAt(LocalDateTime.now())
				.lastMessageAt(null)
				.build();
		chatRoomRepository.save(room);

		List<String> allUserIds = new ArrayList<>();
		allUserIds.add(creatorUserId);
		if (participantUserIds != null) allUserIds.addAll(participantUserIds);

        // 참가자 엔티티 생성: joinedAt=now, lastReadAt는 MySQL 허용 범위의 과거 시각으로 초기화
        //  - 주의: MySQL DATETIME은 1000-01-01 ~ 9999-12-31 범위이므로 LocalDateTime.MIN을 사용할 수 없음
        final LocalDateTime initialLastReadAt = LocalDateTime.of(1970, 1, 1, 0, 0);
		for (String uid : allUserIds) {
			User u = requireUser(uid);
			ChatParticipant p = ChatParticipant.builder()
					.room(room)
					.user(u)
					.joinedAt(LocalDateTime.now())
                    .lastReadAt(initialLastReadAt)
					.build();
			chatParticipantRepository.save(p);
		}
		return room;
	}

	/**
	 * 내가 참여 중인 채팅방 목록 조회
	 */
	@Transactional(readOnly = true)
	public List<ChatRoom> findRoomsOfUser(String userId) {
		User user = requireUser(userId);
		List<ChatParticipant> parts = chatParticipantRepository.findByUser(user);
		List<ChatRoom> rooms = new ArrayList<>();
		for (ChatParticipant p : parts) {
			rooms.add(p.getRoom());
		}
		return rooms;
	}

	/**
	 * 메시지 저장: TALK 수신 시 사용. 방의 lastMessageAt 갱신 포함
	 */
	@Transactional
	public ChatMessage saveMessage(Long roomId, String senderUserId, String content) {
		ChatRoom room = requireRoom(roomId);
		User sender = senderUserId != null ? requireUser(senderUserId) : null;
		ChatMessage msg = ChatMessage.builder()
				.room(room)
				.sender(sender)
				.content(content)
				.createdAt(LocalDateTime.now())
				.build();
		chatMessageRepository.save(msg);
		room.touchLastMessageAt(msg.getCreatedAt());
		chatRoomRepository.save(room);
		return msg;
	}

	/**
	 * 최근 메시지 내역 조회 (시간 오름차순, size개)
	 */
	@Transactional(readOnly = true)
	public List<ChatMessage> loadRecentMessages(Long roomId, int size) {
		ChatRoom room = requireRoom(roomId);
		return chatMessageRepository.findByRoomOrderByCreatedAtAsc(room, PageRequest.of(0, size));
	}

	/**
	 * 읽음 처리: 해당 사용자의 lastReadAt=now
	 * - 미확인 메시지 수 계산의 기준 시각이 됨
	 */
	@Transactional
	public void markRead(Long roomId, String userId) {
		ChatRoom room = requireRoom(roomId);
		User user = requireUser(userId);
		ChatParticipant p = chatParticipantRepository.findByRoomAndUser(room, user)
				.orElseThrow(() -> new IllegalArgumentException("Participant not found"));
		p.markRead(LocalDateTime.now());
		chatParticipantRepository.save(p);
	}

	/**
	 * 방 나가기: 해당 참여자의 leftAt=now 기록
	 */
	@Transactional
	public void leaveRoom(Long roomId, String userId) {
		ChatRoom room = requireRoom(roomId);
		User user = requireUser(userId);
		ChatParticipant p = chatParticipantRepository.findByRoomAndUser(room, user)
				.orElseThrow(() -> new IllegalArgumentException("Participant not found"));
		p.leave(LocalDateTime.now());
		chatParticipantRepository.save(p);

        // 모든 참여자가 나갔는지 확인 후, 모두 나갔다면 방과 관련 데이터 일괄 삭제
        List<ChatParticipant> participants = chatParticipantRepository.findByRoom(room);
        boolean allLeft = true;
        for (ChatParticipant cp : participants) {
            if (cp.getLeftAt() == null) { allLeft = false; break; }
        }
        if (allLeft) {
            // 메시지 → 참가자 → 방 순서로 삭제 (FK 제약 충돌 방지)
            chatMessageRepository.deleteByRoom(room);
            chatParticipantRepository.deleteByRoom(room);
            chatRoomRepository.delete(room);
        }
	}

	/**
	 * 미확인 메시지 수: lastReadAt 이후에 생성된 메시지 개수
	 */
	@Transactional(readOnly = true)
	public long unreadCount(Long roomId, String userId) {
		ChatRoom room = requireRoom(roomId);
		User user = requireUser(userId);
		Optional<ChatParticipant> opt = chatParticipantRepository.findByRoomAndUser(room, user);
		LocalDateTime lastRead = opt.map(ChatParticipant::getLastReadAt).orElse(LocalDateTime.MIN);
		return chatMessageRepository.countByRoomAndCreatedAtAfter(room, lastRead);
	}

	private User requireUser(String userId) {
		return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
	}

	private ChatRoom requireRoom(Long roomId) {
		return chatRoomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("Room not found"));
	}
}


