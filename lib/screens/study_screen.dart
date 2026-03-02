import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:polylingy/data/progress_repository.dart';
import 'package:polylingy/models/course.dart';
import 'package:polylingy/models/topic_progress.dart';
import 'package:polylingy/services/repetition_policy.dart';

enum _StudyState { exercise, result, done }

enum _ResultMode { correct, partial, incorrect }

class StudyScreen extends StatefulWidget {
  final Course course;
  final ProgressRepository progressRepo;
  final RepetitionPolicy policy;

  const StudyScreen({
    super.key,
    required this.course,
    required this.progressRepo,
    required this.policy,
  });

  @override
  State<StudyScreen> createState() => _StudyScreenState();
}

class _StudyScreenState extends State<StudyScreen> {
  static const _correctPhrases = [
    'Correct!',
    'Well done!',
    'Right!',
    'Got it!',
    'Yes!',
  ];
  static const _partialPhrases = [
    'Partially right.',
    'Some correct!',
    'Half right!',
    'Almost!',
    'Close!',
  ];
  static const _incorrectPhrases = [
    'Incorrect.',
    'Not quite.',
    'Try again.',
    'Missed it.',
    'Wrong.',
  ];

  static const _digitKeys = [
    LogicalKeyboardKey.digit1,
    LogicalKeyboardKey.digit2,
    LogicalKeyboardKey.digit3,
    LogicalKeyboardKey.digit4,
    LogicalKeyboardKey.digit5,
    LogicalKeyboardKey.digit6,
    LogicalKeyboardKey.digit7,
    LogicalKeyboardKey.digit8,
    LogicalKeyboardKey.digit9,
  ];

  final _random = Random();
  final _answerController = TextEditingController();

  _StudyState _state = _StudyState.exercise;
  Map<String, TopicProgress> _progressMap = {};
  List<String> _eligibleIds = [];

  Topic? _currentTopic;
  Exercise? _currentExercise;
  Map<String, String> _userAnswers = {};
  _ResultMode? _resultMode;
  String _resultPhrase = '';

  @override
  void initState() {
    super.initState();
    _initSession();
  }

  @override
  void dispose() {
    _answerController.dispose();
    super.dispose();
  }

  List<String> _sortedKeys(Map<String, String> map) {
    final keys = map.keys.toList()..sort((a, b) => int.parse(a).compareTo(int.parse(b)));
    return keys;
  }

  Future<void> _initSession() async {
    final allProgress = await widget.progressRepo.getAllForCourse(widget.course.id);
    final progressMap = {for (final p in allProgress) p.topicId: p};

    final today = _today();
    for (final topic in widget.course.topics) {
      if (!progressMap.containsKey(topic.id)) {
        progressMap[topic.id] = TopicProgress(
          topicId: topic.id,
          courseId: widget.course.id,
          status: ProgressStatus.newTopic,
          consecutiveCorrect: 0,
          intervalDays: 1,
        );
      }
    }

    final eligibleIds = widget.policy.eligibleTopicIds(
      widget.course.topics.map((t) => progressMap[t.id]!).toList(),
      today,
    );
    eligibleIds.shuffle(_random);

    setState(() {
      _progressMap = progressMap;
      _eligibleIds = eligibleIds;
    });

    _pickNext();
  }

  DateTime _today() {
    final now = DateTime.now();
    return DateTime(now.year, now.month, now.day);
  }

  void _pickNext() {
    if (_eligibleIds.isEmpty) {
      setState(() => _state = _StudyState.done);
      return;
    }

    final topicId = _eligibleIds[_random.nextInt(_eligibleIds.length)];
    final topic = widget.course.topics.firstWhere((t) => t.id == topicId);
    _pickExerciseForTopic(topic);
  }

  void _pickExerciseForTopic(Topic topic) {
    final exercise = topic.exercises[_random.nextInt(topic.exercises.length)];
    _answerController.clear();
    setState(() {
      _currentTopic = topic;
      _currentExercise = exercise;
      _userAnswers = {};
      _resultMode = null;
      _resultPhrase = '';
      _state = _StudyState.exercise;
    });
  }

