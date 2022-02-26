package org.inferred.freebuilder.processor.property;

import static com.google.common.collect.Iterables.getLast;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;
import java.util.List;
import java.util.Set;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.ValueType;

/** Readable action fragments for documenting mergeFrom behaviors. */
public class MergeAction extends ValueType {

  public static MergeAction appendingToCollections() {
    return new MergeAction("appending to", "collections", false);
  }

  public static MergeAction skippingDefaults() {
    return new MergeAction("skipping", "defaults", false);
  }

  public static MergeAction skippingEmptyOptionals() {
    return new MergeAction("skipping", "empty optionals", false);
  }

  public static MergeAction skippingUnsetProperties() {
    return new MergeAction("skipping", "unset properties", true);
  }

  /** Emits a sentence fragment combining all the merge actions. */
  public static void addActionsTo(
      SourceBuilder code, Set<MergeAction> mergeActions, boolean forBuilder) {
    SetMultimap<String, String> nounsByVerb = TreeMultimap.create();
    mergeActions.forEach(
        mergeAction -> {
          if (forBuilder || !mergeAction.builderOnly) {
            nounsByVerb.put(mergeAction.verb, mergeAction.noun);
          }
        });
    List<String> verbs = ImmutableList.copyOf(nounsByVerb.keySet());
    String lastVerb = getLast(verbs, null);
    for (String verb : nounsByVerb.keySet()) {
      code.add(", %s%s", (verbs.size() > 1 && verb.equals(lastVerb)) ? "and " : "", verb);
      List<String> nouns = ImmutableList.copyOf(nounsByVerb.get(verb));
      for (int i = 0; i < nouns.size(); ++i) {
        String separator = (i == 0) ? "" : (i == nouns.size() - 1) ? " and" : ",";
        code.add("%s %s", separator, nouns.get(i));
      }
    }
  }

  private final String verb;
  private final String noun;
  private final boolean builderOnly;

  private MergeAction(String verb, String noun, boolean builderOnly) {
    this.verb = verb;
    this.noun = noun;
    this.builderOnly = builderOnly;
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("verb", verb);
    fields.add("noun", noun);
    fields.add("builderOnly", builderOnly);
  }

  @Override
  public String toString() {
    return verb + " " + noun + (builderOnly ? " on builders" : "");
  }
}
