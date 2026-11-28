package com.theieltsspells.aimentor.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentEconomyService {

    public record ShopItem(
            String id,
            String name,
            String description,
            String category, // UTILITY | SKIN | TITLE
            int priceGems,
            boolean consumable,
            String badgeEmoji
    ) {}

    public record WalletResponse(
            int gems,
            int freezeShields,
            int maxFreezeShields,
            boolean doubleGemActive,
            OffsetDateTime doubleGemUntil,
            int arenaHints,
            String equippedSkin,
            String equippedTitle,
            List<String> ownedItems,
            int pvpRankPoints,
            int pvpWins,
            int pvpLosses,
            int streakDays,
            int longestStreak,
            List<String> claimedRoutineKeysToday,
            List<ShopItem> catalog
    ) {}

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final List<ShopItem> CATALOG = List.of(
            new ShopItem("FREEZE_SHIELD", "Khiên Đóng Băng",
                    "Tự động bảo vệ chuỗi ngày học nếu bạn lỡ bận 1 ngày (tối đa 3 khiên).",
                    "UTILITY", 50, true, "🛡️"),
            new ShopItem("DOUBLE_GEM_15M", "Tinh Thể x2 Kim Cương",
                    "Nhân đôi toàn bộ Kim cương kiếm được trong 15 phút tới.",
                    "UTILITY", 40, true, "⚡"),
            new ShopItem("ARENA_HINT_PACK", "Gói 3 Gợi Ý Đấu Trường",
                    "Loại bỏ 2 đáp án nhiễu trong Đấu trường PvP & Mini-Game.",
                    "UTILITY", 30, true, "🔍"),
            new ShopItem("SCHOLAR_HAT", "Mũ Phù Thủy Học Giả",
                    "Phụ kiện học thuật gắn trực tiếp lên Ignis/Spellie trong mọi màn hình.",
                    "SKIN", 80, false, "🎓"),
            new ShopItem("FLAME_CAPE", "Huy Hiệu Tinh Tú Hoàng Gia",
                    "Khung viền & huy hiệu tỏa sáng cho bạn đồng hành ở Home & AI Chat.",
                    "SKIN", 120, false, "✨"),
            new ShopItem("CROWN_85", "Vương Miện Band 8.5+",
                    "Vương miện danh dự hiển thị trên đầu linh vật và Bảng xếp hạng.",
                    "SKIN", 150, false, "👑"),
            new ShopItem("TITLE_VOCAB_MASTER", "Danh hiệu: Thợ Săn Từ Vựng",
                    "Danh hiệu hiển thị dưới tên bạn ở Mục Bạn Bè & Đấu Trường 1v1.",
                    "TITLE", 90, false, "📜"),
            new ShopItem("TITLE_ARENA_LEGEND", "Danh hiệu: Chiến Thần Phản Xạ",
                    "Danh hiệu cao cấp dành cho cao thủ Đấu Trường 1v1.",
                    "TITLE", 140, false, "⚔️")
    );

    private static final Map<String, ShopItem> CATALOG_MAP = new HashMap<>();
    static {
        for (ShopItem item : CATALOG) {
            CATALOG_MAP.put(item.id(), item);
        }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional
    public WalletResponse getWallet(UUID studentId) {
        ensureWalletExists(studentId);
        String todayKey = LocalDate.now(VN_ZONE).toString();
        return jdbc.query("""
                select gems, freeze_shields, double_gem_until, arena_hints,
                       equipped_skin, equipped_title, owned_items::text as owned_json,
                       pvp_rank_points, pvp_wins, pvp_losses,
                       daily_routine_claims::text as claims_json,
                       streak_days, longest_streak
                from public.student_wallets
                where student_id = ?
                """,
                (rs, rowNum) -> {
                    OffsetDateTime doubleUntil = rs.getObject("double_gem_until", OffsetDateTime.class);
                    boolean doubleActive = doubleUntil != null && doubleUntil.isAfter(OffsetDateTime.now());
                    List<String> owned = parseStringList(rs.getString("owned_json"));
                    Map<String, List<String>> claims = parseClaimsMap(rs.getString("claims_json"));
                    return new WalletResponse(
                            rs.getInt("gems"),
                            rs.getInt("freeze_shields"),
                            3,
                            doubleActive,
                            doubleUntil,
                            rs.getInt("arena_hints"),
                            rs.getString("equipped_skin"),
                            rs.getString("equipped_title"),
                            owned,
                            rs.getInt("pvp_rank_points"),
                            rs.getInt("pvp_wins"),
                            rs.getInt("pvp_losses"),
                            rs.getInt("streak_days"),
                            rs.getInt("longest_streak"),
                            claims.getOrDefault(todayKey, List.of()),
                            CATALOG
                    );
                },
                studentId).getFirst();
    }

    @Transactional
    public Map<String, Object> claimRoutineReward(UUID studentId, String stepKey) {
        ensureWalletExists(studentId);
        String key = stepKey == null ? "" : stepKey.trim().toUpperCase(Locale.ROOT);
        if (!List.of("GAME", "STORY", "SPEAK", "CHEST", "QUEST_1", "QUEST_2", "QUEST_3").contains(key)) {
            throw new IllegalArgumentException("Invalid routine step key");
        }

        WalletResponse current = getWallet(studentId);
        if (current.claimedRoutineKeysToday().contains(key)) {
            return Map.of("awardedGems", 0, "alreadyClaimed", true, "wallet", current);
        }

        int baseGems = "CHEST".equals(key) ? 25 : 10;
        int multiplier = current.doubleGemActive() ? 2 : 1;
        int awarded = baseGems * multiplier;

        String todayKey = LocalDate.now(VN_ZONE).toString();
        List<String> updatedToday = new ArrayList<>(current.claimedRoutineKeysToday());
        updatedToday.add(key);

        Map<String, List<String>> nextClaims = new LinkedHashMap<>();
        nextClaims.put(todayKey, updatedToday);

        String claimsJson;
        try {
            claimsJson = objectMapper.writeValueAsString(nextClaims);
        } catch (Exception ex) {
            claimsJson = "{}";
        }

        jdbc.update("""
                update public.student_wallets
                set gems = gems + ?,
                    daily_routine_claims = ?::jsonb,
                    streak_days = case
                      when last_active_date = ? then streak_days
                      when last_active_date = ? then streak_days + 1
                      else greatest(streak_days, 1)
                    end,
                    longest_streak = greatest(
                      longest_streak,
                      case
                        when last_active_date = ? then streak_days
                        when last_active_date = ? then streak_days + 1
                        else greatest(streak_days, 1)
                      end
                    ),
                    last_active_date = ?,
                    updated_at = now()
                where student_id = ?
                """,
                awarded,
                claimsJson,
                LocalDate.now(VN_ZONE),
                LocalDate.now(VN_ZONE).minusDays(1),
                LocalDate.now(VN_ZONE),
                LocalDate.now(VN_ZONE).minusDays(1),
                LocalDate.now(VN_ZONE),
                studentId);

        logTx(studentId, awarded, "ROUTINE_" + key, "Hoàn thành chặng " + key);
        return Map.of("awardedGems", awarded, "alreadyClaimed", false, "wallet", getWallet(studentId));
    }

    @Transactional
    public WalletResponse buyItem(UUID studentId, String itemId) {
        ensureWalletExists(studentId);
        ShopItem item = CATALOG_MAP.get(itemId);
        if (item == null) {
            throw new IllegalArgumentException("Vật phẩm không tồn tại");
        }
        WalletResponse wallet = getWallet(studentId);
        if (wallet.gems() < item.priceGems()) {
            throw new IllegalStateException("Bạn chưa đủ Kim cương (" + wallet.gems() + "/" + item.priceGems() + " 💎)");
        }

        if ("FREEZE_SHIELD".equals(item.id())) {
            if (wallet.freezeShields() >= wallet.maxFreezeShields()) {
                throw new IllegalStateException("Khiên Đóng Băng đã đạt tối đa (3/3)");
            }
            jdbc.update("""
                    update public.student_wallets
                    set gems = gems - ?, freeze_shields = freeze_shields + 1, updated_at = now()
                    where student_id = ?
                    """, item.priceGems(), studentId);
        } else if ("DOUBLE_GEM_15M".equals(item.id())) {
            jdbc.update("""
                    update public.student_wallets
                    set gems = gems - ?,
                        double_gem_until = greatest(coalesce(double_gem_until, now()), now()) + interval '15 minutes',
                        updated_at = now()
                    where student_id = ?
                    """, item.priceGems(), studentId);
        } else if ("ARENA_HINT_PACK".equals(item.id())) {
            jdbc.update("""
                    update public.student_wallets
                    set gems = gems - ?, arena_hints = arena_hints + 3, updated_at = now()
                    where student_id = ?
                    """, item.priceGems(), studentId);
        } else {
            if (wallet.ownedItems().contains(item.id())) {
                throw new IllegalStateException("Bạn đã sở hữu vật phẩm này");
            }
            List<String> updatedOwned = new ArrayList<>(wallet.ownedItems());
            updatedOwned.add(item.id());
            String ownedJson;
            try {
                ownedJson = objectMapper.writeValueAsString(updatedOwned);
            } catch (Exception ex) {
                ownedJson = "[]";
            }
            if ("SKIN".equals(item.category())) {
                jdbc.update("""
                        update public.student_wallets
                        set gems = gems - ?, owned_items = ?::jsonb, equipped_skin = ?, updated_at = now()
                        where student_id = ?
                        """, item.priceGems(), ownedJson, item.id(), studentId);
            } else {
                jdbc.update("""
                        update public.student_wallets
                        set gems = gems - ?, owned_items = ?::jsonb, equipped_title = ?, updated_at = now()
                        where student_id = ?
                        """, item.priceGems(), ownedJson, item.name().replace("Danh hiệu: ", ""), studentId);
            }
        }

        logTx(studentId, -item.priceGems(), "SHOP_BUY_" + item.id(), "Mua " + item.name());
        return getWallet(studentId);
    }

    @Transactional
    public WalletResponse equipItem(UUID studentId, String itemId) {
        ensureWalletExists(studentId);
        WalletResponse wallet = getWallet(studentId);
        if (itemId == null || itemId.isBlank() || "NONE".equalsIgnoreCase(itemId)) {
            jdbc.update("update public.student_wallets set equipped_skin = null, updated_at = now() where student_id = ?", studentId);
            return getWallet(studentId);
        }
        if ("NONE_TITLE".equalsIgnoreCase(itemId)) {
            jdbc.update("update public.student_wallets set equipped_title = null, updated_at = now() where student_id = ?", studentId);
            return getWallet(studentId);
        }
        ShopItem item = CATALOG_MAP.get(itemId);
        if (item == null || !wallet.ownedItems().contains(item.id())) {
            throw new IllegalStateException("Bạn chưa sở hữu trang bị này");
        }
        if ("SKIN".equals(item.category())) {
            jdbc.update("update public.student_wallets set equipped_skin = ?, updated_at = now() where student_id = ?",
                    item.id(), studentId);
        } else if ("TITLE".equals(item.category())) {
            jdbc.update("update public.student_wallets set equipped_title = ?, updated_at = now() where student_id = ?",
                    item.name().replace("Danh hiệu: ", ""), studentId);
        }
        return getWallet(studentId);
    }

    @Transactional
    public WalletResponse recordPvpMatch(UUID studentId, boolean isVictory, boolean isRankedMatch) {
        ensureWalletExists(studentId);
        int deltaRp = isRankedMatch ? (isVictory ? 25 : -10) : 0;
        int winInc = (isRankedMatch && isVictory) ? 1 : 0;
        int lossInc = (isRankedMatch && !isVictory) ? 1 : 0;
        int bonusGems = isRankedMatch ? (isVictory ? 15 : 5) : (isVictory ? 5 : 2);

        WalletResponse current = getWallet(studentId);
        if (current.doubleGemActive()) bonusGems *= 2;

        jdbc.update("""
                update public.student_wallets
                set gems = gems + ?,
                    pvp_rank_points = greatest(0, pvp_rank_points + ?),
                    pvp_wins = pvp_wins + ?,
                    pvp_losses = pvp_losses + ?,
                    updated_at = now()
                where student_id = ?
                """, bonusGems, deltaRp, winInc, lossInc, studentId);

        String note = isRankedMatch
                ? (isVictory ? "Thắng trận Đấu trường Xếp hạng" : "Hoàn thành Đấu trường Xếp hạng")
                : "Đấu giao hữu 1v1";
        logTx(studentId, bonusGems, isRankedMatch ? "PVP_RANKED" : "PVP_FRIENDLY", note);
        return getWallet(studentId);
    }

    @Transactional
    public void addGems(UUID studentId, int delta, String source, String note) {
        ensureWalletExists(studentId);
        jdbc.update("update public.student_wallets set gems = greatest(0, gems + ?), updated_at = now() where student_id = ?",
                delta, studentId);
        logTx(studentId, delta, source, note);
    }

    private void ensureWalletExists(UUID studentId) {
        jdbc.update("""
                insert into public.student_wallets (student_id, gems)
                values (?, 30)
                on conflict (student_id) do nothing
                """, studentId);
    }

    private void logTx(UUID studentId, int amount, String source, String note) {
        jdbc.update("""
                insert into public.gem_transactions (student_id, amount, source, note)
                values (?, ?, ?, ?)
                """, studentId, amount, source, note);
    }

    private List<String> parseStringList(String json) {
        try {
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    private Map<String, List<String>> parseClaimsMap(String json) {
        try {
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            }
        } catch (Exception ignored) {}
        return Map.of();
    }
}
