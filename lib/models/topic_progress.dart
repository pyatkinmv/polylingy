enum ProgressStatus {
  newTopic(0),
  learning(1),
  toReview(2);

  final int value;
  const ProgressStatus(this.value);

  static ProgressStatus fromInt(int v) => switch (v) {
        1 => learning,
        2 => toReview,
        _ => newTopic,
      };
}

class TopicProgress {
  final String topicId;
  final String courseId;
  final ProgressStatus status;
  final int consecutiveCorrect;
  final int intervalDays;
  final DateTime? nextReviewDate;
  final DateTime? lastAnsweredAt;

  const TopicProgress({
    required this.topicId,
    required this.courseId,
    required this.status,
    required this.consecutiveCorrect,
    required this.intervalDays,
    this.nextReviewDate,
    this.lastAnsweredAt,
  });

  bool isEligibleToday(DateTime today) {
    return switch (status) {
      ProgressStatus.newTopic => true,
      ProgressStatus.learning => true,
      ProgressStatus.toReview =>
        nextReviewDate != null && !nextReviewDate!.isAfter(today),
    };
  }

  TopicProgress copyWith({
    ProgressStatus? status,
    int? consecutiveCorrect,
    int? intervalDays,
    DateTime? nextReviewDate,
    DateTime? lastAnsweredAt,
    bool clearNextReviewDate = false,
  }) {
    return TopicProgress(
      topicId: topicId,
      courseId: courseId,
      status: status ?? this.status,
      consecutiveCorrect: consecutiveCorrect ?? this.consecutiveCorrect,
      intervalDays: intervalDays ?? this.intervalDays,
      nextReviewDate: clearNextReviewDate ? null : (nextReviewDate ?? this.nextReviewDate),
      lastAnsweredAt: lastAnsweredAt ?? this.lastAnsweredAt,
    );
  }
}
