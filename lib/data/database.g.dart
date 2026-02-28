// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'database.dart';

// ignore_for_file: type=lint
mixin _$ProgressDaoMixin on DatabaseAccessor<AppDatabase> {
  $TopicProgressTableTable get topicProgressTable =>
      attachedDatabase.topicProgressTable;
  ProgressDaoManager get managers => ProgressDaoManager(this);
}

class ProgressDaoManager {
  final _$ProgressDaoMixin _db;
  ProgressDaoManager(this._db);
  $$TopicProgressTableTableTableManager get topicProgressTable =>
      $$TopicProgressTableTableTableManager(
        _db.attachedDatabase,
        _db.topicProgressTable,
      );
}

class $TopicProgressTableTable extends TopicProgressTable
    with TableInfo<$TopicProgressTableTable, TopicProgressTableData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $TopicProgressTableTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _topicIdMeta = const VerificationMeta(
    'topicId',
  );
  @override
  late final GeneratedColumn<String> topicId = GeneratedColumn<String>(
    'topic_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _courseIdMeta = const VerificationMeta(
    'courseId',
  );
  @override
  late final GeneratedColumn<String> courseId = GeneratedColumn<String>(
    'course_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<int> status = GeneratedColumn<int>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _consecutiveCorrectMeta =
      const VerificationMeta('consecutiveCorrect');
  @override
  late final GeneratedColumn<int> consecutiveCorrect = GeneratedColumn<int>(
    'consecutive_correct',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _intervalDaysMeta = const VerificationMeta(
    'intervalDays',
  );
  @override
  late final GeneratedColumn<int> intervalDays = GeneratedColumn<int>(
    'interval_days',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(1),
  );
  static const VerificationMeta _nextReviewDateMeta = const VerificationMeta(
    'nextReviewDate',
  );
  @override
  late final GeneratedColumn<DateTime> nextReviewDate =
      GeneratedColumn<DateTime>(
        'next_review_date',
        aliasedName,
        true,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: false,
      );
  static const VerificationMeta _lastAnsweredAtMeta = const VerificationMeta(
    'lastAnsweredAt',
  );
  @override
  late final GeneratedColumn<DateTime> lastAnsweredAt =
      GeneratedColumn<DateTime>(
        'last_answered_at',
        aliasedName,
        true,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: false,
      );
  @override
  List<GeneratedColumn> get $columns => [
    topicId,
    courseId,
    status,
    consecutiveCorrect,
    intervalDays,
    nextReviewDate,
    lastAnsweredAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'topic_progress_table';
  @override
  VerificationContext validateIntegrity(
    Insertable<TopicProgressTableData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('topic_id')) {
      context.handle(
        _topicIdMeta,
        topicId.isAcceptableOrUnknown(data['topic_id']!, _topicIdMeta),
      );
    } else if (isInserting) {
      context.missing(_topicIdMeta);
    }
    if (data.containsKey('course_id')) {
      context.handle(
        _courseIdMeta,
        courseId.isAcceptableOrUnknown(data['course_id']!, _courseIdMeta),
      );
    } else if (isInserting) {
      context.missing(_courseIdMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    }
    if (data.containsKey('consecutive_correct')) {
      context.handle(
        _consecutiveCorrectMeta,
        consecutiveCorrect.isAcceptableOrUnknown(
          data['consecutive_correct']!,
          _consecutiveCorrectMeta,
        ),
      );
    }
    if (data.containsKey('interval_days')) {
      context.handle(
        _intervalDaysMeta,
        intervalDays.isAcceptableOrUnknown(
          data['interval_days']!,
          _intervalDaysMeta,
        ),
      );
    }
    if (data.containsKey('next_review_date')) {
      context.handle(
        _nextReviewDateMeta,
        nextReviewDate.isAcceptableOrUnknown(
          data['next_review_date']!,
          _nextReviewDateMeta,
        ),
      );
    }
    if (data.containsKey('last_answered_at')) {
      context.handle(
        _lastAnsweredAtMeta,
        lastAnsweredAt.isAcceptableOrUnknown(
          data['last_answered_at']!,
          _lastAnsweredAtMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {topicId, courseId};
  @override
  TopicProgressTableData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TopicProgressTableData(
      topicId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}topic_id'],
      )!,
      courseId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}course_id'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}status'],
      )!,
      consecutiveCorrect: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}consecutive_correct'],
      )!,
      intervalDays: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}interval_days'],
      )!,
      nextReviewDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}next_review_date'],
      ),
      lastAnsweredAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}last_answered_at'],
      ),
    );
  }

  @override
  $TopicProgressTableTable createAlias(String alias) {
    return $TopicProgressTableTable(attachedDatabase, alias);
  }
}

