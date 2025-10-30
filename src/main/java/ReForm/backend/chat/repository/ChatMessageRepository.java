package ReForm.backend.chat.repository;

import ReForm.backend.chat.entity.ChatMessage;
import ReForm.backend.chat.entity.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	List<ChatMessage> findByRoomOrderByCreatedAtAsc(ChatRoom room, Pageable pageable);
	long countByRoomAndCreatedAtAfter(ChatRoom room, java.time.LocalDateTime instant);
	void deleteByRoom(ChatRoom room);
}


