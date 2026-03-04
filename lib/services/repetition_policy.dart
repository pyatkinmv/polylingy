import 'package:polylingy/models/topic_progress.dart';

abstract class RepetitionPolicy {
  /// Compute persisted progress after a topic completes its session.
  TopicProgress advance(TopicProgress current, int sessionMistakes, DateTime today);

  /// Topic IDs eligible for study today.
  List<String> eligibleTopicIds(List<TopicProgress> all, DateTime today);
}
