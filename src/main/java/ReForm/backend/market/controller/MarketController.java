package ReForm.backend.market.controller;

import ReForm.backend.market.Market;
import ReForm.backend.market.repository.MarketRepository;
import ReForm.backend.market.repository.MarketLikeRepository;
import ReForm.backend.market.repository.MarketCommentRepository;
import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/market")
@Slf4j
public class MarketController {

    private final MarketRepository marketRepository;
    private final UserRepository userRepository;
    private final MarketLikeRepository marketLikeRepository;
    private final MarketCommentRepository marketCommentRepository;

    /**
     * 마켓 제품 등록
     * - 경로: POST /market/item
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PostMapping("/item")
    public ResponseEntity<Map<String, Object>> createMarketItem(@RequestBody MarketItemRequestDTO request) {
        try {
            // 현재 인증된 사용자 ID 추출
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            log.info("[/market/item] 요청 수신 - userId={}, title={}", userId, request.getTitle());

            // 사용자 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            // Market 엔티티 생성
            Market market = Market.builder()
                    .user(user)
                    .title(request.getTitle())
                    .content(request.getContent())
                    .tag(request.getTag())
                    .image(request.getImage())
                    .price(request.getPrice())
                    .isDonation(request.getIsDonation())
                    .createdAt(LocalDateTime.now())
                    .build();

            // DB 저장
            Market savedMarket = marketRepository.save(market);

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("message", "제품이 성공적으로 등록되었습니다.");
            response.put("marketId", savedMarket.getMarketId());
            response.put("title", savedMarket.getTitle());
            response.put("tag", savedMarket.getTag());
            response.put("price", savedMarket.getPrice());
            response.put("isDonation", savedMarket.getIsDonation());
            response.put("createdAt", savedMarket.getCreatedAt());

            log.info("[/market/item] 제품 등록 완료 - userId={}, marketId={}", userId, savedMarket.getMarketId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("[/market/item] 잘못된 요청 - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/market/item] 서버 에러", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 마켓 댓글 삭제
     * - 경로: DELETE /market/item/{market_id}/delete-comment/{comment_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @DeleteMapping("/item/{marketId}/delete-comment/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteMarketComment(
            @PathVariable Integer marketId,
            @PathVariable Integer commentId) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            var comment = marketCommentRepository.findById(commentId)
                    .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

            if (comment.getMarket() == null || !comment.getMarket().getMarketId().equals(marketId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "해당 마켓의 댓글이 아닙니다."));
            }

            if (!comment.getUser().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "본인의 댓글만 삭제할 수 있습니다."));
            }

            marketCommentRepository.deleteById(commentId);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "댓글이 삭제되었습니다.");
            resp.put("marketId", marketId);
            resp.put("commentId", commentId);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/market/item/{}/delete-comment/{}] 에러", marketId, commentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 마켓 댓글 수정
     * - 경로: PUT /market/item/{market_id}/comment/{comment_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PutMapping("/item/{marketId}/comment/{commentId}")
    public ResponseEntity<Map<String, Object>> updateMarketComment(
            @PathVariable Integer marketId,
            @PathVariable Integer commentId,
            @RequestBody CreateCommentRequest request) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            var comment = marketCommentRepository.findById(commentId)
                    .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

            if (comment.getMarket() == null || !comment.getMarket().getMarketId().equals(marketId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "해당 마켓의 댓글이 아닙니다."));
            }

            if (!comment.getUser().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "본인의 댓글만 수정할 수 있습니다."));
            }

            // 엔티티 재생성 없이 필드만 변경하려면 엔티티에 setter가 없으므로 빌더 재구성
            var updated = ReForm.backend.market.MarketComment.builder()
                    .commentId(comment.getCommentId())
                    .market(comment.getMarket())
                    .user(comment.getUser())
                    .content(request.getContent())
                    .createdAt(comment.getCreatedAt())
                    .build();

            var saved = marketCommentRepository.save(updated);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "댓글이 수정되었습니다.");
            resp.put("marketId", marketId);
            resp.put("commentId", saved.getCommentId());
            resp.put("content", saved.getContent());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/market/item/{}/comment/{}] 에러", marketId, commentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 마켓 댓글 작성
     * - 경로: POST /market/item/{market_id}/comment
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PostMapping("/item/{marketId}/comment")
    public ResponseEntity<Map<String, Object>> createMarketComment(
            @PathVariable Integer marketId,
            @RequestBody CreateCommentRequest request) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            Market market = marketRepository.findById(marketId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 제품을 찾을 수 없습니다."));

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            ReForm.backend.market.MarketComment comment = ReForm.backend.market.MarketComment.builder()
                    .market(market)
                    .user(user)
                    .content(request.getContent())
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            ReForm.backend.market.MarketComment saved = marketCommentRepository.save(comment);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "댓글이 등록되었습니다.");
            resp.put("commentId", saved.getCommentId());
            resp.put("marketId", marketId);
            resp.put("author", user.getUserName());
            resp.put("content", saved.getContent());
            resp.put("createdAt", saved.getCreatedAt());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/market/item/{}/comment] 에러", marketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    public static class CreateCommentRequest {
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    /**
     * 마켓 좋아요 삭제
     * - 경로: DELETE /market/item/{market_id}/delete-like
     * - 헤더: Authorization: Bearer {access_token}
     */
    @DeleteMapping("/item/{marketId}/delete-like")
    public ResponseEntity<Map<String, Object>> deleteMarketLike(@PathVariable Integer marketId) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            // 좋아요 존재 여부 확인
            ReForm.backend.market.MarketLike.MarketLikeId id = new ReForm.backend.market.MarketLike.MarketLikeId(marketId, userId);
            var likeOpt = marketLikeRepository.findById(id);
            if (likeOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "좋아요가 존재하지 않습니다."));
            }

            marketLikeRepository.deleteById(id);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "좋아요가 삭제되었습니다.");
            resp.put("marketId", marketId);
            resp.put("userId", userId);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("[/market/item/{}/delete-like] 에러", marketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 마켓 좋아요 등록
     * - 경로: POST /market/item/{market_id}/like
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PostMapping("/item/{marketId}/like")
    public ResponseEntity<Map<String, Object>> likeMarketItem(@PathVariable Integer marketId) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            Market market = marketRepository.findById(marketId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 제품을 찾을 수 없습니다."));

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            ReForm.backend.market.MarketLike.MarketLikeId id = new ReForm.backend.market.MarketLike.MarketLikeId(marketId, userId);

            // 이미 좋아요 했는지 확인
            if (marketLikeRepository.findById(id).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "이미 좋아요를 눌렀습니다."));
            }

            ReForm.backend.market.MarketLike like = ReForm.backend.market.MarketLike.builder()
                    .id(id)
                    .market(market)
                    .user(user)
                    .likedAt(java.time.LocalDateTime.now())
                    .build();

            marketLikeRepository.save(like);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "좋아요가 등록되었습니다.");
            resp.put("marketId", marketId);
            resp.put("userId", userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/market/item/{}/like] 에러", marketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 마켓 제품 상세 조회 (전체 필드)
     * - 경로: GET /market/{market_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @GetMapping("/{marketId}")
    public ResponseEntity<Map<String, Object>> getMarketDetail(@PathVariable Integer marketId) {
        try {
            // 인증 확인
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            Market market = marketRepository.findById(marketId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 제품을 찾을 수 없습니다."));

            Map<String, Object> body = new HashMap<>();
            body.put("marketId", market.getMarketId());
            body.put("title", market.getTitle());
            body.put("author", market.getUser() != null ? market.getUser().getUserName() : null);
        body.put("authorProfileImageUrl", market.getUser() != null ? market.getUser().getProfileImageUrl() : null);
            body.put("createdAt", market.getCreatedAt());
            body.put("price", market.getPrice());
            body.put("tag", market.getTag());
            body.put("image", market.getImage());
            body.put("content", market.getContent());
            body.put("isDonation", market.getIsDonation());

            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/market/{}] 상세 조회 실패", marketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 마켓 목록 조회 (모든 컬럼 반환)
     * - 경로: GET /market
     * - 헤더: Authorization: Bearer {access_token}
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMarketList() {
        try {
            // 인증 확인
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            List<Market> markets = marketRepository.findAllByOrderByCreatedAtDesc();

            List<Map<String, Object>> items = markets.stream()
                    .map(m -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("marketId", m.getMarketId());
                        item.put("userId", m.getUser() != null ? m.getUser().getUserId() : null);
                        item.put("author", m.getUser() != null ? m.getUser().getUserName() : null);
                    item.put("authorProfileImageUrl", m.getUser() != null ? m.getUser().getProfileImageUrl() : null);
                        item.put("title", m.getTitle());
                        item.put("content", m.getContent());
                        item.put("tag", m.getTag());
                        item.put("image", m.getImage());
                        item.put("price", m.getPrice());
                        item.put("isDonation", m.getIsDonation());
                        item.put("createdAt", m.getCreatedAt());
                        return item;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("totalCount", items.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[/market] 목록 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 마켓 제목 검색 (부분일치)
     * - 경로: GET /market/search/{string}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @GetMapping("/search/{string}")
    public ResponseEntity<Map<String, Object>> searchMarketByTitle(@PathVariable("string") String keyword) {
        try {
            // 인증 확인
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            String query = keyword == null ? "" : keyword;
            List<Market> markets = marketRepository.findByTitleContainingOrderByCreatedAtDesc(query);

            List<Map<String, Object>> items = markets.stream()
                    .map(m -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("marketId", m.getMarketId());
                        item.put("title", m.getTitle());
                        item.put("author", m.getUser() != null ? m.getUser().getUserName() : "");
                        item.put("createdAt", m.getCreatedAt());
                        return item;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("totalCount", items.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[/market/search/{}] 검색 실패", keyword, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 마켓 제품 수정
     * - 경로: PUT /market/item/{market_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PutMapping("/item/{marketId}")
    public ResponseEntity<Map<String, Object>> updateMarketItem(
            @PathVariable Integer marketId,
            @RequestBody MarketItemRequestDTO request) {
        try {
            // 현재 인증된 사용자 ID 추출
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            log.info("[/market/item/{}] 수정 요청 수신 - userId={}", marketId, userId);

            // 기존 마켓 아이템 조회
            Market existingMarket = marketRepository.findById(marketId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 제품을 찾을 수 없습니다."));

            // 작성자 확인 (본인만 수정 가능)
            if (!existingMarket.getUser().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "본인의 제품만 수정할 수 있습니다."));
            }

            // 제품 정보 업데이트
            Market updatedMarket = Market.builder()
                    .marketId(existingMarket.getMarketId())
                    .user(existingMarket.getUser())
                    .title(request.getTitle())
                    .content(request.getContent())
                    .tag(request.getTag())
                    .image(request.getImage())
                    .price(request.getPrice())
                    .isDonation(request.getIsDonation())
                    .createdAt(existingMarket.getCreatedAt()) // 생성일은 유지
                    .build();

            // DB 저장
            Market savedMarket = marketRepository.save(updatedMarket);

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("message", "제품이 성공적으로 수정되었습니다.");
            response.put("marketId", savedMarket.getMarketId());
            response.put("title", savedMarket.getTitle());
            response.put("content", savedMarket.getContent());
            response.put("tag", savedMarket.getTag());
            response.put("image", savedMarket.getImage());
            response.put("price", savedMarket.getPrice());
            response.put("isDonation", savedMarket.getIsDonation());
            response.put("createdAt", savedMarket.getCreatedAt());

            log.info("[/market/item/{}] 제품 수정 완료 - userId={}", marketId, userId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("[/market/item/{}] 잘못된 요청 - {}", marketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/market/item/{}] 서버 에러", marketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 마켓 제품 삭제
     * - 경로: DELETE /market/delete-item/{market_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @DeleteMapping("/delete-item/{marketId}")
    public ResponseEntity<Map<String, Object>> deleteMarketItem(@PathVariable Integer marketId) {
        try {
            // 현재 인증된 사용자 ID 추출
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "인증이 필요합니다."));
            }

            log.info("[/market/delete-item/{}] 삭제 요청 수신 - userId={}", marketId, userId);

            // 기존 마켓 아이템 조회
            Market existingMarket = marketRepository.findById(marketId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 제품을 찾을 수 없습니다."));

            // 작성자 확인 (본인만 삭제 가능)
            if (!existingMarket.getUser().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "본인의 제품만 삭제할 수 있습니다."));
            }

            // 제품 삭제
            marketRepository.delete(existingMarket);

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("message", "제품이 성공적으로 삭제되었습니다.");
            response.put("marketId", marketId);
            response.put("deletedTitle", existingMarket.getTitle());

            log.info("[/market/delete-item/{}] 제품 삭제 완료 - userId={}", marketId, userId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("[/market/delete-item/{}] 잘못된 요청 - {}", marketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/market/delete-item/{}] 서버 에러", marketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 현재 인증된 사용자 ID 추출
     */
    private String getCurrentUserId() {
        try {
            String email = org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication() != null ?
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName() : null;

            if (email == null) {
                return null;
            }

            return userRepository.findByEmail(email)
                    .map(User::getUserId)
                    .orElse(null);
        } catch (Exception e) {
            log.error("사용자 ID 추출 실패", e);
            return null;
        }
    }

    /**
     * 마켓 제품 등록 요청 DTO
     */
    public static class MarketItemRequestDTO {
        private String title;
        private String content;
        private String tag;
        private String image;
        private Integer price;
        private Boolean isDonation;

        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getTag() { return tag; }
        public void setTag(String tag) { this.tag = tag; }

        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }

        public Integer getPrice() { return price; }
        public void setPrice(Integer price) { this.price = price; }

        public Boolean getIsDonation() { return isDonation; }
        public void setIsDonation(Boolean isDonation) { this.isDonation = isDonation; }
    }
}
