class SessionResult {
  final bool completed;
  final int mistakes;
  const SessionResult({required this.completed, required this.mistakes});
}

abstract class GraduationPolicy {
  SessionResult recordAnswer(String topicId, bool correct);
}

class _TopicSession {
  int consecutive = 0;
  int mistakes = 0;
}

/// Graduates a topic after [goal] consecutive correct answers (default 3).
class ConsecutiveCorrectPolicy implements GraduationPolicy {
  ConsecutiveCorrectPolicy({this.goal = 3});

  final int goal;
  final _sessions = <String, _TopicSession>{};

  @override
  SessionResult recordAnswer(String topicId, bool correct) {
    final s = _sessions.putIfAbsent(topicId, _TopicSession.new);
    if (correct) {
      s.consecutive++;
    } else {
      s.consecutive = 0;
      s.mistakes++;
    }
    final done = s.consecutive >= goal;
    if (done) _sessions.remove(topicId);
    return SessionResult(completed: done, mistakes: done ? s.mistakes : 0);
  }
}
