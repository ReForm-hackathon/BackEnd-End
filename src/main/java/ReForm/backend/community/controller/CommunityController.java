package ReForm.backend.community.controller;

import ReForm.backend.community.Community;
import ReForm.backend.community.repository.CommunityRepository;
import ReForm.backend.community.repository.CommunityLikeRepository;
import ReForm.backend.community.repository.CommunityCommentRepository;
import ReForm.backend.user.User;
import ReForm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CommunityController {

    private final CommunityRepository communityRepository;
    private final CommunityLikeRepository communityLikeRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final UserRepository userRepository;

    /**
     * 커뮤니티 게시글 작성
     * - 경로: POST /community
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PostMapping("/board")
    public ResponseEntity<Map<String, Object>> createCommunityPost(@RequestBody CommunityPostRequestDTO request) {
        try {
            // 현재 인증된 사용자 ID 추출
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            log.info("[/community] 게시글 작성 요청 수신 - userId={}, title={}", userId, request.getTitle());

            // 사용자 조회
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            // Community 엔티티 생성
            Community community = Community.builder()
                .user(user)
                .title(request.getTitle())
                .content(request.getContent())
                .image(request.getImage())
                .tagContent(request.getTagContent())
                .createdAt(LocalDateTime.now())
                .build();

            // DB 저장
            Community savedCommunity = communityRepository.save(community);

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("message", "게시글이 성공적으로 작성되었습니다.");
            response.put("communityId", savedCommunity.getCommunityId());
            response.put("title", savedCommunity.getTitle());
            response.put("content", savedCommunity.getContent());
            response.put("image", savedCommunity.getImage());
            response.put("tagContent", savedCommunity.getTagContent());
            response.put("author", savedCommunity.getUser().getUserName());
            response.put("createdAt", savedCommunity.getCreatedAt());

            log.info("[/community] 게시글 작성 완료 - userId={}, communityId={}", userId, savedCommunity.getCommunityId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("[/community] 잘못된 요청 - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/community] 서버 에러", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 게시글 수정
     * - 경로: PUT /board/{board_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PutMapping("/board/{boardId}")
    public ResponseEntity<Map<String, Object>> updateCommunityPost(
            @PathVariable Integer boardId,
            @RequestBody CommunityPostRequestDTO request) {
        try {
            // 현재 인증된 사용자 ID 추출
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            log.info("[/board/{}] 게시글 수정 요청 수신 - userId={}", boardId, userId);

            // 기존 게시글 조회
            Community existingCommunity = communityRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글을 찾을 수 없습니다."));

            // 작성자 확인 (본인만 수정 가능)
            if (!existingCommunity.getUser().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "본인의 게시글만 수정할 수 있습니다."));
            }

            // 게시글 정보 업데이트
            Community updatedCommunity = Community.builder()
                .communityId(existingCommunity.getCommunityId())
                .user(existingCommunity.getUser())
                .title(request.getTitle())
                .content(request.getContent())
                .image(request.getImage())
                .tagContent(request.getTagContent())
                .createdAt(existingCommunity.getCreatedAt()) // 생성일은 유지
                .build();

            // DB 저장
            Community savedCommunity = communityRepository.save(updatedCommunity);

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("message", "게시글이 성공적으로 수정되었습니다.");
            response.put("communityId", savedCommunity.getCommunityId());
            response.put("title", savedCommunity.getTitle());
            response.put("content", savedCommunity.getContent());
            response.put("image", savedCommunity.getImage());
            response.put("tagContent", savedCommunity.getTagContent());
            response.put("author", savedCommunity.getUser().getUserName());
            response.put("createdAt", savedCommunity.getCreatedAt());

            log.info("[/board/{}] 게시글 수정 완료 - userId={}", boardId, userId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("[/board/{}] 잘못된 요청 - {}", boardId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/board/{}] 서버 에러", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 게시글 삭제
     * - 경로: DELETE /delete-board/{board_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @DeleteMapping("/delete-board/{boardId}")
    public ResponseEntity<Map<String, Object>> deleteCommunityPost(@PathVariable Integer boardId) {
        try {
            // 현재 인증된 사용자 ID 추출
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            log.info("[/delete-board/{}] 게시글 삭제 요청 수신 - userId={}", boardId, userId);

            // 기존 게시글 조회
            Community existingCommunity = communityRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글을 찾을 수 없습니다."));

            // 작성자 확인 (본인만 삭제 가능)
            if (!existingCommunity.getUser().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "본인의 게시글만 삭제할 수 있습니다."));
            }

            // 게시글 삭제
            communityRepository.delete(existingCommunity);

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("message", "게시글이 성공적으로 삭제되었습니다.");
            response.put("communityId", boardId);
            response.put("deletedTitle", existingCommunity.getTitle());

            log.info("[/delete-board/{}] 게시글 삭제 완료 - userId={}", boardId, userId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("[/delete-board/{}] 잘못된 요청 - {}", boardId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/delete-board/{}] 서버 에러", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 게시글 좋아요
     * - 경로: POST /board/{board_id}/like
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PostMapping("/board/{boardId}/like")
    public ResponseEntity<Map<String, Object>> likeCommunityPost(@PathVariable Integer boardId) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            Community community = communityRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글을 찾을 수 없습니다."));

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            ReForm.backend.community.CommunityLike.CommunityLikeId id = 
                new ReForm.backend.community.CommunityLike.CommunityLikeId(boardId, userId);

            // 이미 좋아요 했는지 확인
            if (communityLikeRepository.findById(id).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "이미 좋아요를 눌렀습니다."));
            }

            ReForm.backend.community.CommunityLike like = ReForm.backend.community.CommunityLike.builder()
                .id(id)
                .community(community)
                .user(user)
                .likedAt(java.time.LocalDateTime.now())
                .build();

            communityLikeRepository.save(like);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "좋아요가 등록되었습니다.");
            resp.put("boardId", boardId);
            resp.put("userId", userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/board/{}/like] 에러", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 게시글 좋아요 삭제
     * - 경로: DELETE /board/{board_id}/delete-like
     * - 헤더: Authorization: Bearer {access_token}
     */
    @DeleteMapping("/board/{boardId}/delete-like")
    public ResponseEntity<Map<String, Object>> deleteCommunityLike(@PathVariable Integer boardId) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            // 좋아요 존재 여부 확인
            ReForm.backend.community.CommunityLike.CommunityLikeId id = 
                new ReForm.backend.community.CommunityLike.CommunityLikeId(boardId, userId);
            var likeOpt = communityLikeRepository.findById(id);
            if (likeOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "좋아요가 존재하지 않습니다."));
            }

            communityLikeRepository.deleteById(id);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "좋아요가 삭제되었습니다.");
            resp.put("boardId", boardId);
            resp.put("userId", userId);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("[/board/{}/delete-like] 에러", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 게시글 목록 조회 (제목, 작성자만)
     * - 경로: GET /community
     * - 헤더: Authorization: Bearer {access_token}
     */
    @GetMapping("/community")
    public ResponseEntity<Map<String, Object>> getCommunityList() {
        try {
            // 인증 확인
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            List<Community> communities = communityRepository.findAllByOrderByCreatedAtDesc();

            List<Map<String, Object>> items = communities.stream()
                .map(c -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("communityId", c.getCommunityId());
                    item.put("title", c.getTitle());
                    item.put("author", c.getUser() != null ? c.getUser().getUserName() : "");
                    long likeCount = communityLikeRepository.countByCommunity_CommunityId(c.getCommunityId());
                    long commentCount = communityCommentRepository.countByCommunity_CommunityId(c.getCommunityId());
                    item.put("likeCount", likeCount);
                    item.put("commentCount", commentCount);
                    return item;
                })
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("totalCount", items.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[/community] 목록 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 제목 검색 (부분일치)
     * - 경로: GET /board/search/{string}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @GetMapping("/board/search/{string}")
    public ResponseEntity<Map<String, Object>> searchCommunityByTitle(@PathVariable("string") String keyword) {
        try {
            // 인증 확인
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            String query = keyword == null ? "" : keyword;
            List<Community> communities = communityRepository.findByTitleContainingOrderByCreatedAtDesc(query);

            List<Map<String, Object>> items = communities.stream()
                .map(c -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("communityId", c.getCommunityId());
                    item.put("title", c.getTitle());
                    item.put("author", c.getUser() != null ? c.getUser().getUserName() : "");
                    item.put("createdAt", c.getCreatedAt());
                    return item;
                })
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("totalCount", items.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[/board/search/{}] 검색 실패", keyword, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 좋아요 상위 5개 커뮤니티 게시글
     * - 경로: GET /community/top-liked
     * - 응답: 이미지(있으면), 제목, 댓글 수, 좋아요 수
     */
    @GetMapping("/community/top-liked")
    public ResponseEntity<Map<String, Object>> getTopLikedCommunities() {
        try {
            // 인증 확인
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            var rows = communityLikeRepository.findTopLikedCommunities(PageRequest.of(0, 5));
            java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();

            for (Object[] row : rows) {
                Integer cid = (Integer) row[0];
                Long likeCount = (Long) row[1];
                var communityOpt = communityRepository.findById(cid);
                if (communityOpt.isEmpty()) continue;
                var c = communityOpt.get();
                long commentCount = communityCommentRepository.countByCommunity_CommunityId(cid);

                Map<String, Object> item = new HashMap<>();
                item.put("communityId", cid);
                item.put("title", c.getTitle());
                item.put("image", c.getImage()); // null 가능: 이미지 없으면 null
                item.put("likeCount", likeCount);
                item.put("commentCount", commentCount);
                items.add(item);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("totalCount", items.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[/community/top-liked] 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 게시글 상세 조회 (전체 필드)
     * - 경로: GET /community/{community_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @GetMapping("/community/{communityId}")
    public ResponseEntity<Map<String, Object>> getCommunityDetail(@PathVariable Integer communityId) {
        try {
            // 인증 확인
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글을 찾을 수 없습니다."));

            Map<String, Object> body = new HashMap<>();
            body.put("communityId", community.getCommunityId());
            body.put("title", community.getTitle());
            body.put("author", community.getUser() != null ? community.getUser().getUserName() : null);
            body.put("createdAt", community.getCreatedAt());
            body.put("image", community.getImage());
            body.put("content", community.getContent());
            body.put("tagContent", community.getTagContent());
            // counts
            long likeCount = communityLikeRepository.countByCommunity_CommunityId(communityId);
            long commentCount = communityCommentRepository.countByCommunity_CommunityId(communityId);
            body.put("likeCount", likeCount);
            body.put("commentCount", commentCount);

            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/community/{}] 상세 조회 실패", communityId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 게시글 댓글 작성
     * - 경로: POST /board/{board_id}/comment
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PostMapping("/board/{boardId}/comment")
    public ResponseEntity<Map<String, Object>> createCommunityComment(
            @PathVariable Integer boardId,
            @RequestBody CreateCommentRequest request) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            Community community = communityRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글을 찾을 수 없습니다."));

            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            ReForm.backend.community.CommunityComment comment = ReForm.backend.community.CommunityComment.builder()
                .community(community)
                .user(user)
                .content(request.getContent())
                .createdAt(java.time.LocalDateTime.now())
                .build();

            ReForm.backend.community.CommunityComment saved = communityCommentRepository.save(comment);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "댓글이 등록되었습니다.");
            resp.put("commentId", saved.getCommentId());
            resp.put("boardId", boardId);
            resp.put("author", user.getUserName());
            resp.put("content", saved.getContent());
            resp.put("createdAt", saved.getCreatedAt());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/board/{}/comment] 에러", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 게시글 댓글 수정
     * - 경로: PUT /board/{board_id}/comment/{comment_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @PutMapping("/board/{boardId}/comment/{commentId}")
    public ResponseEntity<Map<String, Object>> updateCommunityComment(
            @PathVariable Integer boardId,
            @PathVariable Integer commentId,
            @RequestBody CreateCommentRequest request) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            var comment = communityCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

            if (comment.getCommunity() == null || !comment.getCommunity().getCommunityId().equals(boardId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "해당 게시글의 댓글이 아닙니다."));
            }

            if (!comment.getUser().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "본인의 댓글만 수정할 수 있습니다."));
            }

            // 엔티티 재생성 없이 필드만 변경하려면 엔티티에 setter가 없으므로 빌더 재구성
            var updated = ReForm.backend.community.CommunityComment.builder()
                .commentId(comment.getCommentId())
                .community(comment.getCommunity())
                .user(comment.getUser())
                .content(request.getContent())
                .createdAt(comment.getCreatedAt())
                .build();

            var saved = communityCommentRepository.save(updated);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "댓글이 수정되었습니다.");
            resp.put("boardId", boardId);
            resp.put("commentId", saved.getCommentId());
            resp.put("content", saved.getContent());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/board/{}/comment/{}] 에러", boardId, commentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 에러가 발생했습니다."));
        }
    }

    /**
     * 커뮤니티 게시글 댓글 삭제
     * - 경로: DELETE /board/{board_id}/delete-comment/{comment_id}
     * - 헤더: Authorization: Bearer {access_token}
     */
    @DeleteMapping("/board/{boardId}/delete-comment/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteCommunityComment(
            @PathVariable Integer boardId,
            @PathVariable Integer commentId) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "인증이 필요합니다."));
            }

            var comment = communityCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

            if (comment.getCommunity() == null || !comment.getCommunity().getCommunityId().equals(boardId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "해당 게시글의 댓글이 아닙니다."));
            }

            if (!comment.getUser().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "본인의 댓글만 삭제할 수 있습니다."));
            }

            communityCommentRepository.deleteById(commentId);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "댓글이 삭제되었습니다.");
            resp.put("boardId", boardId);
            resp.put("commentId", commentId);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[/board/{}/delete-comment/{}] 에러", boardId, commentId, e);
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
     * 커뮤니티 게시글 작성 요청 DTO
     */
    public static class CommunityPostRequestDTO {
        private String title;
        private String content;
        private String image;
        private String tagContent;

        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
        
        public String getTagContent() { return tagContent; }
        public void setTagContent(String tagContent) { this.tagContent = tagContent; }
    }
}
