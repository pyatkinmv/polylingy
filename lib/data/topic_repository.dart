import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'package:flutter/services.dart' show rootBundle;
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
import 'package:polylingy/models/course.dart';

class TopicRepository {
  /// Gives every topic a unique string `id`, in place. Returns true if anything changed.
  ///
  /// Topics without an id get their current index, so legacy progress rows keyed by
  /// `courseId::index` stay attached. Duplicate ids get a fresh random one.
  static bool ensureTopicIds(List<dynamic> topics, [Random? random]) {
    final rng = random ?? Random.secure();
    final used = <String>{};
    var changed = false;
    for (var i = 0; i < topics.length; i++) {
      final topic = topics[i] as Map<String, dynamic>;
      var id = topic['id']?.toString();
      if (id == null || id.isEmpty) id = '$i';
      if (used.contains(id)) id = _newTopicId(used, rng);
      if (topic['id'] != id) {
        topic['id'] = id;
        changed = true;
      }
      used.add(id);
    }
    return changed;
  }

  static String _newTopicId(Set<String> used, Random rng) {
    String id;
    do {
      id = List.generate(8, (_) => rng.nextInt(16).toRadixString(16)).join();
    } while (used.contains(id));
    return id;
  }

  Future<Directory> _coursesDir() async {
    final docs = await getApplicationDocumentsDirectory();
    final dir = Directory(p.join(docs.path, 'polylingy', 'courses'));
    await dir.create(recursive: true);
    return dir;
  }

  Future<File> _courseFile(String courseId) async {
    final dir = await _coursesDir();
    return File(p.join(dir.path, '$courseId.json'));
  }

  Future<Map<String, dynamic>> _readCourse(File file) async {
    final json = jsonDecode(await file.readAsString()) as Map<String, dynamic>;
    ensureTopicIds(json['topics'] as List<dynamic>? ?? []);
    return json;
  }

  Future<void> ensureSampleCourse() async {
    final dir = await _coursesDir();
    final existing = dir.listSync().whereType<File>().where((f) => f.path.endsWith('.json'));
    if (existing.isEmpty) {
      final json = await rootBundle.loadString('assets/sample_course.json');
      final sampleFile = File(p.join(dir.path, 'sample_course.json'));
      await sampleFile.writeAsString(json);
    }
  }

  Future<void> createCourse(String name) async {
    final dir = await _coursesDir();
    final slug = name.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), '_').replaceAll(RegExp(r'_+$'), '');
    var filename = slug.isEmpty ? 'course' : slug;
    var candidate = File(p.join(dir.path, '$filename.json'));
    var suffix = 2;
    while (candidate.existsSync()) {
      candidate = File(p.join(dir.path, '${filename}_$suffix.json'));
      suffix++;
    }
    final json = jsonEncode({'name': name, 'createdAt': DateTime.now().toIso8601String(), 'topics': []});
    await candidate.writeAsString(json);
  }

  Future<void> renameCourse(String courseId, String newName) async {
    final file = await _courseFile(courseId);
    final json = await _readCourse(file);
    json['createdAt'] ??= file.lastModifiedSync().toIso8601String();
    json['name'] = newName;
    await file.writeAsString(jsonEncode(json));
  }

  Future<void> addTopic(String courseId, Map<String, dynamic> topicJson) async {
    final file = await _courseFile(courseId);
    final json = await _readCourse(file);
    final topics = json['topics'] as List<dynamic>;
    final used = {for (final t in topics) (t as Map<String, dynamic>)['id'] as String};
    final requested = topicJson['id']?.toString();
    topicJson['id'] = requested != null && requested.isNotEmpty && !used.contains(requested)
        ? requested
        : _newTopicId(used, Random.secure());
    topics.add(topicJson);
    await file.writeAsString(jsonEncode(json));
  }

  Future<void> deleteTopic(String courseId, String topicId) async {
    final file = await _courseFile(courseId);
    final json = await _readCourse(file);
    final localId = topicId.substring('$courseId::'.length);
    (json['topics'] as List<dynamic>).removeWhere((t) => (t as Map<String, dynamic>)['id'] == localId);
    await file.writeAsString(jsonEncode(json));
  }

  Future<void> deleteCourse(String courseId) async {
    final file = await _courseFile(courseId);
    if (file.existsSync()) await file.delete();
  }

  Future<List<Course>> loadCourses() async {
    final dir = await _coursesDir();
    final files = dir.listSync().whereType<File>().where((f) => f.path.endsWith('.json')).toList();
    final entries = <({DateTime createdAt, Course course})>[];
    for (final file in files) {
      try {
        final content = await file.readAsString();
        final json = jsonDecode(content) as Map<String, dynamic>;
        final id = p.basenameWithoutExtension(file.path);
        final createdAt = json['createdAt'] != null
            ? DateTime.parse(json['createdAt'] as String)
            : file.lastModifiedSync();
        // Persist backfilled ids right away so later edits can't shift legacy topics.
        if (ensureTopicIds(json['topics'] as List<dynamic>? ?? [])) {
          json['createdAt'] ??= createdAt.toIso8601String();
          await file.writeAsString(jsonEncode(json));
        }
        entries.add((createdAt: createdAt, course: Course.fromJson(json, id)));
      } catch (e) {
        // Skip malformed files
      }
    }
    entries.sort((a, b) => a.createdAt.compareTo(b.createdAt));
    return entries.map((e) => e.course).toList();
  }
}
