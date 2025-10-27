package ReForm.backend.market;

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

@Table(name = "market")
public class Market {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "market_id")
	private Integer marketId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "title")
	private String title;

	@Column(name = "content")
	private String content;

	@Column(name = "image")
	private String image;

	@Column(name = "price")
	private Integer price;

	@Column(name = "is_donation")
	private Boolean isDonation;

	@Column(name = "created_at")
	private LocalDateTime createdAt;
}


