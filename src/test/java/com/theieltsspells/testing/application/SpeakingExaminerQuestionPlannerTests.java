package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnResponse;
import com.theieltsspells.testing.domain.SpeakingExamState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeakingExaminerQuestionPlannerTests {
    private final SpeakingExaminerQuestionPlanner planner =
            new SpeakingExaminerQuestionPlanner(new ObjectMapper());

    @Test
    void buildsAnImmutableThreePartPlanWithOneSharedPartTwoTopic() {
        var plan = planner.plan("""
                {"parts":[
                  {"id":"home","partNo":1,"questions":[
                    {"promptText":"Where do you live?","answerTimeSeconds":30},
                    {"promptText":"What do you enjoy doing at home?"},
                    {"promptText":"Would you like to move home one day?"}]},
                  {"id":"memorable-place","partNo":2,"topicTitle":"A memorable place",
                    "cueCardPromptHtml":"<p>Describe a <strong>memorable place</strong>.</p>",
                    "followUpQuestions":[{"promptText":"Why was this place memorable?"}],
                    "preparationTimeSeconds":60,"answerTimeSeconds":120},
                  {"id":"places","partNo":3,"questions":[
                    {"promptText":"Why do places change over time?"}]}
                ]}
                """);

        assertThat(plan.turns()).hasSize(9);
        assertThat(plan.turns()).filteredOn(turn -> turn.state() == SpeakingExamState.PART_1).hasSize(3);
        assertThat(plan.turns()).filteredOn(turn -> turn.state() == SpeakingExamState.PART_2_LONG_TURN)
                .singleElement().satisfies(turn -> {
                    assertThat(turn.kind()).isEqualTo(SpeakingExaminerTurnResponse.Kind.CUE_CARD);
                    assertThat(turn.promptText()).isEqualTo("Describe a memorable place.");
                    assertThat(turn.topicKey()).isEqualTo("memorable-place");
                });
        assertThat(plan.turns()).filteredOn(turn -> turn.state() == SpeakingExamState.PART_3)
                .extracting(SpeakingExaminerQuestionPlanner.PlannedTurn::topicKey)
                .containsOnly("memorable-place");
    }

    @Test
    void rejectsAPlanThatCannotKeepPartTwoAndPartThreeOnOneTopic() {
        assertThatThrownBy(() -> planner.plan("""
                {"parts":[{"partNo":2,"cueCardPromptHtml":"Describe a place."}]}
                """))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsPartTwoWithoutABankedFollowUpQuestion() {
        assertThatThrownBy(() -> planner.plan("""
                {"parts":[
                  {"partNo":1,"questions":[{"promptText":"One"},{"promptText":"Two"},{"promptText":"Three"}]},
                  {"partNo":2,"cueCardPromptHtml":"Describe a place."},
                  {"partNo":3,"questions":[{"promptText":"Why do places change?"}]}
                ]}
                """))
                .isInstanceOf(BusinessRuleException.class);
    }

}
