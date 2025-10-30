package ReForm.backend.chat.controller;

import ReForm.backend.chat.entity.ChatMessage;
import ReForm.backend.chat.entity.ChatRoom;
import ReForm.backend.chat.service.ChatService;
import ReForm.backend.chat.repository.ChatParticipantRepository;
import ReForm.backend.chat.entity.ChatParticipant;
import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;
import ReForm.backend.user.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 채팅 REST API
 * - 채팅방 생성, 내 채팅방 조회, 채팅 내역 조회, 읽음 처리, 채팅방 나가기
 * - 인증: 요청 헤더의 AccessToken에서 userId를 추출하여 현재 사용자 식별
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ChatParticipantRepository chatParticipantRepository;

	/**
	 * 채팅방 생성
	 * Body: participantUserIds(선택), title(선택)
	 * Resp: 생성된 roomId
	 */
	@PostMapping("/rooms")
    public ResponseEntity<CreateRoomResponse> createRoom(@RequestBody CreateRoomRequest req,
                                                         HttpServletRequest httpRequest) {
        String userId = currentUserId(httpRequest)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token"));
		ChatRoom room = chatService.createRoom(userId, req.participantUserIds, req.title);
		return ResponseEntity.ok(new CreateRoomResponse(room.getId()));
	}

	/**
	 * 내 채팅방 목록 조회
	 */
	@GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> myRooms(HttpServletRequest httpRequest) {
        String userId = currentUserId(httpRequest)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token"));
		return ResponseEntity.ok(chatService.findRoomsOfUser(userId));
	}

    /**
     * 특정 방의 최근 메시지 조회 (개인정보 최소화 응답)
     * - sender는 username, nickname만 포함
     * Query: size(기본 50)
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> messages(@PathVariable Long roomId,
                                                         @RequestParam(defaultValue = "50") int size) {
        List<ChatMessage> messages = chatService.loadRecentMessages(roomId, size);
        List<MessageResponse> response = messages.stream()
                .map(m -> new MessageResponse(
                        m.getId(),
                        m.getContent(),
                        m.getCreatedAt(),
                        m.getSender() == null ? null : new Sender(
                                m.getSender().getUserName(),
                                m.getSender().getNickname()
                        )
                ))
                .toList();
        return ResponseEntity.ok(response);
    }

	/**
	 * 읽음 처리: 해당 방에서 내 lastReadAt 갱신
	 */
	@PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long roomId, HttpServletRequest httpRequest) {
        String userId = currentUserId(httpRequest)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token"));
		chatService.markRead(roomId, userId);
		return ResponseEntity.ok().build();
	}

	/**
	 * 방 나가기: 내 참여 상태에 leftAt 기록
	 */
	@PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long roomId, HttpServletRequest httpRequest) {
        String userId = currentUserId(httpRequest)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token"));
		chatService.leaveRoom(roomId, userId);
		return ResponseEntity.ok().build();
	}

    // 헤더의 AT에서 우선 userId를, 없으면 email을 추출하여 userId로 변환
	private Optional<String> currentUserId(HttpServletRequest request) {
        Optional<String> accessToken = jwtService.extractAccessToken(request);
        // 1) userId 클레임 우선
        Optional<String> byUserId = accessToken.flatMap(jwtService::extractUserId);
        if (byUserId.isPresent()) return byUserId;
        // 2) email 클레임으로 조회 후 userId 변환
        return accessToken
                .flatMap(jwtService::extractEmail)
                .flatMap(email -> userRepository.findFirstByEmailOrderByCreatedAtDesc(email).map(User::getUserId));
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CreateRoomRequest {
		public List<String> participantUserIds; // 방 생성 시 함께 초대할 사용자 목록 (현재 사용자 제외)
		public String title;
	}

	@Getter
	@AllArgsConstructor
	public static class CreateRoomResponse {
		public Long roomId;
	}

    /**
     * 채팅방 검색: 닉네임으로 내 채팅방 목록을 필터링
     * - Path: GET /api/chat/search/{usernickname}
     * - 응답: 방 ID, 참여자 요약, 마지막 메시지 시각, 내 미읽음 수
     */
    @GetMapping("/search/{usernickname}")
    public ResponseEntity<List<ChatSearchResult>> searchRooms(@PathVariable("usernickname") String nickname,
                                                              HttpServletRequest httpRequest) {
        String myUserId = currentUserId(httpRequest)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token"));

        // 내가 속한 방 목록에서, 상대 참여자 중 닉네임이 포함되는 방만 선택
        List<ChatRoom> myRooms = chatService.findRoomsOfUser(myUserId);
        String query = nickname == null ? "" : nickname.toLowerCase();

        List<ChatSearchResult> results = new java.util.ArrayList<>();
        for (ChatRoom room : myRooms) {
            List<ChatParticipant> parts = chatParticipantRepository.findByRoom(room);
            boolean matched = false;
            List<ParticipantSummary> participantSummaries = new java.util.ArrayList<>();
            for (ChatParticipant p : parts) {
                ReForm.backend.user.User u = p.getUser();
                participantSummaries.add(new ParticipantSummary(
                        u.getUserId(), u.getUserName(), u.getNickname()
                ));
                if (!u.getUserId().equals(myUserId)) {
                    String nn = u.getNickname() == null ? "" : u.getNickname().toLowerCase();
                    if (query.isBlank() || nn.contains(query)) matched = true;
                }
            }
            if (!matched) continue;

            long unread = 0L;
            try { unread = chatService.unreadCount(room.getId(), myUserId); } catch (Exception ignore) {}
            results.add(new ChatSearchResult(
                    room.getId(), participantSummaries, room.getLastMessageAt(), unread
            ));
        }

        // 최신 메시지 순 정렬
        results.sort((a, b) -> {
            java.time.LocalDateTime la = a.getLastMessageAt();
            java.time.LocalDateTime lb = b.getLastMessageAt();
            if (la == null && lb == null) return 0;
            if (la == null) return 1;
            if (lb == null) return -1;
            return lb.compareTo(la);
        });

        return ResponseEntity.ok(results);
    }

    @Getter
    @AllArgsConstructor
    public static class ChatSearchResult {
        private Long roomId;
        private List<ParticipantSummary> participants;
        private java.time.LocalDateTime lastMessageAt;
        private long unreadCount;
    }

    @Getter
    @AllArgsConstructor
    public static class ParticipantSummary {
        private String userId;
        private String userName;
        private String nickname;
    }

    @Getter
    @AllArgsConstructor
    public static class MessageResponse {
        public Long messageId;
        public String content;
        public java.time.LocalDateTime createdAt;
        public Sender sender; // null이면 시스템 메시지 등
    }

    @Getter
    @AllArgsConstructor
    public static class Sender {
        public String userName;
        public String nickname;
    }
}


