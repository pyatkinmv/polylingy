import 'package:polylingy/models/topic_progress.dart';

class SrService {
  static const int _graduationThreshold = 3;

  /// Returns updated progress after a correct answer.
  TopicProgress onCorrect(TopicProgress progress, DateTime today) {
    final newConsecutive = progress.consecutiveCorrect + 1;

    if (newConsecutive >= _graduationThreshold) {
      final int newInterval;
      if (progress.status == ProgressStatus.toReview) {
        newInterval = progress.intervalDays * 2;
      } else {
        newInterval = 1;
      }
      final nextReview = DateTime(today.year, today.month, today.day + newInterval);
      return progress.copyWith(
        status: ProgressStatus.toReview,
        consecutiveCorrect: 0,
        intervalDays: newInterval,
        nextReviewDate: nextReview,
        lastAnsweredAt: today,
      );
    }

    return progress.copyWith(
      consecutiveCorrect: newConsecutive,
      lastAnsweredAt: today,
    );
  }

  /// Returns updated progress after an incorrect answer.
  TopicProgress onIncorrect(TopicProgress progress, DateTime today) {
    return progress.copyWith(
      status: ProgressStatus.learning,
      consecutiveCorrect: 0,
      lastAnsweredAt: today,
    );
  }

  /// Filters topics that are eligible for study today.
  List<String> eligibleTopicIds(List<TopicProgress> allProgress, DateTime today) {
    final todayDate = DateTime(today.year, today.month, today.day);
    return allProgress
        .where((p) => p.isEligibleToday(todayDate))
        .map((p) => p.topicId)
        .toList();
  }
}
