enum ExplanationFormat { text, html }

class Exercise {
  final String task;
  final Map<String, String> answer;
  final String exampleExplanation;

  const Exercise({
    required this.task,
    required this.answer,
    required this.exampleExplanation,
  });

  factory Exercise.fromJson(Map<String, dynamic> json) => Exercise(
        task: json['task'] as String? ?? '',
        answer: _parseAnswer(json['answer']),
        exampleExplanation: json['exampleExplanation'] as String? ?? '',
      );

  static Map<String, String> _parseAnswer(dynamic raw) {
    if (raw is String) return {'0': raw};
    if (raw is Map) return {for (final e in raw.entries) e.key as String: e.value as String};
    return {'0': ''};
  }
}

class Topic {
  final String id;
  final String subject;
  final String generalExplanation;
  final ExplanationFormat generalExplanationFormat;
  final List<Exercise> exercises;

  const Topic({
    required this.id,
    required this.subject,
    required this.generalExplanation,
    required this.generalExplanationFormat,
    required this.exercises,
  });

  factory Topic.fromJson(Map<String, dynamic> json, String courseId, int index) {
    return Topic(
      id: '$courseId::$index',
      subject: json['subject'] as String? ?? '',
      generalExplanation: json['generalExplanation'] as String? ?? '',
      generalExplanationFormat: switch (json['generalExplanationFormat'] as String?) {
        'HTML' => ExplanationFormat.html,
        _ => ExplanationFormat.text,
      },
      exercises: (json['exercises'] as List<dynamic>? ?? [])
          .map((e) => Exercise.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class Course {
  final String id;
  final String name;
  final List<Topic> topics;

  const Course({
    required this.id,
    required this.name,
    required this.topics,
  });

  factory Course.fromJson(Map<String, dynamic> json, String id) {
    final topics = <Topic>[];
    final rawTopics = json['topics'] as List<dynamic>? ?? [];
    for (var i = 0; i < rawTopics.length; i++) {
      topics.add(Topic.fromJson(rawTopics[i] as Map<String, dynamic>, id, i));
    }
    return Course(
      id: id,
      name: json['name'] as String? ?? 'Unnamed Course',
      topics: topics,
    );
  }
}
