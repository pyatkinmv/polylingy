import 'package:flutter/material.dart';
import 'package:polylingy/data/database.dart';
import 'package:polylingy/data/progress_repository.dart';
import 'package:polylingy/data/topic_repository.dart';
import 'package:polylingy/screens/home_screen.dart';

class PolylingyApp extends StatelessWidget {
  final AppDatabase database;

  const PolylingyApp({super.key, required this.database});

  @override
  Widget build(BuildContext context) {
    final progressRepo = ProgressRepository(database);
    final topicRepo = TopicRepository();

    return MaterialApp(
      title: 'Polylingy',
      theme: ThemeData(
        colorSchemeSeed: Colors.indigo,
        useMaterial3: true,
      ),
      home: HomeScreen(
        topicRepo: topicRepo,
        progressRepo: progressRepo,
      ),
      debugShowCheckedModeBanner: false,
    );
  }
}
