package com.theieltsspells.aimentor.application;

import com.theieltsspells.notification.PersonalizedNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentFriendsService {

    public record FriendSummary(
            UUID userId,
            String fullName,
            String studentCode,
            String mascotType,
            String equippedSkin,
            String equippedTitle,
            int rankPoints,
            int winCount,
            int totalMatches,
            int streakDays,
            String status, // NONE | PENDING_OUT | PENDING_IN | ACCEPTED
            UUID friendshipId
    ) {}

    public record ChallengeDto(
            UUID id,
            UUID challengerId,
            String challengerName,
            UUID opponentId,
            String opponentName,
            String gameMode,
            String difficulty,
            int wagerGems,
            String status,
            String roomCode,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt
    ) {}

    private final JdbcTemplate jdbc;
    private final PersonalizedNotificationService notificationService;
    private final StudentEconomyService economyService;

    @Transactional(readOnly = true)
    public List<FriendSummary> getFriendsAndRequests(UUID meId) {
        return jdbc.query("""
                select f.id as friendship_id,
                       case when f.requester_id = ? then f.addressee_id else f.requester_id end as peer_id,
                       p.full_name,
                       coalesce(sp.student_code, 'HV-' || upper(left(cast(p.id as text), 6))) as student_code,
                       coalesce(scp.mascot_type, 'OWL') as mascot_type,
                       w.equipped_skin,
                       w.equipped_title,
                       coalesce(w.pvp_rank_points, 120) as rank_points,
                       coalesce(w.pvp_wins, 0) as win_count,
                       coalesce(w.pvp_wins, 0) + coalesce(w.pvp_losses, 0) as total_matches,
                       coalesce(w.streak_days, 0) as streak_days,
                       case
                         when f.status = 'ACCEPTED' then 'ACCEPTED'
                         when f.requester_id = ? then 'PENDING_OUT'
                         else 'PENDING_IN'
                       end as rel_status
                from public.student_friendships f
                join public.profiles p
                  on p.id = case when f.requester_id = ? then f.addressee_id else f.requester_id end
                left join public.student_profiles sp on sp.user_id = p.id
                left join public.student_companion_profiles scp on scp.student_id = p.id
                left join public.student_wallets w on w.student_id = p.id
                where (f.requester_id = ? or f.addressee_id = ?)
                  and f.status in ('ACCEPTED', 'PENDING')
                order by f.updated_at desc
                """,
                (rs, rowNum) -> new FriendSummary(
                        rs.getObject("peer_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("student_code"),
                        rs.getString("mascot_type"),
                        rs.getString("equipped_skin"),
                        rs.getString("equipped_title"),
                        rs.getInt("rank_points"),
                        rs.getInt("win_count"),
                        rs.getInt("total_matches"),
                        rs.getInt("streak_days"),
                        rs.getString("rel_status"),
                        rs.getObject("friendship_id", UUID.class)
                ),
                meId, meId, meId, meId, meId);
    }

    @Transactional(readOnly = true)
    public List<FriendSummary> searchLearners(UUID meId, String query) {
        String clean = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String like = "%" + clean + "%";
        return jdbc.query("""
                select p.id as peer_id,
                       p.full_name,
                       coalesce(sp.student_code, 'HV-' || upper(left(cast(p.id as text), 6))) as student_code,
                       coalesce(scp.mascot_type, 'OWL') as mascot_type,
                       w.equipped_skin,
                       w.equipped_title,
                       coalesce(w.pvp_rank_points, 120) as rank_points,
                       coalesce(w.pvp_wins, 0) as win_count,
                       coalesce(w.pvp_wins, 0) + coalesce(w.pvp_losses, 0) as total_matches,
                       coalesce(w.streak_days, 0) as streak_days,
                       coalesce(
                         case
                           when f.status = 'ACCEPTED' then 'ACCEPTED'
                           when f.status = 'PENDING' and f.requester_id = ? then 'PENDING_OUT'
                           when f.status = 'PENDING' then 'PENDING_IN'
                           else 'NONE'
                         end,
                         'NONE'
                       ) as rel_status,
                       f.id as friendship_id
                from public.profiles p
                left join public.student_profiles sp on sp.user_id = p.id
                left join public.student_companion_profiles scp on scp.student_id = p.id
                left join public.student_wallets w on w.student_id = p.id
                left join public.student_friendships f
                  on least(f.requester_id, f.addressee_id) = least(?, p.id)
                 and greatest(f.requester_id, f.addressee_id) = greatest(?, p.id)
                where p.id <> ?
                  and p.is_active = true
                  and (
                    ? = ''
                    or lower(p.full_name) like ?
                    or lower(coalesce(sp.student_code, '')) like ?
                    or lower(coalesce(p.email, '')) like ?
                  )
                order by coalesce(w.pvp_rank_points, 120) desc, p.full_name asc
                limit 25
                """,
                (rs, rowNum) -> new FriendSummary(
                        rs.getObject("peer_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("student_code"),
                        rs.getString("mascot_type"),
                        rs.getString("equipped_skin"),
                        rs.getString("equipped_title"),
                        rs.getInt("rank_points"),
                        rs.getInt("win_count"),
                        rs.getInt("total_matches"),
                        rs.getInt("streak_days"),
                        rs.getString("rel_status"),
                        rs.getObject("friendship_id", UUID.class)
                ),
                meId, meId, meId, meId, clean, like, like, like);
    }

    @Transactional
    public void sendFriendRequest(UUID meId, UUID targetUserId) {
        if (meId.equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể tự kết bạn với chính mình");
        }
        String myName = jdbc.queryForObject(
                "select coalesce(full_name, 'Học viên') from public.profiles where id = ?",
                String.class, meId);

        List<Map<String, Object>> existing = jdbc.queryForList("""
                select id, status, requester_id from public.student_friendships
                where least(requester_id, addressee_id) = least(?, ?)
                  and greatest(requester_id, addressee_id) = greatest(?, ?)
                """, meId, targetUserId, meId, targetUserId);

        if (existing.isEmpty()) {
            UUID fid = UUID.randomUUID();
            jdbc.update("""
                    insert into public.student_friendships(id, requester_id, addressee_id, status)
                    values (?, ?, ?, 'PENDING')
                    """, fid, meId, targetUserId);

            notificationService.sendDirectNotice(
                    targetUserId,
                    "FRIEND_REQUEST",
                    "Lời mời kết bạn mới",
                    myName + " muốn kết bạn & luyện tập IELTS cùng bạn!",
                    String.format("{\"friendshipId\":\"%s\",\"targetAction\":\"FRIENDS\"}", fid)
            );
        } else {
            UUID fid = (UUID) existing.getFirst().get("id");
            String status = (String) existing.getFirst().get("status");
            if ("DECLINED".equals(status)) {
                jdbc.update("""
                        update public.student_friendships
                        set requester_id = ?, addressee_id = ?, status = 'PENDING', updated_at = now()
                        where id = ?
                        """, meId, targetUserId, fid);
                notificationService.sendDirectNotice(
                        targetUserId,
                        "FRIEND_REQUEST",
                        "Lời mời kết bạn mới",
                        myName + " muốn kết bạn & luyện tập IELTS cùng bạn!",
                        String.format("{\"friendshipId\":\"%s\",\"targetAction\":\"FRIENDS\"}", fid)
                );
            }
        }
    }

    @Transactional
    public void acceptFriendRequest(UUID meId, UUID peerOrFriendshipId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, requester_id, addressee_id from public.student_friendships
                where (id = ? or (requester_id = ? and addressee_id = ?))
                  and addressee_id = ?
                  and status = 'PENDING'
                """, peerOrFriendshipId, peerOrFriendshipId, meId, meId);
        if (rows.isEmpty()) return;

        UUID fid = (UUID) rows.getFirst().get("id");
        UUID requesterId = (UUID) rows.getFirst().get("requester_id");
        jdbc.update("update public.student_friendships set status = 'ACCEPTED', updated_at = now() where id = ?", fid);

        String myName = jdbc.queryForObject(
                "select coalesce(full_name, 'Học viên') from public.profiles where id = ?",
                String.class, meId);
        notificationService.sendDirectNotice(
                requesterId,
                "FRIEND_ACCEPTED",
                "Đã trở thành bạn bè!",
                myName + " đã đồng ý kết bạn. Hãy thách đấu 1v1 ngay nào!",
                String.format("{\"friendshipId\":\"%s\",\"targetAction\":\"FRIENDS\"}", fid)
        );
    }

    @Transactional
    public void removeFriendship(UUID meId, UUID peerOrFriendshipId) {
        jdbc.update("""
                delete from public.student_friendships
                where (id = ? or least(requester_id, addressee_id) = least(?, ?))
                  and (requester_id = ? or addressee_id = ?)
                """, peerOrFriendshipId, meId, peerOrFriendshipId, meId, meId);
    }

    @Transactional(readOnly = true)
    public List<FriendSummary> getRealLeaderboard() {
        return jdbc.query("""
                select p.id as peer_id,
                       p.full_name,
                       coalesce(sp.student_code, 'HV-' || upper(left(cast(p.id as text), 6))) as student_code,
                       coalesce(scp.mascot_type, 'OWL') as mascot_type,
                       w.equipped_skin,
                       w.equipped_title,
                       coalesce(w.pvp_rank_points, 120) as rank_points,
                       coalesce(w.pvp_wins, 0) as win_count,
                       coalesce(w.pvp_wins, 0) + coalesce(w.pvp_losses, 0) as total_matches,
                       coalesce(w.streak_days, 0) as streak_days
                from public.profiles p
                left join public.student_profiles sp on sp.user_id = p.id
                left join public.student_companion_profiles scp on scp.student_id = p.id
                left join public.student_wallets w on w.student_id = p.id
                where p.is_active = true
                  and (sp.user_id is not null or scp.student_id is not null or w.student_id is not null)
                order by coalesce(w.pvp_rank_points, 120) desc, coalesce(w.pvp_wins, 0) desc, p.full_name asc
                limit 50
                """,
                (rs, rowNum) -> new FriendSummary(
                        rs.getObject("peer_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("student_code"),
                        rs.getString("mascot_type"),
                        rs.getString("equipped_skin"),
                        rs.getString("equipped_title"),
                        rs.getInt("rank_points"),
                        rs.getInt("win_count"),
                        rs.getInt("total_matches"),
                        rs.getInt("streak_days"),
                        "NONE",
                        null
                ));
    }

    @Transactional
    public ChallengeDto createChallenge(UUID challengerId, UUID opponentId, String gameMode, String difficulty, int wagerGems) {
        if (challengerId.equals(opponentId)) {
            throw new IllegalArgumentException("Không thể tự thách đấu chính mình");
        }
        int safeWager = Math.max(0, Math.min(100, wagerGems));
        if (safeWager > 0) {
            var wallet = economyService.getWallet(challengerId);
            if (wallet.gems() < safeWager) {
                throw new IllegalStateException("Bạn không đủ Kim cương để đặt cược " + safeWager + " 💎");
            }
        }
        String mode = (gameMode == null || gameMode.isBlank()) ? "SPEED_VOCAB" : gameMode.toUpperCase(Locale.ROOT);
        String diff = (difficulty == null || difficulty.isBlank()) ? "MEDIUM" : difficulty.toUpperCase(Locale.ROOT);
        UUID id = UUID.randomUUID();
        String roomCode = "duel_" + id.toString().substring(0, 8);

        jdbc.update("""
                insert into public.pvp_challenges
                  (id, challenger_id, opponent_id, game_mode, difficulty, wager_gems, status, room_code)
                values (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, id, challengerId, opponentId, mode, diff, safeWager, roomCode);

        String challengerName = jdbc.queryForObject(
                "select coalesce(full_name, 'Bạn học') from public.profiles where id = ?",
                String.class, challengerId);
        notificationService.sendDirectNotice(
                opponentId,
                "PVP_CHALLENGE",
                "⚔️ Lời mời thách đấu 1v1 mới!",
                challengerName + " thách đấu bạn (" + mode + " • " + diff
                        + (safeWager > 0 ? " • Cược " + safeWager + " 💎" : " • Giao hữu") + ")!",
                String.format("{\"challengeId\":\"%s\",\"roomCode\":\"%s\",\"targetAction\":\"PVP_CHALLENGE\"}", id, roomCode)
        );

        return getChallengeById(id);
    }

    @Transactional(readOnly = true)
    public List<ChallengeDto> listMyChallenges(UUID meId) {
        return jdbc.query("""
                select c.id, c.challenger_id, cp.full_name as challenger_name,
                       c.opponent_id, op.full_name as opponent_name,
                       c.game_mode, c.difficulty, c.wager_gems, c.status, c.room_code,
                       c.created_at, c.expires_at
                from public.pvp_challenges c
                join public.profiles cp on cp.id = c.challenger_id
                join public.profiles op on op.id = c.opponent_id
                where (c.challenger_id = ? or c.opponent_id = ?)
                  and c.status in ('PENDING', 'ACCEPTED')
                  and c.expires_at > now()
                order by c.created_at desc
                limit 20
                """,
                (rs, rowNum) -> new ChallengeDto(
                        rs.getObject("id", UUID.class),
                        rs.getObject("challenger_id", UUID.class),
                        rs.getString("challenger_name"),
                        rs.getObject("opponent_id", UUID.class),
                        rs.getString("opponent_name"),
                        rs.getString("game_mode"),
                        rs.getString("difficulty"),
                        rs.getInt("wager_gems"),
                        rs.getString("status"),
                        rs.getString("room_code"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("expires_at", OffsetDateTime.class)
                ),
                meId, meId);
    }

    @Transactional
    public ChallengeDto respondChallenge(UUID meId, UUID challengeId, boolean accept) {
        ChallengeDto challenge = getChallengeById(challengeId);
        if (!challenge.opponentId().equals(meId)) {
            throw new IllegalStateException("Bạn không phải người được thách đấu");
        }
        String nextStatus = accept ? "ACCEPTED" : "DECLINED";
        jdbc.update("update public.pvp_challenges set status = ? where id = ?", nextStatus, challengeId);

        if (accept) {
            notificationService.sendDirectNotice(
                    challenge.challengerId(),
                    "PVP_CHALLENGE_ACCEPTED",
                    "🔥 " + challenge.opponentName() + " đã nhận lời thách đấu!",
                    "Vào ngay Đấu trường 1v1 phòng " + challenge.roomCode() + " để so tài!",
                    String.format("{\"challengeId\":\"%s\",\"roomCode\":\"%s\",\"targetAction\":\"PVP_CHALLENGE\"}",
                            challengeId, challenge.roomCode())
            );
        }
        return getChallengeById(challengeId);
    }

    private ChallengeDto getChallengeById(UUID id) {
        return jdbc.query("""
                select c.id, c.challenger_id, cp.full_name as challenger_name,
                       c.opponent_id, op.full_name as opponent_name,
                       c.game_mode, c.difficulty, c.wager_gems, c.status, c.room_code,
                       c.created_at, c.expires_at
                from public.pvp_challenges c
                join public.profiles cp on cp.id = c.challenger_id
                join public.profiles op on op.id = c.opponent_id
                where c.id = ?
                """,
                (rs, rowNum) -> new ChallengeDto(
                        rs.getObject("id", UUID.class),
                        rs.getObject("challenger_id", UUID.class),
                        rs.getString("challenger_name"),
                        rs.getObject("opponent_id", UUID.class),
                        rs.getString("opponent_name"),
                        rs.getString("game_mode"),
                        rs.getString("difficulty"),
                        rs.getInt("wager_gems"),
                        rs.getString("status"),
                        rs.getString("room_code"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("expires_at", OffsetDateTime.class)
                ),
                id).getFirst();
    }
}
