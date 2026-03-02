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
    final json = jsonEncode({'name': name, 'topics': []});
    await candidate.writeAsString(json);
  }

  Future<void> renameCourse(String courseId, String newName) async {
    final dir = await _coursesDir();
    final file = File(p.join(dir.path, '$courseId.json'));
    final content = await file.readAsString();
    final json = (jsonDecode(content) as Map<String, dynamic>)..['name'] = newName;
    await file.writeAsString(jsonEncode(json));
  }

  Future<void> deleteCourse(String courseId) async {
    final dir = await _coursesDir();
    final file = File(p.join(dir.path, '$courseId.json'));
    if (file.existsSync()) await file.delete();
  }

  Future<List<Course>> loadCourses() async {
    final dir = await _coursesDir();
    final files = dir.listSync().whereType<File>().where((f) => f.path.endsWith('.json')).toList()
      ..sort((a, b) => a.lastModifiedSync().compareTo(b.lastModifiedSync()));
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
