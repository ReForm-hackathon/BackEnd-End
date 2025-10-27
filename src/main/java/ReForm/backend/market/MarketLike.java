package ReForm.backend.market;

import ReForm.backend.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder

@Table(name = "market_like")
public class MarketLike {

	// 복합키를 한 값 객체로 내장하여 PK로 사용 (@Embeddable 클래스 참조)
	@EmbeddedId
	private MarketLikeId id;

	// Market의 식별자(market_id)를 복합키의 일부로 사용하기 위해 @MapsId("marketId")로 동기화
	@ManyToOne
	@MapsId("marketId")
	@JoinColumn(name = "market_id")
	private Market market;

	// User의 식별자(user_id)를 복합키의 일부로 사용하기 위해 @MapsId("userId")로 동기화
	@ManyToOne
	@MapsId("userId")
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "liked_at")
	private LocalDateTime likedAt;

	// market_id + user_id로 구성된 식별자 값 객체
	@Embeddable
	public static class MarketLikeId implements Serializable {
		@Column(name = "market_id")
		private Integer marketId;

		@Column(name = "user_id")
		private String userId;

		public MarketLikeId() {}

		public MarketLikeId(Integer marketId, String userId) {
			this.marketId = marketId;
			this.userId = userId;
		}

		// JPA에서 식별자 값의 동등성 비교를 위해 반드시 equals/hashCode 구현 필요
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			MarketLikeId that = (MarketLikeId) o;
			return Objects.equals(marketId, that.marketId) && Objects.equals(userId, that.userId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(marketId, userId);
		}
	}
}


