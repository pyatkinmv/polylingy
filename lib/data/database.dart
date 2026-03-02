import 'dart:io';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
part 'database.g.dart';

class TopicProgressTable extends Table {
  TextColumn get topicId => text()();
  TextColumn get courseId => text()();
  IntColumn get status => integer().withDefault(const Constant(0))();
  IntColumn get consecutiveCorrect => integer().withDefault(const Constant(0))();
  IntColumn get intervalDays => integer().withDefault(const Constant(1))();
  RealColumn get easeFactor => real().withDefault(const Constant(2.5))();
  DateTimeColumn get nextReviewDate => dateTime().nullable()();
  DateTimeColumn get lastAnsweredAt => dateTime().nullable()();

  @override
  Set<Column> get primaryKey => {topicId, courseId};
}

@DriftAccessor(tables: [TopicProgressTable])
class ProgressDao extends DatabaseAccessor<AppDatabase> with _$ProgressDaoMixin {
  ProgressDao(super.db);

  Future<TopicProgressTableData?> getProgress(String topicId, String courseId) {
    return (select(topicProgressTable)
          ..where((t) => t.topicId.equals(topicId) & t.courseId.equals(courseId)))
        .getSingleOrNull();
  }

  Future<List<TopicProgressTableData>> getAllForCourse(String courseId) {
    return (select(topicProgressTable)
          ..where((t) => t.courseId.equals(courseId)))
        .get();
  }

  Future<void> upsertProgress(TopicProgressTableCompanion companion) {
    return into(topicProgressTable).insertOnConflictUpdate(companion);
  }
}

@DriftDatabase(tables: [TopicProgressTable], daos: [ProgressDao])
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(_openConnection());

  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onUpgrade: (m, from, to) async {
      if (from < 2) {
        await m.addColumn(topicProgressTable, topicProgressTable.easeFactor);
      }
    },
  );
}

LazyDatabase _openConnection() {
  return LazyDatabase(() async {
    final dir = await getApplicationDocumentsDirectory();
    final dbPath = p.join(dir.path, 'polylingy', 'progress.db');
    await Directory(p.dirname(dbPath)).create(recursive: true);
    return NativeDatabase(File(dbPath));
  });
}
