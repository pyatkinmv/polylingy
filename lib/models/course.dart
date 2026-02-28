class Exercise {
  final String task;
  final String answer;
  final String exampleExplanation;

  const Exercise({
    required this.task,
    required this.answer,
    required this.exampleExplanation,
  });

  factory Exercise.fromJson(Map<String, dynamic> json) => Exercise(
        task: json['task'] as String? ?? '',
        answer: json['answer'] as String? ?? '',
        exampleExplanation: json['exampleExplanation'] as String? ?? '',
      );
}

class Topic {
  final String id;
  final String subject;
  final String generalExplanation;
  final List<Exercise> exercises;

  const Topic({
    required this.id,
    required this.subject,
    required this.generalExplanation,
    required this.exercises,
  });

  factory Topic.fromJson(Map<String, dynamic> json, String courseId, int index) {
    return Topic(
      id: '$courseId::$index',
      subject: json['subject'] as String? ?? '',
      generalExplanation: json['generalExplanation'] as String? ?? '',
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
