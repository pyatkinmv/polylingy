import 'package:flutter/material.dart';
import 'package:polylingy/data/progress_repository.dart';
import 'package:polylingy/data/topic_repository.dart';
import 'package:polylingy/models/course.dart';
import 'package:polylingy/models/topic_progress.dart';
import 'package:polylingy/screens/study_screen.dart';
import 'package:polylingy/services/sm2_policy.dart';

class HomeScreen extends StatefulWidget {
  final TopicRepository topicRepo;
  final ProgressRepository progressRepo;

  const HomeScreen({
    super.key,
    required this.topicRepo,
    required this.progressRepo,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  List<_CourseStats>? _courses;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      await widget.topicRepo.ensureSampleCourse();
      final courses = await widget.topicRepo.loadCourses();
      final today = DateTime.now();
      final todayDate = DateTime(today.year, today.month, today.day);

      final stats = <_CourseStats>[];
      for (final course in courses) {
        final allProgress = await widget.progressRepo.getAllForCourse(course.id);
        final progressById = {for (final p in allProgress) p.topicId: p};

        int newCount = 0, toReview = 0, doneToday = 0;
        for (final topic in course.topics) {
          final p = progressById[topic.id];
          if (p == null || p.status == ProgressStatus.newTopic) {
            newCount++;
          } else if (p.status == ProgressStatus.learning) {
            newCount++;
          } else if (p.status == ProgressStatus.toReview) {
            if (p.nextReviewDate != null && !p.nextReviewDate!.isAfter(todayDate)) {
              toReview++;
            } else {
              if (p.lastAnsweredAt != null) {
                final last = p.lastAnsweredAt!;
                if (last.year == today.year && last.month == today.month && last.day == today.day) {
                  doneToday++;
                }
              }
            }
          }
        }
        stats.add(_CourseStats(course: course, newCount: newCount, toReview: toReview, doneToday: doneToday));
      }

      setState(() => _courses = stats);
    } catch (e) {
      setState(() => _error = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Polylingy'),
        centerTitle: false,
        actions: [
          TextButton.icon(
            onPressed: _showNewCourseDialog,
            icon: const Icon(Icons.add),
            label: const Text('New course'),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_error != null) {
      return Center(child: Text('Error: $_error'));
    }
    if (_courses == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 720),
        child: ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: _courses!.length,
          separatorBuilder: (_, _) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final stats = _courses![index];
            return _CourseCard(
              stats: stats,
              onStart: () async {
                await Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => StudyScreen(
                      course: stats.course,
                      progressRepo: widget.progressRepo,
                      policy: Sm2Policy(),
                    ),
                  ),
                );
                _load();
              },
            );
          },
        ),
      ),
    );
  }

  Future<void> _showNewCourseDialog() async {
    final controller = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New course'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Course name'),
          onSubmitted: (_) => Navigator.pop(ctx, true),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('OK')),
        ],
      ),
    );
    final name = controller.text.trim();
    if (confirmed == true && name.isNotEmpty) {
      await widget.topicRepo.createCourse(name);
      _load();
    }
  }
}

class _CourseStats {
  final Course course;
  final int newCount;
  final int toReview;
  final int doneToday;

  const _CourseStats({
    required this.course,
    required this.newCount,
    required this.toReview,
    required this.doneToday,
  });

  int get eligible => newCount + toReview;
}

class _CourseCard extends StatelessWidget {
  final _CourseStats stats;
  final VoidCallback onStart;

  const _CourseCard({required this.stats, required this.onStart});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(stats.course.name, style: theme.textTheme.titleMedium),
                  const SizedBox(height: 4),
                  Text(
                    '${stats.newCount} new · ${stats.toReview} to review · ${stats.doneToday} done today',
                    style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey[600]),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 16),
            FilledButton(
              onPressed: stats.eligible > 0 ? onStart : null,
              child: const Text('Start'),
            ),
          ],
        ),
      ),
    );
  }
}
