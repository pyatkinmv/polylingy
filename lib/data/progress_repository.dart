import 'package:drift/drift.dart';
import 'package:polylingy/data/database.dart';
import 'package:polylingy/models/topic_progress.dart';

class ProgressRepository {
  final AppDatabase _db;

  ProgressRepository(this._db);

  ProgressDao get _dao => _db.progressDao;

  TopicProgress _fromRow(TopicProgressTableData row) => TopicProgress(
        topicId: row.topicId,
        courseId: row.courseId,
        status: ProgressStatus.fromInt(row.status),
        consecutiveCorrect: row.consecutiveCorrect,
        intervalDays: row.intervalDays,
        nextReviewDate: row.nextReviewDate,
        lastAnsweredAt: row.lastAnsweredAt,
      );

  Future<TopicProgress?> getProgress(String topicId, String courseId) async {
    final row = await _dao.getProgress(topicId, courseId);
    return row == null ? null : _fromRow(row);
  }

  Future<List<TopicProgress>> getAllForCourse(String courseId) async {
    final rows = await _dao.getAllForCourse(courseId);
    return rows.map(_fromRow).toList();
  }

  Future<void> saveProgress(TopicProgress progress) {
    return _dao.upsertProgress(TopicProgressTableCompanion(
      topicId: Value(progress.topicId),
      courseId: Value(progress.courseId),
      status: Value(progress.status.value),
      consecutiveCorrect: Value(progress.consecutiveCorrect),
      intervalDays: Value(progress.intervalDays),
      nextReviewDate: Value(progress.nextReviewDate),
      lastAnsweredAt: Value(progress.lastAnsweredAt),
    ));
  }
}
