package ReForm.backend.chat;

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
@Builder

@Table(name = "chat")
public class Chat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "chat_id")
	private Integer chatId;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "message")
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(name = "sender")
	private Sender sender;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public enum Sender {
		USER, AI
	}
}


