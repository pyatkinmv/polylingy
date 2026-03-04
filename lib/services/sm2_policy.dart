import 'package:polylingy/models/topic_progress.dart';
import 'package:polylingy/services/repetition_policy.dart';

class Sm2Policy implements RepetitionPolicy {
  static const double _minEf = 1.3;

  @override
  TopicProgress advance(TopicProgress current, int sessionMistakes, DateTime today) {
    final q = switch (sessionMistakes) { 0 => 5, 1 => 4, 2 => 3, _ => 2 };
    final efDelta = switch (q) { 5 => 0.1, 4 => 0.0, 3 => -0.15, _ => -0.3 };
    final newEf = (current.easeFactor + efDelta).clamp(_minEf, double.infinity);
    final newInterval = q > 2 ? (current.intervalDays * newEf).round() : 1;
    final nextReview = today.add(Duration(days: newInterval));
    return current.copyWith(
      status: ProgressStatus.toReview,
      easeFactor: newEf,
      intervalDays: newInterval,
      nextReviewDate: nextReview,
      lastAnsweredAt: today,
    );
  }

  @override
  List<String> eligibleTopicIds(List<TopicProgress> all, DateTime today) =>
      all.where((p) => p.isEligibleToday(today)).map((p) => p.topicId).toList();
}
