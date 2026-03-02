import 'package:flutter_test/flutter_test.dart';
import 'package:polylingy/models/topic_progress.dart';
import 'package:polylingy/services/sm2_policy.dart';

// ─── helpers ────────────────────────────────────────────────────────────────

const _courseId = 'course1';
final _today = DateTime(2026, 1, 15);

TopicProgress newCard(String id) => TopicProgress(
      topicId: id,
      courseId: _courseId,
      status: ProgressStatus.newTopic,
      consecutiveCorrect: 0,
      intervalDays: 1,
      easeFactor: 2.5,
    );

TopicProgress reviewCard(
  String id, {
  required int intervalDays,
  required double easeFactor,
  DateTime? nextReviewDate,
}) =>
    TopicProgress(
      topicId: id,
      courseId: _courseId,
      status: ProgressStatus.toReview,
      consecutiveCorrect: 0,
      intervalDays: intervalDays,
      easeFactor: easeFactor,
      nextReviewDate: nextReviewDate ?? _today,
    );

// Shorthand: drive a sequence of answers and return the final SessionResult.
// answers: list of true (correct) / false (incorrect).
// The policy is fresh for each call unless you pass one in.
({bool completed, int mistakes}) drive(
  Sm2Policy policy,
  String topicId,
  List<bool> answers,
) {
  late ({bool completed, int mistakes}) last;
  for (final a in answers) {
    final r = policy.recordAnswer(topicId, a);
    last = (completed: r.completed, mistakes: r.mistakes);
  }
  return last;
}

// ─── tests ──────────────────────────────────────────────────────────────────