class TopicProgressTableData extends DataClass
    implements Insertable<TopicProgressTableData> {
  final String topicId;
  final String courseId;
  final int status;
  final int consecutiveCorrect;
  final int intervalDays;
  final DateTime? nextReviewDate;
  final DateTime? lastAnsweredAt;
  const TopicProgressTableData({
    required this.topicId,
    required this.courseId,
    required this.status,
    required this.consecutiveCorrect,
    required this.intervalDays,
    this.nextReviewDate,
    this.lastAnsweredAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['topic_id'] = Variable<String>(topicId);
    map['course_id'] = Variable<String>(courseId);
    map['status'] = Variable<int>(status);
    map['consecutive_correct'] = Variable<int>(consecutiveCorrect);
    map['interval_days'] = Variable<int>(intervalDays);
    if (!nullToAbsent || nextReviewDate != null) {
      map['next_review_date'] = Variable<DateTime>(nextReviewDate);
    }
    if (!nullToAbsent || lastAnsweredAt != null) {
      map['last_answered_at'] = Variable<DateTime>(lastAnsweredAt);
    }
    return map;
  }

  TopicProgressTableCompanion toCompanion(bool nullToAbsent) {
    return TopicProgressTableCompanion(
      topicId: Value(topicId),
      courseId: Value(courseId),
      status: Value(status),
      consecutiveCorrect: Value(consecutiveCorrect),
      intervalDays: Value(intervalDays),
      nextReviewDate: nextReviewDate == null && nullToAbsent
          ? const Value.absent()
          : Value(nextReviewDate),
      lastAnsweredAt: lastAnsweredAt == null && nullToAbsent
          ? const Value.absent()
          : Value(lastAnsweredAt),
    );
  }

  factory TopicProgressTableData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TopicProgressTableData(
      topicId: serializer.fromJson<String>(json['topicId']),
      courseId: serializer.fromJson<String>(json['courseId']),
      status: serializer.fromJson<int>(json['status']),
      consecutiveCorrect: serializer.fromJson<int>(json['consecutiveCorrect']),
      intervalDays: serializer.fromJson<int>(json['intervalDays']),
      nextReviewDate: serializer.fromJson<DateTime?>(json['nextReviewDate']),
      lastAnsweredAt: serializer.fromJson<DateTime?>(json['lastAnsweredAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'topicId': serializer.toJson<String>(topicId),
      'courseId': serializer.toJson<String>(courseId),
      'status': serializer.toJson<int>(status),
      'consecutiveCorrect': serializer.toJson<int>(consecutiveCorrect),
      'intervalDays': serializer.toJson<int>(intervalDays),
      'nextReviewDate': serializer.toJson<DateTime?>(nextReviewDate),
      'lastAnsweredAt': serializer.toJson<DateTime?>(lastAnsweredAt),
    };
  }

  TopicProgressTableData copyWith({
    String? topicId,
    String? courseId,
    int? status,
    int? consecutiveCorrect,
    int? intervalDays,
    Value<DateTime?> nextReviewDate = const Value.absent(),
    Value<DateTime?> lastAnsweredAt = const Value.absent(),
  }) => TopicProgressTableData(
    topicId: topicId ?? this.topicId,
    courseId: courseId ?? this.courseId,
    status: status ?? this.status,
    consecutiveCorrect: consecutiveCorrect ?? this.consecutiveCorrect,
    intervalDays: intervalDays ?? this.intervalDays,
    nextReviewDate: nextReviewDate.present
        ? nextReviewDate.value
        : this.nextReviewDate,
    lastAnsweredAt: lastAnsweredAt.present
        ? lastAnsweredAt.value
        : this.lastAnsweredAt,
  );
  TopicProgressTableData copyWithCompanion(TopicProgressTableCompanion data) {
    return TopicProgressTableData(
      topicId: data.topicId.present ? data.topicId.value : this.topicId,
      courseId: data.courseId.present ? data.courseId.value : this.courseId,
      status: data.status.present ? data.status.value : this.status,
      consecutiveCorrect: data.consecutiveCorrect.present
          ? data.consecutiveCorrect.value
          : this.consecutiveCorrect,
      intervalDays: data.intervalDays.present
          ? data.intervalDays.value
          : this.intervalDays,
      nextReviewDate: data.nextReviewDate.present
          ? data.nextReviewDate.value
          : this.nextReviewDate,
      lastAnsweredAt: data.lastAnsweredAt.present
          ? data.lastAnsweredAt.value
          : this.lastAnsweredAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TopicProgressTableData(')
          ..write('topicId: $topicId, ')
          ..write('courseId: $courseId, ')
          ..write('status: $status, ')
          ..write('consecutiveCorrect: $consecutiveCorrect, ')
          ..write('intervalDays: $intervalDays, ')
          ..write('nextReviewDate: $nextReviewDate, ')
          ..write('lastAnsweredAt: $lastAnsweredAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    topicId,
    courseId,
    status,
    consecutiveCorrect,
    intervalDays,
    nextReviewDate,
    lastAnsweredAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TopicProgressTableData &&
          other.topicId == this.topicId &&
          other.courseId == this.courseId &&
          other.status == this.status &&
          other.consecutiveCorrect == this.consecutiveCorrect &&
          other.intervalDays == this.intervalDays &&
          other.nextReviewDate == this.nextReviewDate &&
          other.lastAnsweredAt == this.lastAnsweredAt);
}

class TopicProgressTableCompanion
    extends UpdateCompanion<TopicProgressTableData> {
  final Value<String> topicId;
  final Value<String> courseId;
  final Value<int> status;
  final Value<int> consecutiveCorrect;
  final Value<int> intervalDays;
  final Value<DateTime?> nextReviewDate;
  final Value<DateTime?> lastAnsweredAt;
  final Value<int> rowid;
  const TopicProgressTableCompanion({
    this.topicId = const Value.absent(),
    this.courseId = const Value.absent(),
    this.status = const Value.absent(),
    this.consecutiveCorrect = const Value.absent(),
    this.intervalDays = const Value.absent(),
    this.nextReviewDate = const Value.absent(),
    this.lastAnsweredAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  TopicProgressTableCompanion.insert({
    required String topicId,
    required String courseId,
    this.status = const Value.absent(),
    this.consecutiveCorrect = const Value.absent(),
    this.intervalDays = const Value.absent(),
    this.nextReviewDate = const Value.absent(),
    this.lastAnsweredAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : topicId = Value(topicId),
       courseId = Value(courseId);
  static Insertable<TopicProgressTableData> custom({
    Expression<String>? topicId,
    Expression<String>? courseId,
    Expression<int>? status,
    Expression<int>? consecutiveCorrect,
    Expression<int>? intervalDays,
    Expression<DateTime>? nextReviewDate,
    Expression<DateTime>? lastAnsweredAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (topicId != null) 'topic_id': topicId,
      if (courseId != null) 'course_id': courseId,
      if (status != null) 'status': status,
      if (consecutiveCorrect != null) 'consecutive_correct': consecutiveCorrect,
      if (intervalDays != null) 'interval_days': intervalDays,
      if (nextReviewDate != null) 'next_review_date': nextReviewDate,
      if (lastAnsweredAt != null) 'last_answered_at': lastAnsweredAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  TopicProgressTableCompanion copyWith({
    Value<String>? topicId,
    Value<String>? courseId,
    Value<int>? status,
    Value<int>? consecutiveCorrect,
    Value<int>? intervalDays,
    Value<DateTime?>? nextReviewDate,
    Value<DateTime?>? lastAnsweredAt,
    Value<int>? rowid,
  }) {
    return TopicProgressTableCompanion(
      topicId: topicId ?? this.topicId,
      courseId: courseId ?? this.courseId,
      status: status ?? this.status,
      consecutiveCorrect: consecutiveCorrect ?? this.consecutiveCorrect,
      intervalDays: intervalDays ?? this.intervalDays,
      nextReviewDate: nextReviewDate ?? this.nextReviewDate,
      lastAnsweredAt: lastAnsweredAt ?? this.lastAnsweredAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (topicId.present) {
      map['topic_id'] = Variable<String>(topicId.value);
    }
    if (courseId.present) {
      map['course_id'] = Variable<String>(courseId.value);
    }
    if (status.present) {
      map['status'] = Variable<int>(status.value);
    }
    if (consecutiveCorrect.present) {
      map['consecutive_correct'] = Variable<int>(consecutiveCorrect.value);
    }
    if (intervalDays.present) {
      map['interval_days'] = Variable<int>(intervalDays.value);
    }
    if (nextReviewDate.present) {
      map['next_review_date'] = Variable<DateTime>(nextReviewDate.value);
    }
    if (lastAnsweredAt.present) {
      map['last_answered_at'] = Variable<DateTime>(lastAnsweredAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TopicProgressTableCompanion(')
          ..write('topicId: $topicId, ')
          ..write('courseId: $courseId, ')
          ..write('status: $status, ')
          ..write('consecutiveCorrect: $consecutiveCorrect, ')
          ..write('intervalDays: $intervalDays, ')
          ..write('nextReviewDate: $nextReviewDate, ')
          ..write('lastAnsweredAt: $lastAnsweredAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $TopicProgressTableTable topicProgressTable =
      $TopicProgressTableTable(this);
  late final ProgressDao progressDao = ProgressDao(this as AppDatabase);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [topicProgressTable];
}

typedef $$TopicProgressTableTableCreateCompanionBuilder =
    TopicProgressTableCompanion Function({
      required String topicId,
      required String courseId,
      Value<int> status,
      Value<int> consecutiveCorrect,
      Value<int> intervalDays,
      Value<DateTime?> nextReviewDate,
      Value<DateTime?> lastAnsweredAt,
      Value<int> rowid,
    });
typedef $$TopicProgressTableTableUpdateCompanionBuilder =
    TopicProgressTableCompanion Function({
      Value<String> topicId,
      Value<String> courseId,
      Value<int> status,
      Value<int> consecutiveCorrect,
      Value<int> intervalDays,
      Value<DateTime?> nextReviewDate,
      Value<DateTime?> lastAnsweredAt,
      Value<int> rowid,
    });

class $$TopicProgressTableTableFilterComposer
    extends Composer<_$AppDatabase, $TopicProgressTableTable> {
  $$TopicProgressTableTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get topicId => $composableBuilder(
    column: $table.topicId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get courseId => $composableBuilder(
    column: $table.courseId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get consecutiveCorrect => $composableBuilder(
    column: $table.consecutiveCorrect,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get intervalDays => $composableBuilder(
    column: $table.intervalDays,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get nextReviewDate => $composableBuilder(
    column: $table.nextReviewDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get lastAnsweredAt => $composableBuilder(
    column: $table.lastAnsweredAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$TopicProgressTableTableOrderingComposer
    extends Composer<_$AppDatabase, $TopicProgressTableTable> {
  $$TopicProgressTableTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get topicId => $composableBuilder(
    column: $table.topicId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get courseId => $composableBuilder(
    column: $table.courseId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get consecutiveCorrect => $composableBuilder(
    column: $table.consecutiveCorrect,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get intervalDays => $composableBuilder(
    column: $table.intervalDays,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get nextReviewDate => $composableBuilder(
    column: $table.nextReviewDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get lastAnsweredAt => $composableBuilder(
    column: $table.lastAnsweredAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$TopicProgressTableTableAnnotationComposer
    extends Composer<_$AppDatabase, $TopicProgressTableTable> {
  $$TopicProgressTableTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get topicId =>
      $composableBuilder(column: $table.topicId, builder: (column) => column);

  GeneratedColumn<String> get courseId =>
      $composableBuilder(column: $table.courseId, builder: (column) => column);

  GeneratedColumn<int> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<int> get consecutiveCorrect => $composableBuilder(
    column: $table.consecutiveCorrect,
    builder: (column) => column,
  );

  GeneratedColumn<int> get intervalDays => $composableBuilder(
    column: $table.intervalDays,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get nextReviewDate => $composableBuilder(
    column: $table.nextReviewDate,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get lastAnsweredAt => $composableBuilder(
    column: $table.lastAnsweredAt,
    builder: (column) => column,
  );
}

class $$TopicProgressTableTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $TopicProgressTableTable,
          TopicProgressTableData,
          $$TopicProgressTableTableFilterComposer,
          $$TopicProgressTableTableOrderingComposer,
          $$TopicProgressTableTableAnnotationComposer,
          $$TopicProgressTableTableCreateCompanionBuilder,
          $$TopicProgressTableTableUpdateCompanionBuilder,
          (
            TopicProgressTableData,
            BaseReferences<
              _$AppDatabase,
              $TopicProgressTableTable,
              TopicProgressTableData
            >,
          ),
          TopicProgressTableData,
          PrefetchHooks Function()
        > {
  $$TopicProgressTableTableTableManager(
    _$AppDatabase db,
    $TopicProgressTableTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$TopicProgressTableTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$TopicProgressTableTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$TopicProgressTableTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> topicId = const Value.absent(),
                Value<String> courseId = const Value.absent(),
                Value<int> status = const Value.absent(),
                Value<int> consecutiveCorrect = const Value.absent(),
                Value<int> intervalDays = const Value.absent(),
                Value<DateTime?> nextReviewDate = const Value.absent(),
                Value<DateTime?> lastAnsweredAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TopicProgressTableCompanion(
                topicId: topicId,
                courseId: courseId,
                status: status,
                consecutiveCorrect: consecutiveCorrect,
                intervalDays: intervalDays,
                nextReviewDate: nextReviewDate,
                lastAnsweredAt: lastAnsweredAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String topicId,
                required String courseId,
                Value<int> status = const Value.absent(),
                Value<int> consecutiveCorrect = const Value.absent(),
                Value<int> intervalDays = const Value.absent(),
                Value<DateTime?> nextReviewDate = const Value.absent(),
                Value<DateTime?> lastAnsweredAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TopicProgressTableCompanion.insert(
                topicId: topicId,
                courseId: courseId,
                status: status,
                consecutiveCorrect: consecutiveCorrect,
                intervalDays: intervalDays,
                nextReviewDate: nextReviewDate,
                lastAnsweredAt: lastAnsweredAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$TopicProgressTableTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $TopicProgressTableTable,
      TopicProgressTableData,
      $$TopicProgressTableTableFilterComposer,
      $$TopicProgressTableTableOrderingComposer,
      $$TopicProgressTableTableAnnotationComposer,
      $$TopicProgressTableTableCreateCompanionBuilder,
      $$TopicProgressTableTableUpdateCompanionBuilder,
      (
        TopicProgressTableData,
        BaseReferences<
          _$AppDatabase,
          $TopicProgressTableTable,
          TopicProgressTableData
        >,
      ),
      TopicProgressTableData,
      PrefetchHooks Function()
    >;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$TopicProgressTableTableTableManager get topicProgressTable =>
      $$TopicProgressTableTableTableManager(_db, _db.topicProgressTable);
}