  void _submit() {
    final keys = _sortedKeys(_currentExercise!.answer);
    final parts = _answerController.text.split(',').map((s) => s.trim()).toList();
    final answers = {
      for (var i = 0; i < keys.length; i++) keys[i]: i < parts.length ? parts[i] : '',
    };
    final correctCount = keys.where((k) {
      final user = answers[k] ?? '';
      final correct = _currentExercise!.answer[k] ?? '';
      return user.trim().toLowerCase() == correct.trim().toLowerCase();
    }).length;
    final mode = correctCount == keys.length
        ? _ResultMode.correct
        : correctCount == 0
            ? _ResultMode.incorrect
            : _ResultMode.partial;
    final phrases = switch (mode) {
      _ResultMode.correct   => _correctPhrases,
      _ResultMode.partial   => _partialPhrases,
      _ResultMode.incorrect => _incorrectPhrases,
    };
    setState(() {
      _userAnswers = answers;
      _resultMode = mode;
      _resultPhrase = phrases[_random.nextInt(phrases.length)];
      _state = _StudyState.result;
    });
  }

  Future<void> _rate(bool correct) async {
    final topic = _currentTopic!;
    final result = widget.policy.recordAnswer(topic.id, correct);

    if (result.completed) {
      final updated = widget.policy.advance(_progressMap[topic.id]!, result.mistakes, _today());
      await widget.progressRepo.saveProgress(updated);
      _progressMap[topic.id] = updated;
      if (!updated.isEligibleToday(_today())) {
        _eligibleIds.remove(topic.id);
      }
      _pickNext();
    } else {
      _pickExerciseForTopic(topic);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.course.name),
      ),
      body: switch (_state) {
        _StudyState.exercise => _buildExercise(),
        _StudyState.result => _buildResult(),
        _StudyState.done => _buildDone(),
      },
    );
  }

  Color _modeColor(_ResultMode mode) => switch (mode) {
    _ResultMode.correct   => const Color(0xFFA5D6A7),
    _ResultMode.partial   => const Color(0xFFFFE082),
    _ResultMode.incorrect => const Color(0xFFEF9A9A),
  };

  Widget _buildAnnotatedTask() {
    final exercise = _currentExercise!;
    final pattern = RegExp(r'\{(\d+)\}');
    final spans = <InlineSpan>[];
    int lastEnd = 0;
    for (final match in pattern.allMatches(exercise.task)) {
      if (match.start > lastEnd) {
        spans.add(TextSpan(text: exercise.task.substring(lastEnd, match.start)));
      }
      final key = match.group(1)!;
      final correct = exercise.answer[key] ?? '';
      final user = _userAnswers[key] ?? '';
      final isCorrect = user.trim().toLowerCase() == correct.trim().toLowerCase();
      if (isCorrect) {
        spans.add(TextSpan(
          text: correct,
          style: const TextStyle(color: Colors.green, fontWeight: FontWeight.bold),
        ));
      } else {
        final text = user.trim().isEmpty ? correct : '${user.trim()} → $correct';
        spans.add(TextSpan(
          text: text,
          style: const TextStyle(color: Colors.red, fontWeight: FontWeight.bold),
        ));
      }
      lastEnd = match.end;
    }
    if (lastEnd < exercise.task.length) {
      spans.add(TextSpan(text: exercise.task.substring(lastEnd)));
    }
    return SizedBox(
      width: double.infinity,
      child: RichText(
        textAlign: TextAlign.center,
        text: TextSpan(
          style: Theme.of(context).textTheme.bodyLarge,
          children: spans,
        ),
      ),
    );
  }

  Widget _buildExercise() {
    if (_currentTopic == null || _currentExercise == null) {
      return const Center(child: CircularProgressIndicator());
    }
    final exercise = _currentExercise!;
    final taskText = exercise.task.replaceAllMapped(RegExp(r'\{\d+\}'), (_) => '___');
    final keys = _sortedKeys(exercise.answer);
    final multiBlank = keys.length > 1;
    return SingleChildScrollView(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  width: double.infinity,
                  child: Text(
                    _currentTopic!.subject,
                    style: Theme.of(context).textTheme.titleLarge,
                    textAlign: TextAlign.center,
                  ),
                ),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  child: Text(taskText, style: Theme.of(context).textTheme.bodyLarge, textAlign: TextAlign.center),
                ),
                const SizedBox(height: 24),
                TextField(
                  controller: _answerController,
                  autofocus: true,
                  decoration: InputDecoration(
                    border: const OutlineInputBorder(),
                    labelText: multiBlank ? 'Your answers (comma-separated)' : 'Your answer',
                  ),
                  onSubmitted: (_) => _submit(),
                ),
                const SizedBox(height: 16),
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton(
                    onPressed: _submit,
                    child: const Text('Submit'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildResult() {
    final exercise = _currentExercise!;
    final topic = _currentTopic!;
    final buttons = [
      (label: 'Incorrect', onPressed: () => _rate(false), filled: false),
      (label: 'Correct',   onPressed: () => _rate(true),  filled: true),
    ];
    return Focus(
      autofocus: true,
      onKeyEvent: (_, event) {
        if (event is! KeyDownEvent) return KeyEventResult.ignored;
        final index = _digitKeys.indexOf(event.logicalKey);
        if (index >= 0 && index < buttons.length) {
          buttons[index].onPressed();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 720),
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox(
                          width: double.infinity,
                          child: Text(
                            topic.subject,
                            style: Theme.of(context).textTheme.titleLarge,
                            textAlign: TextAlign.center,
                          ),
                        ),
                        const SizedBox(height: 24),
                        _buildAnnotatedTask(),
                        const SizedBox(height: 12),
                        Container(
                          width: double.infinity,
                          alignment: Alignment.center,
                          padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
                          decoration: BoxDecoration(
                            color: _modeColor(_resultMode!),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            _resultPhrase,
                            style: Theme.of(context).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold, fontSize: 17),
                            textAlign: TextAlign.center,
                          ),
                        ),
                        if (exercise.exampleExplanation.isNotEmpty) ...[
                          const SizedBox(height: 16),
                          _ExplanationBox(
                            label: 'Specific explanation',
                            color: const Color(0xFFDEF3C8),
                            child: Text(exercise.exampleExplanation, style: Theme.of(context).textTheme.bodyMedium),
                          ),
                        ],
                        if (topic.generalExplanation.isNotEmpty) ...[
                          const SizedBox(height: 16),
                          _ExplanationBox(
                            label: 'Explanation',
                            color: const Color(0xFFE3F2FD),
                            child: _FormattedContent(
                              content: topic.generalExplanation,
                              format: topic.generalExplanationFormat,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
          Container(
            decoration: BoxDecoration(
              border: Border(top: BorderSide(color: Colors.grey.shade300, width: 1.5)),
            ),
            padding: const EdgeInsets.fromLTRB(32, 12, 32, 20),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 720),
                child: OverflowBar(
                  alignment: MainAxisAlignment.center,
                  spacing: 12,
                  children: [
                    for (final btn in buttons)
                      btn.filled
                          ? FilledButton(onPressed: btn.onPressed, child: Text(btn.label))
                          : OutlinedButton(onPressed: btn.onPressed, child: Text(btn.label)),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDone() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            'All done for today!',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          Text(
            'Come back tomorrow to continue.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 32),
          FilledButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Back to home'),
          ),
        ],
      ),
    );
  }
}

class _ExplanationBox extends StatelessWidget {
  final String label;
  final Color color;
  final Widget child;

  const _ExplanationBox({required this.label, required this.color, required this.child});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(8),
      ),
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(color: Colors.grey[700]),
          ),
          const SizedBox(height: 4),
          child,
        ],
      ),
    );
  }
}

class _FormattedContent extends StatelessWidget {
  final String content;
  final ExplanationFormat format;

  const _FormattedContent({required this.content, required this.format});

  @override
  Widget build(BuildContext context) {
    return switch (format) {
      ExplanationFormat.html => Html(data: content),
      ExplanationFormat.text => Text(content, style: Theme.of(context).textTheme.bodyMedium),
    };
  }
}
