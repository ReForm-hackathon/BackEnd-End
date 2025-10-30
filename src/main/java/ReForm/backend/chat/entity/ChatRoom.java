package ReForm.backend.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@Table(name = "chat_room")
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "room_id")
	private Long id;

	// 방 표시용 제목 (선택)
	@Column(name = "title")
	private String title;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	// 최근 메시지 생성 시각(정렬/리스트용)
	@Column(name = "last_message_at")
	private LocalDateTime lastMessageAt;

	public void touchLastMessageAt(LocalDateTime at) {
		this.lastMessageAt = at;
	}
}


