import 'package:polylingy/models/topic_progress.dart';
import 'package:polylingy/services/repetition_policy.dart';

class _TopicSession {
  int consecutive = 0;
  int mistakes = 0;
}

class Sm2Policy implements RepetitionPolicy {
  static const int _goal = 3;
  static const double _minEf = 1.3;

  final Map<String, _TopicSession> _sessions = {};

  @override
  SessionResult recordAnswer(String topicId, bool correct) {
    final s = _sessions.putIfAbsent(topicId, _TopicSession.new);
    if (correct) {
      s.consecutive++;
    } else {
      s.consecutive = 0;
      s.mistakes++;
    }
    final done = s.consecutive >= _goal;
    if (done) _sessions.remove(topicId);
    return SessionResult(completed: done, mistakes: done ? s.mistakes : 0);
  }

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
