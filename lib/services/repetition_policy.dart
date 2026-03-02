import 'package:polylingy/models/topic_progress.dart';

class SessionResult {
  final bool completed;
  final int mistakes;
  const SessionResult({required this.completed, required this.mistakes});
}

abstract class RepetitionPolicy {
  /// Record one answer for a topic. Returns whether the session goal is met.
  SessionResult recordAnswer(String topicId, bool correct);

  /// Compute persisted progress after a topic completes its session.
  TopicProgress advance(TopicProgress current, int sessionMistakes, DateTime today);

  /// Topic IDs eligible for study today.
  List<String> eligibleTopicIds(List<TopicProgress> all, DateTime today);
}
