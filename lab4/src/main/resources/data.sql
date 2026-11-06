-- Тестовые записи в КАЖДОЙ таблице (добавляются один раз).
INSERT INTO users (name, completed_exams) VALUES ('Иван', 1), ('Мария', 0);
INSERT INTO exams (name, passing_score, time_limit_minutes, status)
VALUES ('Основы Java', 15, 60, 'DRAFT'),
       ('Основы SQL', 10, 30, 'PUBLISHED');
INSERT INTO exam_tasks (exam_id, question, correct_answer, max_score)
VALUES (1, 'Что такое JVM?', 'виртуальная машина', 10),
       (1, 'Что такое int?', 'целое число', 10),
       (2, 'Команда для чтения данных?', 'SELECT', 10),
       (2, 'Команда для удаления строк?', 'DELETE', 10);
INSERT INTO exam_attempts (exam_id, user_id, status, total_score, finished_at)
VALUES (2, 1, 'COMPLETED', 10, CURRENT_TIMESTAMP),
       (2, 2, 'IN_PROGRESS', 0, NULL);
INSERT INTO task_solutions (attempt_id, task_id, answer, points)
VALUES (1, 3, 'SELECT', 10),
       (1, 4, 'DROP', 0);
