package org.inferred.freebuilder.processor.property;

import static com.google.common.truth.Truth.assertThat;

import static org.inferred.freebuilder.processor.property.MergeAction.addActionsTo;
import static org.inferred.freebuilder.processor.property.MergeAction.appendingToCollections;
import static org.inferred.freebuilder.processor.property.MergeAction.skippingDefaults;
import static org.inferred.freebuilder.processor.property.MergeAction.skippingEmptyOptionals;
import static org.inferred.freebuilder.processor.property.MergeAction.skippingUnsetProperties;

import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.junit.Test;

public class MergeActionTest {

  @Test
  public void appendsNothingIfNoActions() {
    SourceBuilder code = SourceBuilder.forTesting();
    MergeAction.addActionsTo(code, ImmutableSet.of(), false);
    assertThat(code.toString()).isEmpty();
  }

  @Test
  public void appendsSingleUnconditionalMergeActionForBuilder() {
    SourceBuilder code = SourceBuilder.forTesting();
    addActionsTo(code, ImmutableSet.of(skippingDefaults()), true);
    assertThat(code.toString()).isEqualTo(", skipping defaults");
  }

  @Test
  public void appendsSingleUnconditionalMergeActionForValue() {
    SourceBuilder code = SourceBuilder.forTesting();
    addActionsTo(code, ImmutableSet.of(skippingDefaults()), false);
    assertThat(code.toString()).isEqualTo(", skipping defaults");
  }

  @Test
  public void appendsSingleConditionMergeActionForBuilder() {
    SourceBuilder code = SourceBuilder.forTesting();
    addActionsTo(code, ImmutableSet.of(skippingUnsetProperties()), true);
    assertThat(code.toString()).isEqualTo(", skipping unset properties");
  }

  @Test
  public void skipsSingleConditionMergeActionForValue() {
    SourceBuilder code = SourceBuilder.forTesting();
    addActionsTo(code, ImmutableSet.of(skippingUnsetProperties()), false);
    assertThat(code.toString()).isEmpty();
  }

  @Test
  public void appendsTwoSkippingActionsForBuilder() {
    SourceBuilder code = SourceBuilder.forTesting();
    addActionsTo(code, ImmutableSet.of(skippingDefaults(), skippingUnsetProperties()), true);
    assertThat(code.toString()).isEqualTo(", skipping defaults and unset properties");
  }

  @Test
  public void appendsThreeSkippingActionsForBuilder() {
    SourceBuilder code = SourceBuilder.forTesting();
    ImmutableSet<MergeAction> actions = ImmutableSet.of(
        skippingDefaults(), skippingUnsetProperties(), skippingEmptyOptionals());
    addActionsTo(code, actions, true);
    assertThat(code.toString()).isEqualTo(
        ", skipping defaults, empty optionals and unset properties");
  }

  @Test
  public void appendsTwoVerbsForBuilder() {
    SourceBuilder code = SourceBuilder.forTesting();
    ImmutableSet<MergeAction> actions = ImmutableSet.of(
        skippingDefaults(), skippingUnsetProperties(), appendingToCollections());
    addActionsTo(code, actions, true);
    assertThat(code.toString()).isEqualTo(
        ", appending to collections, and skipping defaults and unset properties");
  }
}
