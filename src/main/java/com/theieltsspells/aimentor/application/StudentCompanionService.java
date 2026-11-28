package com.theieltsspells.aimentor.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.aimentor.application.dto.StudentCompanionProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentCompanionService {

    private static final Set<String> VALID_MASCOTS = Set.of("DRAGON", "OWL");
    private static final Set<String> VALID_ACCENTS = Set.of("US", "UK");
    private static final Set<String> VALID_CEFR = Set.of("A1", "A2", "B1", "B2", "C1", "C2");
    private static final Set<String> VALID_GOALS = Set.of("IELTS", "COMMUNICATION", "EXAM");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public StudentCompanionProfileDto getProfile(UUID studentId) {
        List<StudentCompanionProfileDto> rows = jdbc.query("""
                select mascot_type, accent, cefr_level, learning_goal, daily_target_xp,
                       favorite_categories::text as categories_json, preferred_game, onboarding_completed
                from public.student_companion_profiles
                where student_id = ?
                """,
                (rs, rowNum) -> new StudentCompanionProfileDto(
                        rs.getString("mascot_type"),
                        rs.getString("accent"),
                        rs.getString("cefr_level"),
                        rs.getString("learning_goal"),
                        rs.getInt("daily_target_xp"),
                        parseCategories(rs.getString("categories_json")),
                        rs.getString("preferred_game"),
                        rs.getBoolean("onboarding_completed")
                ),
                studentId);

        if (rows.isEmpty()) {
            return new StudentCompanionProfileDto(
                    "OWL", "US", "A2", "IELTS", 100,
                    List.of("Đời sống hàng ngày", "Ngụ ngôn & Triết lý"),
                    "SPEED_VOCAB", false
            );
        }
        return rows.getFirst();
    }

    @Transactional
    public StudentCompanionProfileDto upsertProfile(UUID studentId, StudentCompanionProfileDto req) {
        String mascot = normalizeOrDefault(req.mascotType(), VALID_MASCOTS, "OWL");
        String accent = normalizeOrDefault(req.accent(), VALID_ACCENTS, "US");
        String cefr = normalizeOrDefault(req.cefrLevel(), VALID_CEFR, "A2");
        String goal = normalizeOrDefault(req.learningGoal(), VALID_GOALS, "IELTS");
        int xp = (req.dailyTargetXp() == 50 || req.dailyTargetXp() == 150) ? req.dailyTargetXp() : 100;
        String game = (req.preferredGame() == null || req.preferredGame().isBlank())
                ? "SPEED_VOCAB" : req.preferredGame().toUpperCase(Locale.ROOT);
        List<String> categories = (req.favoriteCategories() == null || req.favoriteCategories().isEmpty())
                ? List.of("Đời sống hàng ngày", "Ngụ ngôn & Triết lý")
                : req.favoriteCategories();

        String categoriesJson;
        try {
            categoriesJson = objectMapper.writeValueAsString(categories);
        } catch (Exception ex) {
            categoriesJson = "[\"Đời sống hàng ngày\",\"Ngụ ngôn & Triết lý\"]";
        }

        jdbc.update("""
                insert into public.student_companion_profiles
                  (student_id, mascot_type, accent, cefr_level, learning_goal, daily_target_xp,
                   favorite_categories, preferred_game, onboarding_completed, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, now(), now())
                on conflict (student_id) do update
                set mascot_type = excluded.mascot_type,
                    accent = excluded.accent,
                    cefr_level = excluded.cefr_level,
                    learning_goal = excluded.learning_goal,
                    daily_target_xp = excluded.daily_target_xp,
                    favorite_categories = excluded.favorite_categories,
                    preferred_game = excluded.preferred_game,
                    onboarding_completed = excluded.onboarding_completed,
                    updated_at = now()
                """,
                studentId, mascot, accent, cefr, goal, xp, categoriesJson, game, req.onboardingCompleted());

        return getProfile(studentId);
    }

    private List<String> parseCategories(String json) {
        try {
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            }
        } catch (Exception ignored) {}
        return List.of("Đời sống hàng ngày", "Ngụ ngôn & Triết lý");
    }

    private static String normalizeOrDefault(String value, Set<String> allowed, String fallback) {
        if (value == null) return fallback;
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(upper) ? upper : fallback;
    }
}
