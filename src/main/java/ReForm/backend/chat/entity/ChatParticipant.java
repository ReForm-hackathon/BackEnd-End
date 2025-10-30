package ReForm.backend.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ReForm.backend.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Builder
@Table(name = "chat_participant")
public class ChatParticipant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "participant_id")
	private Long id;

	@ManyToOne
	@JoinColumn(name = "room_id")
	private ChatRoom room;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "joined_at")
	private LocalDateTime joinedAt;

	// 방 나간 시각(재입장 정책에 따라 갱신 가능)
	@Column(name = "left_at")
	private LocalDateTime leftAt;

	// 마지막 읽음 기준 시각(미확인 메시지 수 계산 기준)
	@Column(name = "last_read_at")
	private LocalDateTime lastReadAt;

	public void markRead(LocalDateTime at) {
		this.lastReadAt = at;
	}

	public void leave(LocalDateTime at) {
		this.leftAt = at;
	}
}


