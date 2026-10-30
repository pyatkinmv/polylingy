import 'dart:math';
import 'package:flutter_test/flutter_test.dart';
import 'package:polylingy/data/topic_repository.dart';
import 'package:polylingy/models/course.dart';

List<dynamic> topics(List<Object?> ids) => [
      for (final id in ids) {'subject': 's', 'id': ?id},
    ];

List<Object?> idsOf(List<dynamic> topics) => [for (final t in topics) t['id']];

void main() {
  group('ensureTopicIds', () {
    test('legacy topics get their index, matching old courseId::index ids', () {
      final t = topics([null, null, null]);
      expect(TopicRepository.ensureTopicIds(t), isTrue);
      expect(idsOf(t), ['0', '1', '2']);
    });

    test('existing unique ids are kept and nothing changes', () {
      final t = topics(['a', 'b']);
      expect(TopicRepository.ensureTopicIds(t), isFalse);
      expect(idsOf(t), ['a', 'b']);
    });

    test('ids stay stable after a topic is removed', () {
      final t = topics([null, null, null]);
      TopicRepository.ensureTopicIds(t);
      t.removeAt(1);
      expect(TopicRepository.ensureTopicIds(t), isFalse);
      expect(idsOf(t), ['0', '2']);
    });

    test('duplicate ids are replaced with fresh unique ones', () {
      final t = topics(['x', 'x', null]);
      expect(TopicRepository.ensureTopicIds(t, Random(1)), isTrue);
      final ids = idsOf(t);
      expect(ids[0], 'x');
      expect(ids[1], isNot('x'));
      expect(ids[2], '2');
      expect(ids.toSet().length, 3);
    });

    test('non-string ids are normalized to strings', () {
      final t = topics([7]);
      expect(TopicRepository.ensureTopicIds(t), isTrue);
      expect(idsOf(t), ['7']);
    });
  });

  group('Topic.fromJson', () {
    test('uses stored id, not the position', () {
      final topic = Topic.fromJson({'id': 'abc', 'subject': 's'}, 'course', 5);
      expect(topic.id, 'course::abc');
    });

    test('falls back to index when id is missing', () {
      final topic = Topic.fromJson({'subject': 's'}, 'course', 5);
      expect(topic.id, 'course::5');
    });
  });
}
