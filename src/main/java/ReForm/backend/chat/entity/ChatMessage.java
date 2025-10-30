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
@Table(name = "chat_message")
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "message_id")
	private Long id;

	// 메시지가 속한 방
	@ManyToOne
	@JoinColumn(name = "room_id")
	private ChatRoom room;

	// 발신자(시스템 메시지 등 특수 케이스에 대비해 null 허용 가능)
	@ManyToOne
	@JoinColumn(name = "sender_id")
	private User sender;

	// 메시지 본문
	@Column(name = "content", length = 2000)
	private String content;

	// 메시지 생성 시각
	@Column(name = "created_at")
	private LocalDateTime createdAt;
}


