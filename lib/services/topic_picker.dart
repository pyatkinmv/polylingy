import 'dart:math';

abstract class TopicPicker {
  String pick(List<String> eligible, String? currentTopicId, Random random);
}

/// Picks a uniformly random topic from the eligible pool each time.
class RandomTopicPicker implements TopicPicker {
  const RandomTopicPicker();

  @override
  String pick(List<String> eligible, String? currentTopicId, Random random) =>
      eligible[random.nextInt(eligible.length)];
}

/// Repeats the current topic until it graduates; picks randomly when there is none.
class FixedTopicPicker implements TopicPicker {
  const FixedTopicPicker();

  @override
  String pick(List<String> eligible, String? currentTopicId, Random random) {
    if (currentTopicId != null && eligible.contains(currentTopicId)) {
      return currentTopicId;
    }
    return eligible[random.nextInt(eligible.length)];
  }
}
