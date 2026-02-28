import 'package:flutter_test/flutter_test.dart';
import 'package:polylingy/models/topic_progress.dart';

void main() {
  test('isEligibleToday — new topic is eligible', () {
    final progress = TopicProgress(
      topicId: 'c::0',
      courseId: 'c',
      status: ProgressStatus.newTopic,
      consecutiveCorrect: 0,
      intervalDays: 1,
    );
    final today = DateTime(2026, 1, 1);
    expect(progress.isEligibleToday(today), isTrue);
  });

  test('isEligibleToday — toReview topic with future nextReviewDate is not eligible', () {
    final progress = TopicProgress(
      topicId: 'c::0',
      courseId: 'c',
      status: ProgressStatus.toReview,
      consecutiveCorrect: 3,
      intervalDays: 4,
      nextReviewDate: DateTime(2026, 1, 5),
    );
    final today = DateTime(2026, 1, 1);
    expect(progress.isEligibleToday(today), isFalse);
  });
}
