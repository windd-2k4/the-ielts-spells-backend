package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnResponse;
import com.theieltsspells.testing.domain.SpeakingExamState;
import com.theieltsspells.testing.domain.SpeakingSessionVariant;
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
                """, SpeakingSessionVariant.FULL_MOCK);

        assertThat(plan.turns()).hasSize(9);
        assertThat(plan.turns()).filteredOn(turn -> turn.state() == SpeakingExamState.PART_1).hasSize(3);
        assertThat(plan.turns()).filteredOn(turn -> turn.state() == SpeakingExamState.PART_2_LONG_TURN)
                .singleElement().satisfies(turn -> {
                    assertThat(turn.kind()).isEqualTo(SpeakingExaminerTurnResponse.Kind.CUE_CARD);
                    assertThat(turn.promptText()).isEqualTo("Describe a memorable place.");
                    assertThat(turn.speechText()).isEqualTo(SpeakingExaminerQuestionPlanner.PART_TWO_LONG_TURN_SPEECH);
                    assertThat(turn.topicKey()).isEqualTo("memorable-place");
                });
        assertThat(plan.turns()).filteredOn(turn -> turn.state() == SpeakingExamState.PART_3)
                .extracting(SpeakingExaminerQuestionPlanner.PlannedTurn::topicKey)
                .containsOnly("memorable-place");
    }

    @Test
    void snapshotsOnlyTheRequestedStandalonePart() {
        var partOne = planner.plan("""
                {"parts":[{"id":"home","partNo":1,"questions":[
                  {"promptText":"One"},{"promptText":"Two"},{"promptText":"Three"},
                  {"promptText":"Four"},{"promptText":"Five"},{"promptText":"Six"},
                  {"promptText":"Ignored"}]}]}
                """, SpeakingSessionVariant.PART_1);
        assertThat(partOne.turns()).hasSize(5);
        assertThat(partOne.turns()).extracting(SpeakingExaminerQuestionPlanner.PlannedTurn::state)
                .containsOnly(SpeakingExamState.MIC_CHECK, SpeakingExamState.PART_1, SpeakingExamState.CLOSING);
        assertThat(partOne.turns()).filteredOn(turn -> turn.state() == SpeakingExamState.PART_1)
                .extracting(SpeakingExaminerQuestionPlanner.PlannedTurn::promptText)
                .containsExactly("One", "Two", "Three");
        assertThat(partOne.turns()).filteredOn(turn -> turn.state() == SpeakingExamState.PART_1)
                .first().satisfies(turn -> {
                    assertThat(turn.promptText()).isEqualTo("One");
                    assertThat(turn.speechText()).startsWith("Good morning.").endsWith("One");
                });

        var partTwo = planner.plan("""
                {"parts":[{"id":"place","partNo":2,"cueCardPromptHtml":"Describe a place.",
                  "followUpQuestions":[{"promptText":"Why?"}]}]}
                """, SpeakingSessionVariant.PART_2);
        assertThat(partTwo.turns()).extracting(SpeakingExaminerQuestionPlanner.PlannedTurn::state)
                .containsExactly(SpeakingExamState.MIC_CHECK, SpeakingExamState.PART_2_LONG_TURN,
                        SpeakingExamState.PART_2_FOLLOW_UP, SpeakingExamState.CLOSING);

        var partThree = planner.plan("""
                {"parts":[{"id":"society","partNo":3,"questions":[
                  {"promptText":"One"},{"promptText":"Two"},{"promptText":"Three"}]}]}
                """, SpeakingSessionVariant.PART_3);
        assertThat(partThree.turns()).extracting(SpeakingExaminerQuestionPlanner.PlannedTurn::state)
                .containsExactly(SpeakingExamState.MIC_CHECK, SpeakingExamState.PART_3,
                        SpeakingExamState.PART_3, SpeakingExamState.PART_3, SpeakingExamState.CLOSING);
    }

    @Test
    void rejectsAPlanThatCannotKeepPartTwoAndPartThreeOnOneTopic() {
        assertThatThrownBy(() -> planner.plan("""
                {"parts":[{"partNo":2,"cueCardPromptHtml":"Describe a place."}]}
                """, SpeakingSessionVariant.FULL_MOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsAStandalonePartWhenItsQuestionBankIsMissing() {
        assertThatThrownBy(() -> planner.plan("{\"parts\":[]}", SpeakingSessionVariant.PART_1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Part 1");
    }

    @Test
    void rejectsPartTwoWithoutABankedFollowUpQuestion() {
        assertThatThrownBy(() -> planner.plan("""
                {"parts":[
                  {"partNo":1,"questions":[{"promptText":"One"},{"promptText":"Two"},{"promptText":"Three"}]},
                  {"partNo":2,"cueCardPromptHtml":"Describe a place."},
                  {"partNo":3,"questions":[{"promptText":"Why do places change?"}]}
                ]}
                """, SpeakingSessionVariant.FULL_MOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

}