void main() {
  // ══════════════════════════════════════════════════════════════════════════
  // SESSION COMPLETION  (recordAnswer)
  // ══════════════════════════════════════════════════════════════════════════
  group('session — completion rule (3 consecutive correct)', () {
    test('3 correct in a row → completes immediately', () {
      final p = Sm2Policy();
      expect(drive(p, 'a', [true, true, true]).completed, isTrue);
    });

    test('2 correct is not enough', () {
      final p = Sm2Policy();
      expect(drive(p, 'a', [true, true]).completed, isFalse);
    });

    test('1 correct is not enough', () {
      final p = Sm2Policy();
      expect(drive(p, 'a', [true]).completed, isFalse);
    });

    test('1 incorrect then 3 correct → completes on 4th answer', () {
      final p = Sm2Policy();
      final results = [false, true, true, true].map((a) => p.recordAnswer('a', a)).toList();
      expect(results[0].completed, isFalse);
      expect(results[1].completed, isFalse);
      expect(results[2].completed, isFalse);
      expect(results[3].completed, isTrue);
    });

    test('correct, incorrect, correct, correct, correct → completes on 5th', () {
      final p = Sm2Policy();
      // streak: 1, reset, 1, 2, 3 ✓
      final results = [true, false, true, true, true].map((a) => p.recordAnswer('a', a)).toList();
      expect(results[4].completed, isTrue);
    });

    test('incorrect resets the streak to zero', () {
      final p = Sm2Policy();
      // build streak of 2, then break it
      p.recordAnswer('a', true);
      p.recordAnswer('a', true);
      p.recordAnswer('a', false); // reset
      // now need 3 more
      p.recordAnswer('a', true);
      p.recordAnswer('a', true);
      expect(p.recordAnswer('a', true).completed, isTrue);
    });

    test('many wrong answers followed by 3 correct → still completes', () {
      final p = Sm2Policy();
      for (var i = 0; i < 10; i++) p.recordAnswer('a', false);
      expect(drive(p, 'a', [true, true, true]).completed, isTrue);
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // MISTAKE COUNTING
  // ══════════════════════════════════════════════════════════════════════════
  group('session — mistake count at completion', () {
    test('0 mistakes: perfect run', () {
      final p = Sm2Policy();
      expect(drive(p, 'a', [true, true, true]).mistakes, 0);
    });

    test('1 mistake before eventual 3-in-a-row', () {
      final p = Sm2Policy();
      expect(drive(p, 'a', [false, true, true, true]).mistakes, 1);
    });

    test('2 mistakes: incorrect, incorrect, then 3 correct', () {
      final p = Sm2Policy();
      expect(drive(p, 'a', [false, false, true, true, true]).mistakes, 2);
    });

    test('3 mistakes → q=2 territory', () {
      final p = Sm2Policy();
      expect(drive(p, 'a', [false, false, false, true, true, true]).mistakes, 3);
    });

    test('mistakes counted across streak resets', () {
      // wrong, right×2, wrong, right×3 → 2 mistakes total
      final p = Sm2Policy();
      expect(drive(p, 'a', [false, true, true, false, true, true, true]).mistakes, 2);
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // MULTI-TOPIC INDEPENDENCE
  // ══════════════════════════════════════════════════════════════════════════
  group('session — topics are tracked independently', () {
    test('completing topic A does not affect topic B streak', () {
      final p = Sm2Policy();
      p.recordAnswer('A', true); // A: streak 1
      p.recordAnswer('B', true); // B: streak 1
      p.recordAnswer('A', true); // A: streak 2
      p.recordAnswer('A', true); // A: done
      // B still needs 2 more
      expect(p.recordAnswer('B', true).completed, isFalse);
      expect(p.recordAnswer('B', true).completed, isTrue);
    });

    test('incorrect on A does not reset B streak', () {
      final p = Sm2Policy();
      p.recordAnswer('B', true);
      p.recordAnswer('B', true);
      p.recordAnswer('A', false); // reset A only
      expect(p.recordAnswer('B', true).completed, isTrue);
    });

    test('two topics can complete in the same session at different times', () {
      final p = Sm2Policy();
      final r1 = drive(p, 'X', [true, true, true]);
      final r2 = drive(p, 'Y', [false, true, true, true]);
      expect(r1.completed, isTrue);
      expect(r2.completed, isTrue);
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // SESSION STATE RESET AFTER COMPLETION
  // ══════════════════════════════════════════════════════════════════════════
  group('session — state is cleared after completion', () {
    test('after a topic completes, its session state resets for re-use', () {
      final p = Sm2Policy();
      // first run: 1 mistake
      drive(p, 'a', [false, true, true, true]);
      // second run: perfect — mistakes should start fresh from 0
      final r = drive(p, 'a', [true, true, true]);
      expect(r.mistakes, 0);
    });

    test('streak counter starts from 0 after completion', () {
      final p = Sm2Policy();
      drive(p, 'a', [true, true, true]);  // complete
      // now need 3 more — 2 should not be enough
      expect(drive(p, 'a', [true, true]).completed, isFalse);
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // QUALITY SCORE MAPPING
  // ══════════════════════════════════════════════════════════════════════════
  group('advance — quality mapped from session mistakes', () {
    // We observe the effect of q through EF changes.
    test('0 mistakes → q=5 → EF increases by 0.1', () {
      final p = Sm2Policy();
      final result = p.advance(newCard('a'), 0, _today);
      expect(result.easeFactor, closeTo(2.6, 0.001));
    });

    test('1 mistake → q=4 → EF unchanged', () {
      final p = Sm2Policy();
      final result = p.advance(newCard('a'), 1, _today);
      expect(result.easeFactor, closeTo(2.5, 0.001));
    });

    test('2 mistakes → q=3 → EF decreases by 0.15', () {
      final p = Sm2Policy();
      final result = p.advance(newCard('a'), 2, _today);
      expect(result.easeFactor, closeTo(2.35, 0.001));
    });

    test('3 mistakes → q=2 → EF decreases by 0.3', () {
      final p = Sm2Policy();
      final result = p.advance(newCard('a'), 3, _today);
      expect(result.easeFactor, closeTo(2.2, 0.001));
    });

    test('10 mistakes → still q=2 → EF decreases by 0.3 (not more)', () {
      final p = Sm2Policy();
      final result = p.advance(newCard('a'), 10, _today);
      expect(result.easeFactor, closeTo(2.2, 0.001));
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // EASE FACTOR — MINIMUM FLOOR
  // ══════════════════════════════════════════════════════════════════════════
  group('advance — EF floor at 1.3', () {
    test('EF=1.4, q=2 (3 mistakes) → would go to 1.1 but clamped to 1.3', () {
      final p = Sm2Policy();
      final card = reviewCard('a', intervalDays: 5, easeFactor: 1.4);
      expect(p.advance(card, 3, _today).easeFactor, closeTo(1.3, 0.001));
    });

    test('EF=1.3, q=2 → stays at 1.3 (already at floor)', () {
      final p = Sm2Policy();
      final card = reviewCard('a', intervalDays: 5, easeFactor: 1.3);
      expect(p.advance(card, 3, _today).easeFactor, closeTo(1.3, 0.001));
    });

    test('EF=1.35, q=3 (2 mistakes) → 1.35-0.15=1.2 → clamped to 1.3', () {
      final p = Sm2Policy();
      final card = reviewCard('a', intervalDays: 5, easeFactor: 1.35);
      expect(p.advance(card, 2, _today).easeFactor, closeTo(1.3, 0.001));
    });

    test('EF=1.3, q=4 (1 mistake) → unchanged at 1.3 (no change for q=4)', () {
      final p = Sm2Policy();
      final card = reviewCard('a', intervalDays: 5, easeFactor: 1.3);
      expect(p.advance(card, 1, _today).easeFactor, closeTo(1.3, 0.001));
    });

    test('EF=1.3, q=5 (0 mistakes) → increases to 1.4', () {
      final p = Sm2Policy();
      final card = reviewCard('a', intervalDays: 5, easeFactor: 1.3);
      expect(p.advance(card, 0, _today).easeFactor, closeTo(1.4, 0.001));
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // INTERVAL CALCULATION  (new card: I=1, EF=2.5)
  // ══════════════════════════════════════════════════════════════════════════
  group('advance — interval for a fresh new card (I=1, EF=2.5)', () {
    test('0 mistakes (q=5): newEF=2.6 → I = round(1 × 2.6) = 3', () {
      final result = Sm2Policy().advance(newCard('a'), 0, _today);
      expect(result.intervalDays, 3);
    });

    test('1 mistake (q=4): EF unchanged=2.5 → I = round(1 × 2.5) = 3', () {
      // Dart rounds 2.5 → 3 (half away from zero)
      final result = Sm2Policy().advance(newCard('a'), 1, _today);
      expect(result.intervalDays, 3);
    });

    test('2 mistakes (q=3): newEF=2.35 → I = round(1 × 2.35) = 2', () {
      final result = Sm2Policy().advance(newCard('a'), 2, _today);
      expect(result.intervalDays, 2);
    });

    test('3 mistakes (q=2): interval resets to 1 regardless of EF', () {
      final result = Sm2Policy().advance(newCard('a'), 3, _today);
      expect(result.intervalDays, 1);
    });

    test('4 mistakes (q=2): same as 3 mistakes — I=1', () {
      final result = Sm2Policy().advance(newCard('a'), 4, _today);
      expect(result.intervalDays, 1);
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // INTERVAL CALCULATION  (review card with accumulated history)
  // ══════════════════════════════════════════════════════════════════════════
  group('advance — interval for a review card (I=5, EF=2.5)', () {
    test('0 mistakes (q=5): newEF=2.6 → I = round(5 × 2.6) = 13', () {
      final card = reviewCard('a', intervalDays: 5, easeFactor: 2.5);
      expect(Sm2Policy().advance(card, 0, _today).intervalDays, 13);
    });

    test('1 mistake (q=4): EF=2.5 → I = round(5 × 2.5) = 13', () {
      // 5 × 2.5 = 12.5 → rounds to 13
      final card = reviewCard('a', intervalDays: 5, easeFactor: 2.5);
      expect(Sm2Policy().advance(card, 1, _today).intervalDays, 13);
    });

    test('2 mistakes (q=3): newEF=2.35 → I = round(5 × 2.35) = 12', () {
      final card = reviewCard('a', intervalDays: 5, easeFactor: 2.5);
      expect(Sm2Policy().advance(card, 2, _today).intervalDays, 12);
    });

    test('3 mistakes (q=2): I resets to 1', () {
      final card = reviewCard('a', intervalDays: 5, easeFactor: 2.5);
      expect(Sm2Policy().advance(card, 3, _today).intervalDays, 1);
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // NEXT REVIEW DATE
  // ══════════════════════════════════════════════════════════════════════════
  group('advance — nextReviewDate', () {
    test('0 mistakes on new card: today + 3 days', () {
      final result = Sm2Policy().advance(newCard('a'), 0, _today);
      expect(result.nextReviewDate, DateTime(2026, 1, 18));
    });

    test('3 mistakes (q=2) on new card: today + 1 day', () {
      final result = Sm2Policy().advance(newCard('a'), 3, _today);
      expect(result.nextReviewDate, DateTime(2026, 1, 16));
    });

    test('0 mistakes on I=10 card (EF=2.5): today + round(10×2.6)=26 days', () {
      final card = reviewCard('a', intervalDays: 10, easeFactor: 2.5);
      final result = Sm2Policy().advance(card, 0, _today);
      expect(result.nextReviewDate, DateTime(2026, 2, 10)); // Jan 15 + 26
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // STATUS AFTER ADVANCE
  // ══════════════════════════════════════════════════════════════════════════
  group('advance — status always becomes toReview', () {
    test('new card → toReview after advance', () {
      final result = Sm2Policy().advance(newCard('a'), 0, _today);
      expect(result.status, ProgressStatus.toReview);
    });

    test('learning card → toReview after advance', () {
      final card = TopicProgress(
        topicId: 'a', courseId: _courseId,
        status: ProgressStatus.learning,
        consecutiveCorrect: 0, intervalDays: 1, easeFactor: 2.5,
      );
      final result = Sm2Policy().advance(card, 0, _today);
      expect(result.status, ProgressStatus.toReview);
    });

    test('toReview card stays toReview after advance', () {
      final card = reviewCard('a', intervalDays: 7, easeFactor: 2.5);
      expect(Sm2Policy().advance(card, 0, _today).status, ProgressStatus.toReview);
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // INTERVAL GROWTH OVER MULTIPLE PERFECT REVIEWS
  // ══════════════════════════════════════════════════════════════════════════
  group('advance — interval growth across successive perfect reviews', () {
    test('simulate 5 consecutive perfect sessions starting from a new card', () {
      final p = Sm2Policy();
      var card = newCard('a');
      final intervals = <int>[];

      for (var session = 0; session < 5; session++) {
        card = p.advance(card, 0, _today);
        intervals.add(card.intervalDays);
      }

      // Each review: I_new = round(I_old × EF_new)
      // Session 1: EF=2.6, I=round(1×2.6)=3
      // Session 2: EF=2.7, I=round(3×2.7)=8
      // Session 3: EF=2.8, I=round(8×2.8)=22
      // Session 4: EF=2.9, I=round(22×2.9)=64
      // Session 5: EF=3.0, I=round(64×3.0)=192
      expect(intervals, [3, 8, 22, 64, 192]);
    });

    test('simulate 4 reviews with 1 mistake each (q=4, EF stable)', () {
      final p = Sm2Policy();
      var card = newCard('a');
      final intervals = <int>[];

      for (var session = 0; session < 4; session++) {
        card = p.advance(card, 1, _today); // 1 mistake → q=4, EF unchanged
        intervals.add(card.intervalDays);
      }

      // EF stays at 2.5 throughout
      // Session 1: I=round(1×2.5)=3 (Dart rounds 2.5→3)
      // Session 2: I=round(3×2.5)=8 (7.5→8)
      // Session 3: I=round(8×2.5)=20
      // Session 4: I=round(20×2.5)=50
      expect(intervals, [3, 8, 20, 50]);
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // INTERVAL DECAY — HARD PERFORMANCE
  // ══════════════════════════════════════════════════════════════════════════
  group('advance — EF erosion with repeated hard reviews (q=3)', () {
    test('EF decreases each time and interval grows slower', () {
      final p = Sm2Policy();
      var card = newCard('a');
      final efs = <double>[];

      for (var session = 0; session < 5; session++) {
        card = p.advance(card, 2, _today); // 2 mistakes → q=3
        efs.add(card.easeFactor);
      }

      // EF: 2.5-0.15=2.35, 2.35-0.15=2.20, 2.20-0.15=2.05, 2.05-0.15=1.90, 1.90-0.15=1.75
      expect(efs[0], closeTo(2.35, 0.001));
      expect(efs[1], closeTo(2.20, 0.001));
      expect(efs[2], closeTo(2.05, 0.001));
      expect(efs[3], closeTo(1.90, 0.001));
      expect(efs[4], closeTo(1.75, 0.001));
    });

    test('EF floors at 1.3 after enough q=2 (forgotten) reviews', () {
      final p = Sm2Policy();
      var card = newCard('a');

      // After enough q=2 resets the EF should hit the floor
      for (var i = 0; i < 10; i++) {
        card = p.advance(card, 3, _today);
      }
      expect(card.easeFactor, closeTo(1.3, 0.001));
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // RESET THEN RECOVER
  // ══════════════════════════════════════════════════════════════════════════
  group('advance — reset (q=2) then perfect recovery', () {
    test('q=2 resets interval to 1; subsequent perfect review multiplies from 1', () {
      final p = Sm2Policy();
      var card = reviewCard('a', intervalDays: 20, easeFactor: 2.5);

      // Forgotten — 3 mistakes
      card = p.advance(card, 3, _today);
      expect(card.intervalDays, 1);
      expect(card.easeFactor, closeTo(2.2, 0.001));

      // Perfect recovery from I=1
      card = p.advance(card, 0, _today);
      // newEF = 2.2+0.1=2.3, I=round(1×2.3)=2
      expect(card.intervalDays, 2);
      expect(card.easeFactor, closeTo(2.3, 0.001));
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // ELIGIBLE TOPIC IDS
  // ══════════════════════════════════════════════════════════════════════════
  group('eligibleTopicIds', () {
    final policy = Sm2Policy();

    test('new topics are always eligible', () {
      final ids = policy.eligibleTopicIds([newCard('a'), newCard('b')], _today);
      expect(ids, containsAll(['a', 'b']));
    });

    test('learning topics are always eligible', () {
      final card = TopicProgress(
        topicId: 'a', courseId: _courseId,
        status: ProgressStatus.learning,
        consecutiveCorrect: 0, intervalDays: 1, easeFactor: 2.5,
      );
      expect(policy.eligibleTopicIds([card], _today), contains('a'));
    });

    test('toReview card due today is eligible', () {
      final card = reviewCard('a', intervalDays: 3, easeFactor: 2.5, nextReviewDate: _today);
      expect(policy.eligibleTopicIds([card], _today), contains('a'));
    });

    test('toReview card due yesterday is eligible', () {
      final yesterday = _today.subtract(const Duration(days: 1));
      final card = reviewCard('a', intervalDays: 3, easeFactor: 2.5, nextReviewDate: yesterday);
      expect(policy.eligibleTopicIds([card], _today), contains('a'));
    });

    test('toReview card due tomorrow is NOT eligible', () {
      final tomorrow = _today.add(const Duration(days: 1));
      final card = reviewCard('a', intervalDays: 3, easeFactor: 2.5, nextReviewDate: tomorrow);
      expect(policy.eligibleTopicIds([card], _today), isEmpty);
    });

    test('toReview card due in a week is NOT eligible', () {
      final future = _today.add(const Duration(days: 7));
      final card = reviewCard('a', intervalDays: 7, easeFactor: 2.5, nextReviewDate: future);
      expect(policy.eligibleTopicIds([card], _today), isEmpty);
    });

    test('mixed pool: only eligible cards returned', () {
      final due = reviewCard('due', intervalDays: 3, easeFactor: 2.5, nextReviewDate: _today);
      final future = reviewCard('future', intervalDays: 7, easeFactor: 2.5,
          nextReviewDate: _today.add(const Duration(days: 3)));
      final fresh = newCard('fresh');

      final ids = policy.eligibleTopicIds([due, future, fresh], _today);
      expect(ids, containsAll(['due', 'fresh']));
      expect(ids, isNot(contains('future')));
    });

    test('empty input → empty result', () {
      expect(policy.eligibleTopicIds([], _today), isEmpty);
    });
  });
}
