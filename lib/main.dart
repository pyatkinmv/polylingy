import 'package:flutter/material.dart';
import 'package:polylingy/app.dart';
import 'package:polylingy/data/database.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final database = AppDatabase();
  runApp(PolylingyApp(database: database));
}
