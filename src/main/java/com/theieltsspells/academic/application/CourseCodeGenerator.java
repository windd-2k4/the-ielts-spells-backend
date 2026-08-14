package com.theieltsspells.academic.application;

import com.theieltsspells.shared.persistence.enums.SkillPair;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class CourseCodeGenerator {
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyMM", Locale.ROOT);
    private final JdbcTemplate jdbc;

    public String next(SkillPair skillPair, LocalDate startsOn) {
        var sequence = jdbc.queryForObject("select nextval('public.course_code_seq')", Long.class);
        var prefix = skillPair == SkillPair.SPEAKING_WRITING ? "SW" : "LR";
        return "%s-%s-%03d".formatted(prefix, startsOn.format(YEAR_MONTH), sequence);
    }
}
