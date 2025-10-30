package ReForm.backend.chat.repository;

import ReForm.backend.chat.entity.ChatParticipant;
import ReForm.backend.chat.entity.ChatRoom;
import ReForm.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {
	List<ChatParticipant> findByUser(User user);
	List<ChatParticipant> findByRoom(ChatRoom room);
	Optional<ChatParticipant> findByRoomAndUser(ChatRoom room, User user);
	void deleteByRoom(ChatRoom room);
}


