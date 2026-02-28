import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart' show rootBundle;
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
import 'package:polylingy/models/course.dart';

class TopicRepository {
  Future<Directory> _coursesDir() async {
    final docs = await getApplicationDocumentsDirectory();
    final dir = Directory(p.join(docs.path, 'polylingy', 'courses'));
    await dir.create(recursive: true);
    return dir;
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

  Future<List<Course>> loadCourses() async {
    final dir = await _coursesDir();
    final files = dir.listSync().whereType<File>().where((f) => f.path.endsWith('.json'));
    final courses = <Course>[];
    for (final file in files) {
      try {
        final content = await file.readAsString();
        final json = jsonDecode(content) as Map<String, dynamic>;
        final id = p.basenameWithoutExtension(file.path);
        courses.add(Course.fromJson(json, id));
      } catch (e) {
        // Skip malformed files
      }
    }
    return courses;
  }
}
