-- =============================================================
--  HSTS  -  Developer Testing Seed Script
--  Run against:  hsts_db
--
--  Populates: 3 courses, 6 teachers, 6 students, 2 administrators,
--             100 questions, 6 exams, 3 sat exams per student.
--
--  Logins  (all passwords: password123)
--    teacher1 .. teacher6   TEACHER    (2 per course)
--    student1 .. student6   STUDENT    (enrolled in all 3 courses)
--    admin1, admin2         PRINCIPAL  (approve exams, view reports)
--
--  Passwords are stored in plain text on purpose: UserDAO.authenticateUser
--  falls back to a direct comparison when the stored value is not a BCrypt
--  hash (does not start with $2). Any password changed through the app is
--  re-stored hashed.
-- =============================================================

USE hsts_db;

-- ---------------------------------------------------------------
--  RESET  (children first, parents last)
-- ---------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE bot_questions;
TRUNCATE TABLE bot_sources;
TRUNCATE TABLE course_bots;
TRUNCATE TABLE course_teachers;
TRUNCATE TABLE student_answers;
TRUNCATE TABLE test_submissions;
TRUNCATE TABLE test_questions;
TRUNCATE TABLE enrollments;
TRUNCATE TABLE tests_history;
TRUNCATE TABLE tests;
TRUNCATE TABLE questions_history;
TRUNCATE TABLE questions;
TRUNCATE TABLE teacher_statistics;
TRUNCATE TABLE user_settings;
TRUNCATE TABLE images;
TRUNCATE TABLE users;
TRUNCATE TABLE courses;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
--  1. COURSES  (3)
-- =============================================================
INSERT INTO courses (id, course_code, course_name, subject_code, subject_name) VALUES
(1, 'MA', 'Mathematics', 'MA', 'Mathematics'),
(2, 'PH', 'Physics', 'SC', 'Science'),
(3, 'CS', 'Computer Science', 'CS', 'Computer Science');

-- =============================================================
--  2. USERS
--     ids  1-6   teachers    (2 assigned to each course)
--     ids  7-12  students    (grade levels 9-12)
--     ids 13-14  administrators (PRINCIPAL role)
-- =============================================================
INSERT INTO users (id, username, password_hash, role, full_name, grade_level, national_id) VALUES
( 1, 'teacher1', 'password123', 'TEACHER',   'Teacher One  ', NULL, '300000001'),
( 2, 'teacher2', 'password123', 'TEACHER',   'Teacher Two  ', NULL, '300000002'),
( 3, 'teacher3', 'password123', 'TEACHER',   'Teacher Three', NULL, '300000003'),
( 4, 'teacher4', 'password123', 'TEACHER',   'Teacher Four ', NULL, '300000004'),
( 5, 'teacher5', 'password123', 'TEACHER',   'Teacher Five ', NULL, '300000005'),
( 6, 'teacher6', 'password123', 'TEACHER',   'Teacher Six  ', NULL, '300000006'),
( 7, 'student1', 'password123', 'STUDENT',   'Student One  ',  9,   '200000001'),
( 8, 'student2', 'password123', 'STUDENT',   'Student Two  ', 10,   '200000002'),
( 9, 'student3', 'password123', 'STUDENT',   'Student Three', 11,   '200000003'),
(10, 'student4', 'password123', 'STUDENT',   'Student Four ', 12,   '200000004'),
(11, 'student5', 'password123', 'STUDENT',   'Student Five ',  9,   '200000005'),
(12, 'student6', 'password123', 'STUDENT',   'Student Six  ', 10,   '200000006'),
(13, 'admin1',    'password123', 'PRINCIPAL', 'Admin One    ', NULL, '100000001'),
(14, 'admin2',    'password123', 'PRINCIPAL', 'Admin Two    ', NULL, '100000002');

-- national_id is what a student types at the exam entry gate alongside the
-- 4-digit exam code, so every student needs one for START_EXAM_BY_CODE to work.

-- =============================================================
--  3. COURSE_TEACHERS  (6 teachers split evenly, 2 per course)
--     Grants the right to edit that course's bot sources and to
--     generate questions/exams for it.
-- =============================================================
INSERT INTO course_teachers (course_id, teacher_id) VALUES
(1, 1),   -- Mathematics: teacher1
(1, 2),   -- Mathematics: teacher2
(2, 3),   -- Physics: teacher3
(2, 4),   -- Physics: teacher4
(3, 5),   -- Computer Science: teacher5
(3, 6);   -- Computer Science: teacher6

-- =============================================================
--  4. QUESTIONS  (100 total)
--     ids   1- 34  Mathematics      (course 1)
--     ids  35- 67  Physics          (course 2)
--     ids  68-100  Computer Science (course 3)
--     Authorship alternates between the two teachers of each course,
--     so teacher_statistics and the teacher activity report have data.
-- =============================================================

-- -- MATHEMATICS (MA001-MA034) --
INSERT INTO questions (id, question_code, course_id, question_text, option_a, option_b, option_c, option_d, correct_option, image_url, teacher_id, difficulty_level, topic) VALUES
(  1, 'MA001', 1, 'What is the value of x in 2x + 6 = 14?', '2', '4', '6', '8', 'B', NULL, 1, 'EASY', 'Algebra'),
(  2, 'MA002', 1, 'Simplify: 3(x + 4) - 2x', 'x + 12', '5x + 12', 'x + 4', '5x + 4', 'A', NULL, 2, 'EASY', 'Algebra'),
(  3, 'MA003', 1, 'What is the slope of the line y = -3x + 7?', '7', '3', '-3', '-7', 'C', NULL, 1, 'EASY', 'Algebra'),
(  4, 'MA004', 1, 'Factorise x^2 - 9', '(x-3)(x-3)', '(x+3)(x+3)', '(x-9)(x+1)', '(x-3)(x+3)', 'D', NULL, 2, 'MEDIUM', 'Algebra'),
(  5, 'MA005', 1, 'Solve x^2 - 5x + 6 = 0', 'x = 1 or 6', 'x = 2 or 3', 'x = -2 or -3', 'x = 5 or 6', 'B', NULL, 1, 'MEDIUM', 'Algebra'),
(  6, 'MA006', 1, 'If f(x) = 2x + 1, what is f(5)?', '10', '11', '12', '6', 'B', NULL, 2, 'EASY', 'Algebra'),
(  7, 'MA007', 1, 'What is the value of 5! (5 factorial)?', '25', '60', '120', '720', 'C', NULL, 1, 'EASY', 'Algebra'),
(  8, 'MA008', 1, 'Expand (x + 2)^2', 'x^2 + 4', 'x^2 + 2x + 4', 'x^2 + 4x + 4', 'x^2 + 4x + 2', 'C', NULL, 2, 'MEDIUM', 'Algebra'),
(  9, 'MA009', 1, 'Solve the inequality 3x - 5 > 7', 'x > 4', 'x > 2', 'x < 4', 'x > 12', 'A', NULL, 1, 'MEDIUM', 'Algebra'),
( 10, 'MA010', 1, 'What is the sum of the first 10 positive integers?', '45', '50', '55', '100', 'C', NULL, 2, 'MEDIUM', 'Algebra'),
( 11, 'MA011', 1, 'The sum of the interior angles of a triangle is:', '90 degrees', '180 degrees', '270 degrees', '360 degrees', 'B', NULL, 1, 'EASY', 'Geometry'),
( 12, 'MA012', 1, 'Area of a circle with radius 5 (use pi = 3.14):', '31.4', '78.5', '15.7', '157', 'B', NULL, 2, 'EASY', 'Geometry'),
( 13, 'MA013', 1, 'A right triangle has legs 3 and 4. Its hypotenuse is:', '5', '6', '7', '12', 'A', NULL, 1, 'EASY', 'Geometry'),
( 14, 'MA014', 1, 'The sum of the interior angles of a hexagon is:', '540 degrees', '720 degrees', '900 degrees', '1080 degrees', 'B', NULL, 2, 'HARD', 'Geometry'),
( 15, 'MA015', 1, 'Volume of a cube with edge length 4:', '16', '48', '64', '256', 'C', NULL, 1, 'EASY', 'Geometry'),
( 16, 'MA016', 1, 'How many faces does a rectangular prism have?', '4', '6', '8', '12', 'B', NULL, 2, 'EASY', 'Geometry'),
( 17, 'MA017', 1, 'Circumference of a circle with diameter 10 (pi = 3.14):', '15.7', '31.4', '62.8', '78.5', 'B', NULL, 1, 'MEDIUM', 'Geometry'),
( 18, 'MA018', 1, 'Two angles are complementary. One is 35 degrees. The other is:', '45', '55', '65', '145', 'B', NULL, 2, 'EASY', 'Geometry'),
( 19, 'MA019', 1, 'Area of a trapezoid with parallel sides 6 and 10 and height 4:', '32', '40', '64', '24', 'A', NULL, 1, 'MEDIUM', 'Geometry'),
( 20, 'MA020', 1, 'What is sin(30 degrees)?', '0', '0.5', '0.707', '1', 'B', NULL, 2, 'MEDIUM', 'Trigonometry'),
( 21, 'MA021', 1, 'What is cos(0 degrees)?', '0', '0.5', '1', '-1', 'C', NULL, 1, 'EASY', 'Trigonometry'),
( 22, 'MA022', 1, 'What is tan(45 degrees)?', '0', '0.5', '1', 'undefined', 'C', NULL, 2, 'MEDIUM', 'Trigonometry'),
( 23, 'MA023', 1, 'Which identity is always true?', 'sin^2 + cos^2 = 1', 'sin + cos = 1', 'tan = cos/sin', 'sin = cos', 'A', NULL, 1, 'MEDIUM', 'Trigonometry'),
( 24, 'MA024', 1, 'In a right triangle, sine equals:', 'adjacent/hypotenuse', 'opposite/hypotenuse', 'opposite/adjacent', 'hypotenuse/opposite', 'B', NULL, 2, 'MEDIUM', 'Trigonometry'),
( 25, 'MA025', 1, 'How many radians are in 180 degrees?', 'pi/2', 'pi', '2pi', 'pi/4', 'B', NULL, 1, 'HARD', 'Trigonometry'),
( 26, 'MA026', 1, 'The mean of 4, 8, 10, 14 is:', '8', '9', '10', '12', 'B', NULL, 2, 'EASY', 'Statistics'),
( 27, 'MA027', 1, 'The median of 3, 7, 9, 15, 21 is:', '7', '9', '12', '15', 'B', NULL, 1, 'EASY', 'Statistics'),
( 28, 'MA028', 1, 'The mode of 2, 3, 3, 5, 7, 3 is:', '2', '3', '5', '7', 'B', NULL, 2, 'EASY', 'Statistics'),
( 29, 'MA029', 1, 'The range of 12, 5, 20, 8 is:', '8', '12', '15', '20', 'C', NULL, 1, 'EASY', 'Statistics'),
( 30, 'MA030', 1, 'Probability of rolling a 4 on a fair six-sided die:', '1/2', '1/3', '1/6', '1/4', 'C', NULL, 2, 'EASY', 'Statistics'),
( 31, 'MA031', 1, 'Probability of drawing a red card from a standard 52-card deck:', '1/4', '1/3', '1/2', '2/3', 'C', NULL, 1, 'MEDIUM', 'Statistics'),
( 32, 'MA032', 1, 'The derivative of x^2 is:', 'x', '2x', 'x^3/3', '2', 'B', NULL, 2, 'HARD', 'Calculus'),
( 33, 'MA033', 1, 'The derivative of a constant is:', 'the constant', '1', '0', 'undefined', 'C', NULL, 1, 'MEDIUM', 'Calculus'),
( 34, 'MA034', 1, 'The integral of 2x dx is:', 'x^2 + C', '2 + C', 'x + C', '2x^2 + C', 'A', NULL, 2, 'HARD', 'Calculus');

-- -- PHYSICS (PH001-PH033) --
INSERT INTO questions (id, question_code, course_id, question_text, option_a, option_b, option_c, option_d, correct_option, image_url, teacher_id, difficulty_level, topic) VALUES
( 35, 'PH001', 2, 'Newton''s second law is expressed as:', 'F = mv', 'F = ma', 'F = m/a', 'F = a/m', 'B', NULL, 3, 'EASY', 'Mechanics'),
( 36, 'PH002', 2, 'The SI unit of force is the:', 'joule', 'watt', 'newton', 'pascal', 'C', NULL, 4, 'EASY', 'Mechanics'),
( 37, 'PH003', 2, 'An object at rest stays at rest unless acted on by a force. This is:', 'Newton''s first law', 'Newton''s second law', 'Newton''s third law', 'Hooke''s law', 'A', NULL, 3, 'EASY', 'Mechanics'),
( 38, 'PH004', 2, 'For every action there is an equal and opposite reaction. This is:', 'first law', 'second law', 'third law', 'law of gravitation', 'C', NULL, 4, 'EASY', 'Mechanics'),
( 39, 'PH005', 2, 'Acceleration due to gravity near Earth is about:', '5.0 m/s^2', '9.8 m/s^2', '12.5 m/s^2', '20 m/s^2', 'B', NULL, 3, 'EASY', 'Mechanics'),
( 40, 'PH006', 2, 'A 10 kg mass accelerates at 3 m/s^2. The net force is:', '3 N', '13 N', '30 N', '0.3 N', 'C', NULL, 4, 'MEDIUM', 'Mechanics'),
( 41, 'PH007', 2, 'Momentum is defined as:', 'mass times velocity', 'mass times acceleration', 'force times time', 'mass divided by velocity', 'A', NULL, 3, 'MEDIUM', 'Mechanics'),
( 42, 'PH008', 2, 'The SI unit of pressure is the:', 'newton', 'pascal', 'joule', 'watt', 'B', NULL, 4, 'EASY', 'Mechanics'),
( 43, 'PH009', 2, 'A car travels 150 km in 2 hours. Its average speed is:', '50 km/h', '75 km/h', '100 km/h', '300 km/h', 'B', NULL, 3, 'EASY', 'Mechanics'),
( 44, 'PH010', 2, 'Weight differs from mass because weight:', 'is measured in kg', 'depends on gravity', 'is always constant', 'is a scalar', 'B', NULL, 4, 'MEDIUM', 'Mechanics'),
( 45, 'PH011', 2, 'Kinetic energy is given by:', 'mv', 'mgh', 'half mv^2', 'ma', 'C', NULL, 3, 'MEDIUM', 'Energy'),
( 46, 'PH012', 2, 'The SI unit of energy is the:', 'newton', 'joule', 'watt', 'volt', 'B', NULL, 4, 'EASY', 'Energy'),
( 47, 'PH013', 2, 'Power is defined as:', 'work times time', 'work divided by time', 'force times distance', 'energy times force', 'B', NULL, 3, 'MEDIUM', 'Energy'),
( 48, 'PH014', 2, 'Gravitational potential energy near Earth is:', 'mgh', 'half mv^2', 'ma', 'mv', 'A', NULL, 4, 'MEDIUM', 'Energy'),
( 49, 'PH015', 2, 'Energy cannot be created or destroyed. This is the law of:', 'entropy', 'conservation of energy', 'thermodynamics zero', 'inertia', 'B', NULL, 3, 'EASY', 'Energy'),
( 50, 'PH016', 2, 'The SI unit of power is the:', 'joule', 'newton', 'watt', 'ampere', 'C', NULL, 4, 'EASY', 'Energy'),
( 51, 'PH017', 2, 'Work equals:', 'force times displacement', 'force divided by time', 'mass times velocity', 'power times force', 'A', NULL, 3, 'MEDIUM', 'Energy'),
( 52, 'PH018', 2, 'The speed of sound in air at room temperature is about:', '34 m/s', '343 m/s', '3430 m/s', '300000 km/s', 'B', NULL, 4, 'MEDIUM', 'Waves'),
( 53, 'PH019', 2, 'Wavelength is measured in:', 'hertz', 'seconds', 'metres', 'joules', 'C', NULL, 3, 'EASY', 'Waves'),
( 54, 'PH020', 2, 'Frequency is measured in:', 'metres', 'hertz', 'joules', 'watts', 'B', NULL, 4, 'EASY', 'Waves'),
( 55, 'PH021', 2, 'The wave equation relating speed, frequency and wavelength is:', 'v = f divided by lambda', 'v = f times lambda', 'v = lambda divided by f', 'v = f + lambda', 'B', NULL, 3, 'MEDIUM', 'Waves'),
( 56, 'PH022', 2, 'Light travels at approximately:', '300 m/s', '3000 km/s', '300000 km/s', '30 km/s', 'C', NULL, 4, 'EASY', 'Waves'),
( 57, 'PH023', 2, 'The bending of waves around an obstacle is called:', 'reflection', 'refraction', 'diffraction', 'dispersion', 'C', NULL, 3, 'HARD', 'Waves'),
( 58, 'PH024', 2, 'The bending of light as it passes between media is:', 'reflection', 'refraction', 'diffraction', 'absorption', 'B', NULL, 4, 'MEDIUM', 'Waves'),
( 59, 'PH025', 2, 'The SI unit of electric current is the:', 'volt', 'ohm', 'ampere', 'coulomb', 'C', NULL, 3, 'EASY', 'Electricity'),
( 60, 'PH026', 2, 'Ohm''s law states that:', 'V = IR', 'V = I/R', 'V = R/I', 'V = I + R', 'A', NULL, 4, 'EASY', 'Electricity'),
( 61, 'PH027', 2, 'The SI unit of resistance is the:', 'volt', 'ohm', 'watt', 'ampere', 'B', NULL, 3, 'EASY', 'Electricity'),
( 62, 'PH028', 2, 'In a series circuit, the current is:', 'different in each component', 'the same everywhere', 'zero', 'doubled at each resistor', 'B', NULL, 4, 'MEDIUM', 'Electricity'),
( 63, 'PH029', 2, 'Two 4-ohm resistors in series give a total resistance of:', '2 ohms', '4 ohms', '8 ohms', '16 ohms', 'C', NULL, 3, 'MEDIUM', 'Electricity'),
( 64, 'PH030', 2, 'Electric charge is measured in:', 'amperes', 'volts', 'coulombs', 'ohms', 'C', NULL, 4, 'MEDIUM', 'Electricity'),
( 65, 'PH031', 2, 'Heat always flows from:', 'cold to hot', 'hot to cold', 'high to low pressure', 'low to high density', 'B', NULL, 3, 'EASY', 'Thermodynamics'),
( 66, 'PH032', 2, 'Water freezes at what temperature in Celsius?', '-10', '0', '32', '100', 'B', NULL, 4, 'EASY', 'Thermodynamics'),
( 67, 'PH033', 2, 'Absolute zero is approximately:', '0 degrees C', '-100 degrees C', '-273 degrees C', '-373 degrees C', 'C', NULL, 3, 'HARD', 'Thermodynamics');

-- -- COMPUTER SCIENCE (CS001-CS033) --
INSERT INTO questions (id, question_code, course_id, question_text, option_a, option_b, option_c, option_d, correct_option, image_url, teacher_id, difficulty_level, topic) VALUES
( 68, 'CS001', 3, 'Which of these is NOT a primitive type in Java?', 'int', 'boolean', 'String', 'double', 'C', NULL, 5, 'EASY', 'Programming Basics'),
( 69, 'CS002', 3, 'What does a compiler do?', 'runs code line by line', 'translates source into machine code', 'stores data', 'manages memory only', 'B', NULL, 6, 'EASY', 'Programming Basics'),
( 70, 'CS003', 3, 'Which keyword declares a constant in Java?', 'const', 'static', 'final', 'immutable', 'C', NULL, 5, 'MEDIUM', 'Programming Basics'),
( 71, 'CS004', 3, 'Which loop always executes at least once?', 'for', 'while', 'do-while', 'foreach', 'C', NULL, 6, 'MEDIUM', 'Programming Basics'),
( 72, 'CS005', 3, 'What is the value of 7 / 2 in Java integer arithmetic?', '3.5', '3', '4', '0', 'B', NULL, 5, 'MEDIUM', 'Programming Basics'),
( 73, 'CS006', 3, 'Array indices in Java start at:', '-1', '0', '1', 'depends on the array', 'B', NULL, 6, 'EASY', 'Programming Basics'),
( 74, 'CS007', 3, 'Which symbol tests equality in Java?', '=', '==', 'equals', '===', 'B', NULL, 5, 'EASY', 'Programming Basics'),
( 75, 'CS008', 3, 'What does OOP stand for?', 'Open Order Protocol', 'Object Oriented Programming', 'Optimal Output Processing', 'Ordered Object Pattern', 'B', NULL, 6, 'EASY', 'Programming Basics'),
( 76, 'CS009', 3, 'Which access modifier is the most restrictive?', 'public', 'protected', 'default', 'private', 'D', NULL, 5, 'MEDIUM', 'Programming Basics'),
( 77, 'CS010', 3, 'A method that calls itself is:', 'iterative', 'recursive', 'static', 'abstract', 'B', NULL, 6, 'MEDIUM', 'Programming Basics'),
( 78, 'CS011', 3, 'A stack follows which order?', 'FIFO', 'LIFO', 'random', 'sorted', 'B', NULL, 5, 'EASY', 'Data Structures'),
( 79, 'CS012', 3, 'A queue follows which order?', 'FIFO', 'LIFO', 'random', 'sorted', 'A', NULL, 6, 'EASY', 'Data Structures'),
( 80, 'CS013', 3, 'Which structure stores key-value pairs?', 'array', 'stack', 'hash map', 'queue', 'C', NULL, 5, 'EASY', 'Data Structures'),
( 81, 'CS014', 3, 'Adding an element to a stack is called:', 'enqueue', 'push', 'insert', 'append', 'B', NULL, 6, 'EASY', 'Data Structures'),
( 82, 'CS015', 3, 'A node in a singly linked list contains data and:', 'two pointers', 'one pointer to the next node', 'an index', 'a hash', 'B', NULL, 5, 'MEDIUM', 'Data Structures'),
( 83, 'CS016', 3, 'Which structure is best for looking up a value by a unique key?', 'linked list', 'array', 'hash map', 'stack', 'C', NULL, 6, 'MEDIUM', 'Data Structures'),
( 84, 'CS017', 3, 'A binary tree node has at most how many children?', '1', '2', '3', 'unlimited', 'B', NULL, 5, 'MEDIUM', 'Data Structures'),
( 85, 'CS018', 3, 'Removing an element from a queue is called:', 'pop', 'dequeue', 'shift', 'delete', 'B', NULL, 6, 'MEDIUM', 'Data Structures'),
( 86, 'CS019', 3, 'Binary search requires the collection to be:', 'empty', 'sorted', 'unsorted', 'a linked list', 'B', NULL, 5, 'MEDIUM', 'Algorithms'),
( 87, 'CS020', 3, 'The time complexity of binary search is:', 'O(1)', 'O(log n)', 'O(n)', 'O(n^2)', 'B', NULL, 6, 'HARD', 'Algorithms'),
( 88, 'CS021', 3, 'The time complexity of linear search is:', 'O(1)', 'O(log n)', 'O(n)', 'O(n^2)', 'C', NULL, 5, 'MEDIUM', 'Algorithms'),
( 89, 'CS022', 3, 'Which sort repeatedly swaps adjacent out-of-order elements?', 'bubble sort', 'merge sort', 'quick sort', 'heap sort', 'A', NULL, 6, 'MEDIUM', 'Algorithms'),
( 90, 'CS023', 3, 'Average time complexity of merge sort is:', 'O(n)', 'O(n log n)', 'O(n^2)', 'O(log n)', 'B', NULL, 5, 'HARD', 'Algorithms'),
( 91, 'CS024', 3, 'Big-O notation describes:', 'exact runtime in seconds', 'an upper bound on growth', 'memory address size', 'the number of variables', 'B', NULL, 6, 'HARD', 'Algorithms'),
( 92, 'CS025', 3, 'Accessing an array element by index takes:', 'O(1)', 'O(log n)', 'O(n)', 'O(n^2)', 'A', NULL, 5, 'MEDIUM', 'Algorithms'),
( 93, 'CS026', 3, 'What does SQL stand for?', 'Structured Query Language', 'Simple Question Layer', 'System Query Logic', 'Sorted Quick Lookup', 'A', NULL, 6, 'EASY', 'Databases'),
( 94, 'CS027', 3, 'Which SQL keyword retrieves data?', 'GET', 'SELECT', 'FETCH', 'READ', 'B', NULL, 5, 'EASY', 'Databases'),
( 95, 'CS028', 3, 'A column that uniquely identifies each row is a:', 'foreign key', 'primary key', 'index', 'view', 'B', NULL, 6, 'EASY', 'Databases'),
( 96, 'CS029', 3, 'Which clause filters rows in a SQL query?', 'ORDER BY', 'WHERE', 'GROUP BY', 'HAVING', 'B', NULL, 5, 'EASY', 'Databases'),
( 97, 'CS030', 3, 'A foreign key enforces:', 'uniqueness', 'referential integrity', 'sort order', 'encryption', 'B', NULL, 6, 'MEDIUM', 'Databases'),
( 98, 'CS031', 3, 'Which SQL statement adds a new row?', 'UPDATE', 'INSERT', 'ALTER', 'CREATE', 'B', NULL, 5, 'EASY', 'Databases'),
( 99, 'CS032', 3, 'Which protocol underlies the web?', 'FTP', 'HTTP', 'SMTP', 'SSH', 'B', NULL, 6, 'EASY', 'Networking'),
(100, 'CS033', 3, 'What does IP stand for?', 'Internet Protocol', 'Internal Process', 'Indexed Packet', 'Instruction Pointer', 'A', NULL, 5, 'EASY', 'Networking');

-- =============================================================
--  5. TESTS  (6 exams - 2 per course, one from each teacher)
--     teacher_notes        = Exam.title
--     student_instructions = Exam.instructions
--
--     Exams 1-3 are APPROVED, active, and scheduled for TODAY (midnight
--     to 23:59:59 of the day the seed is run), so a student can enter them
--     with the 4-digit exam_code. These are the three every student has
--     already sat.
--     Exams 4-5 are PENDING, to populate the principal's approval queue.
--     Exam 6 is REJECTED, to show a rejection reason reaching its author.
-- =============================================================
INSERT INTO tests (id, test_code, teacher_id, course_id, duration_minutes,
                   student_instructions, teacher_notes, is_active,
                   approval_status, rejection_reason, open_at, close_at, exam_code) VALUES
(1, 'MAMID1', 1, 1, 60,
 'Answer all 10 questions. Calculators are not permitted.',
 'Mathematics Mid-Term', TRUE, 'APPROVED', NULL, TIMESTAMP(CURDATE()), TIMESTAMP(CURDATE(), '23:59:59'), '1101'),
(2, 'PHMID1', 3, 2, 60,
 'Answer all 10 questions. Show units in your working.',
 'Physics Mid-Term', TRUE, 'APPROVED', NULL, TIMESTAMP(CURDATE()), TIMESTAMP(CURDATE(), '23:59:59'), '2201'),
(3, 'CSMID1', 5, 3, 60,
 'Answer all 10 questions. 60 minutes allowed.',
 'Computer Science Mid-Term', TRUE, 'APPROVED', NULL, TIMESTAMP(CURDATE()), TIMESTAMP(CURDATE(), '23:59:59'), '3301'),
(4, 'MAFIN1', 2, 1, 90,
 'Final examination. Answer every question.',
 'Mathematics Final', FALSE, 'PENDING', NULL, NULL, NULL, NULL),
(5, 'PHFIN1', 4, 2, 90,
 'Final examination. Answer every question.',
 'Physics Final', FALSE, 'PENDING', NULL, NULL, NULL, NULL),
(6, 'CSFIN1', 6, 3, 45,
 'Short advanced quiz.',
 'Computer Science Advanced Quiz', FALSE, 'REJECTED', 'Too short for a final assessment, and question 3 duplicates the mid-term. Please revise and resubmit.', NULL, NULL, NULL);

-- The open window is derived from CURDATE(), so exams 1-3 are always
-- scheduled for the day the seed is run and are enterable all of that day.
-- Re-run the seed to move them to a later day.

-- =============================================================
--  6. TEST_QUESTIONS  (10 questions per exam, 10 points each = 100)
--     The 100-point rule is enforced in ClientController.requestCreateExam,
--     so seeded exams must satisfy it too.
-- =============================================================
-- Exam 1
INSERT INTO test_questions (test_id, question_id, points_worth) VALUES
(1,   1, 10.00),
(1,   2, 10.00),
(1,   3, 10.00),
(1,   4, 10.00),
(1,   5, 10.00),
(1,   6, 10.00),
(1,   7, 10.00),
(1,   8, 10.00),
(1,   9, 10.00),
(1,  10, 10.00);

-- Exam 2
INSERT INTO test_questions (test_id, question_id, points_worth) VALUES
(2,  35, 10.00),
(2,  36, 10.00),
(2,  37, 10.00),
(2,  38, 10.00),
(2,  39, 10.00),
(2,  40, 10.00),
(2,  41, 10.00),
(2,  42, 10.00),
(2,  43, 10.00),
(2,  44, 10.00);

-- Exam 3
INSERT INTO test_questions (test_id, question_id, points_worth) VALUES
(3,  68, 10.00),
(3,  69, 10.00),
(3,  70, 10.00),
(3,  71, 10.00),
(3,  72, 10.00),
(3,  73, 10.00),
(3,  74, 10.00),
(3,  75, 10.00),
(3,  76, 10.00),
(3,  77, 10.00);

-- Exam 4
INSERT INTO test_questions (test_id, question_id, points_worth) VALUES
(4,  11, 10.00),
(4,  12, 10.00),
(4,  13, 10.00),
(4,  14, 10.00),
(4,  15, 10.00),
(4,  16, 10.00),
(4,  17, 10.00),
(4,  18, 10.00),
(4,  19, 10.00),
(4,  20, 10.00);

-- Exam 5
INSERT INTO test_questions (test_id, question_id, points_worth) VALUES
(5,  45, 10.00),
(5,  46, 10.00),
(5,  47, 10.00),
(5,  48, 10.00),
(5,  49, 10.00),
(5,  50, 10.00),
(5,  51, 10.00),
(5,  52, 10.00),
(5,  53, 10.00),
(5,  54, 10.00);

-- Exam 6
INSERT INTO test_questions (test_id, question_id, points_worth) VALUES
(6,  78, 10.00),
(6,  79, 10.00),
(6,  80, 10.00),
(6,  81, 10.00),
(6,  82, 10.00),
(6,  83, 10.00),
(6,  84, 10.00),
(6,  85, 10.00),
(6,  86, 10.00),
(6,  87, 10.00);

-- =============================================================
--  7. ENROLLMENTS  (every student in all 3 courses)
-- =============================================================
INSERT INTO enrollments (student_id, course_id) VALUES
(7, 1),
(7, 2),
(7, 3),
(8, 1),
(8, 2),
(8, 3),
(9, 1),
(9, 2),
(9, 3),
(10, 1),
(10, 2),
(10, 3),
(11, 1),
(11, 2),
(11, 3),
(12, 1),
(12, 2),
(12, 3);

-- =============================================================
--  8. TEST_SUBMISSIONS  (3 per student x 6 students = 18)
--     Every student has sat exams 1, 2 and 3 -- TODAY, inside the window
--     the tests above are scheduled for: exam 1 sat at 08:00, exam 2 at
--     10:00, exam 3 at 12:00, each student finishing at their own time
--     within the 60-minute allowance. started_at is now genuinely earlier
--     than submitted_at rather than a duplicate of it.
--     final_score is COMPUTED from the student_answers below, so the
--     marked script a student reviews always matches the grade shown.
--
--     Submission 5 is AWAITING_APPROVAL so the teacher's grade queue is
--     not empty. Note that getResultsForStudent filters on status='GRADED',
--     so that student correctly sees only 2 of her 3 results until it is released.
--     Submission 1 has been overridden by a teacher: final_score differs
--     from original_score and a justification is recorded.
-- =============================================================
INSERT INTO test_submissions (id, test_id, student_id, status, final_score,
                              original_score, grade_override_reason, approved_by,
                              started_at, submitted_at) VALUES
( 1, 1,  7, 'GRADED',             60.00,  50.00,
     'Question 4 was ambiguously worded; 10 marks awarded to all students who attempted it.', 1,
     TIMESTAMP(CURDATE(), '08:00:00'), TIMESTAMP(CURDATE(), '08:47:00')),
( 2, 2,  7, 'GRADED',            100.00, 100.00, NULL, 3,
     TIMESTAMP(CURDATE(), '10:00:00'), TIMESTAMP(CURDATE(), '10:55:00')),
( 3, 3,  7, 'GRADED',            100.00, 100.00, NULL, 5,
     TIMESTAMP(CURDATE(), '12:00:00'), TIMESTAMP(CURDATE(), '12:44:00')),
( 4, 1,  8, 'GRADED',             90.00,  90.00, NULL, 1,
     TIMESTAMP(CURDATE(), '08:00:00'), TIMESTAMP(CURDATE(), '08:52:00')),
( 5, 2,  8, 'AWAITING_APPROVAL',  70.00,  70.00, NULL, NULL,
     TIMESTAMP(CURDATE(), '10:00:00'), TIMESTAMP(CURDATE(), '10:43:00')),
( 6, 3,  8, 'GRADED',             90.00,  90.00, NULL, 5,
     TIMESTAMP(CURDATE(), '12:00:00'), TIMESTAMP(CURDATE(), '12:50:00')),
( 7, 1,  9, 'GRADED',             50.00,  50.00, NULL, 1,
     TIMESTAMP(CURDATE(), '08:00:00'), TIMESTAMP(CURDATE(), '08:41:00')),
( 8, 2,  9, 'GRADED',             80.00,  80.00, NULL, 3,
     TIMESTAMP(CURDATE(), '10:00:00'), TIMESTAMP(CURDATE(), '10:49:00')),
( 9, 3,  9, 'GRADED',             60.00,  60.00, NULL, 5,
     TIMESTAMP(CURDATE(), '12:00:00'), TIMESTAMP(CURDATE(), '12:41:00')),
(10, 1, 10, 'GRADED',            100.00, 100.00, NULL, 1,
     TIMESTAMP(CURDATE(), '08:00:00'), TIMESTAMP(CURDATE(), '08:58:00')),
(11, 2, 10, 'GRADED',             60.00,  60.00, NULL, 3,
     TIMESTAMP(CURDATE(), '10:00:00'), TIMESTAMP(CURDATE(), '10:58:00')),
(12, 3, 10, 'GRADED',             50.00,  50.00, NULL, 5,
     TIMESTAMP(CURDATE(), '12:00:00'), TIMESTAMP(CURDATE(), '12:56:00')),
(13, 1, 11, 'GRADED',             50.00,  50.00, NULL, 1,
     TIMESTAMP(CURDATE(), '08:00:00'), TIMESTAMP(CURDATE(), '08:45:00')),
(14, 2, 11, 'GRADED',             50.00,  50.00, NULL, 3,
     TIMESTAMP(CURDATE(), '10:00:00'), TIMESTAMP(CURDATE(), '10:46:00')),
(15, 3, 11, 'GRADED',             50.00,  50.00, NULL, 5,
     TIMESTAMP(CURDATE(), '12:00:00'), TIMESTAMP(CURDATE(), '12:47:00')),
(16, 1, 12, 'GRADED',             60.00,  60.00, NULL, 1,
     TIMESTAMP(CURDATE(), '08:00:00'), TIMESTAMP(CURDATE(), '08:50:00')),
(17, 2, 12, 'GRADED',            100.00, 100.00, NULL, 3,
     TIMESTAMP(CURDATE(), '10:00:00'), TIMESTAMP(CURDATE(), '10:52:00')),
(18, 3, 12, 'GRADED',             70.00,  70.00, NULL, 5,
     TIMESTAMP(CURDATE(), '12:00:00'), TIMESTAMP(CURDATE(), '12:53:00'));

-- =============================================================
--  9. STUDENT_ANSWERS  (18 submissions x 10 questions = 180)
--     Required by GET_SUBMISSION_REVIEW: without these the marked-script
--     review screen has nothing to show.
-- =============================================================
INSERT INTO student_answers (submission_id, question_id, student_answer) VALUES
( 1,   1, 'B'),
( 1,   2, 'C'),
( 1,   3, 'A'),
( 1,   4, 'D'),
( 1,   5, 'D'),
( 1,   6, 'B'),
( 1,   7, 'C'),
( 1,   8, 'D'),
( 1,   9, 'A'),
( 1,  10, 'A'),
( 2,  35, 'B'),
( 2,  36, 'C'),
( 2,  37, 'A'),
( 2,  38, 'C'),
( 2,  39, 'B'),
( 2,  40, 'C'),
( 2,  41, 'A'),
( 2,  42, 'B'),
( 2,  43, 'B'),
( 2,  44, 'B'),
( 3,  68, 'C'),
( 3,  69, 'B'),
( 3,  70, 'C'),
( 3,  71, 'C'),
( 3,  72, 'B'),
( 3,  73, 'B'),
( 3,  74, 'B'),
( 3,  75, 'B'),
( 3,  76, 'D'),
( 3,  77, 'B'),
( 4,   1, 'B'),
( 4,   2, 'A'),
( 4,   3, 'C'),
( 4,   4, 'D'),
( 4,   5, 'D'),
( 4,   6, 'B'),
( 4,   7, 'C'),
( 4,   8, 'C'),
( 4,   9, 'A'),
( 4,  10, 'C'),
( 5,  35, 'B'),
( 5,  36, 'B'),
( 5,  37, 'A'),
( 5,  38, 'D'),
( 5,  39, 'B'),
( 5,  40, 'B'),
( 5,  41, 'A'),
( 5,  42, 'B'),
( 5,  43, 'B'),
( 5,  44, 'B'),
( 6,  68, 'C'),
( 6,  69, 'B'),
( 6,  70, 'C'),
( 6,  71, 'C'),
( 6,  72, 'B'),
( 6,  73, 'A'),
( 6,  74, 'B'),
( 6,  75, 'B'),
( 6,  76, 'D'),
( 6,  77, 'B'),
( 7,   1, 'C'),
( 7,   2, 'B'),
( 7,   3, 'C'),
( 7,   4, 'C'),
( 7,   5, 'B'),
( 7,   6, 'B'),
( 7,   7, 'A'),
( 7,   8, 'C'),
( 7,   9, 'B'),
( 7,  10, 'C'),
( 8,  35, 'B'),
( 8,  36, 'C'),
( 8,  37, 'A'),
( 8,  38, 'C'),
( 8,  39, 'A'),
( 8,  40, 'C'),
( 8,  41, 'A'),
( 8,  42, 'B'),
( 8,  43, 'D'),
( 8,  44, 'B'),
( 9,  68, 'C'),
( 9,  69, 'A'),
( 9,  70, 'A'),
( 9,  71, 'C'),
( 9,  72, 'B'),
( 9,  73, 'B'),
( 9,  74, 'A'),
( 9,  75, 'B'),
( 9,  76, 'A'),
( 9,  77, 'B'),
(10,   1, 'B'),
(10,   2, 'A'),
(10,   3, 'C'),
(10,   4, 'D'),
(10,   5, 'B'),
(10,   6, 'B'),
(10,   7, 'C'),
(10,   8, 'C'),
(10,   9, 'A'),
(10,  10, 'C'),
(11,  35, 'A'),
(11,  36, 'A'),
(11,  37, 'A'),
(11,  38, 'C'),
(11,  39, 'C'),
(11,  40, 'C'),
(11,  41, 'A'),
(11,  42, 'B'),
(11,  43, 'B'),
(11,  44, 'A'),
(12,  68, 'C'),
(12,  69, 'B'),
(12,  70, 'D'),
(12,  71, 'A'),
(12,  72, 'D'),
(12,  73, 'A'),
(12,  74, 'B'),
(12,  75, 'B'),
(12,  76, 'D'),
(12,  77, 'A'),
(13,   1, 'B'),
(13,   2, 'D'),
(13,   3, 'C'),
(13,   4, 'D'),
(13,   5, 'A'),
(13,   6, 'D'),
(13,   7, 'A'),
(13,   8, 'C'),
(13,   9, 'A'),
(13,  10, 'D'),
(14,  35, 'B'),
(14,  36, 'C'),
(14,  37, 'B'),
(14,  38, 'A'),
(14,  39, 'C'),
(14,  40, 'D'),
(14,  41, 'C'),
(14,  42, 'B'),
(14,  43, 'B'),
(14,  44, 'B'),
(15,  68, 'D'),
(15,  69, 'B'),
(15,  70, 'A'),
(15,  71, 'C'),
(15,  72, 'B'),
(15,  73, 'B'),
(15,  74, 'D'),
(15,  75, 'D'),
(15,  76, 'D'),
(15,  77, 'C'),
(16,   1, 'B'),
(16,   2, 'B'),
(16,   3, 'D'),
(16,   4, 'D'),
(16,   5, 'B'),
(16,   6, 'B'),
(16,   7, 'B'),
(16,   8, 'A'),
(16,   9, 'A'),
(16,  10, 'C'),
(17,  35, 'B'),
(17,  36, 'C'),
(17,  37, 'A'),
(17,  38, 'C'),
(17,  39, 'B'),
(17,  40, 'C'),
(17,  41, 'A'),
(17,  42, 'B'),
(17,  43, 'B'),
(17,  44, 'B'),
(18,  68, 'D'),
(18,  69, 'B'),
(18,  70, 'B'),
(18,  71, 'C'),
(18,  72, 'B'),
(18,  73, 'B'),
(18,  74, 'B'),
(18,  75, 'B'),
(18,  76, 'C'),
(18,  77, 'B');

-- =============================================================
-- 10. TEACHER_STATISTICS  (counts match the rows actually seeded)
--     Backs the principal's teacher-activity report.
-- =============================================================
INSERT INTO teacher_statistics (teacher_id, tests_count, questions_count) VALUES
(1, 1, 17),
(2, 1, 17),
(3, 1, 17),
(4, 1, 16),
(5, 1, 17),
(6, 1, 16);

-- =============================================================
-- 11. COURSE_BOTS + BOT_SOURCES  (one learning bot per course)
--     Sources are the ONLY material the bot may answer from, so these
--     double as the grounding context sent to the answering engine.
-- =============================================================
INSERT INTO course_bots (id, course_id, bot_name, is_available, created_by) VALUES
(1, 1, 'Mathematics Study Bot',      TRUE, 1),
(2, 2, 'Physics Study Bot',          TRUE, 3),
(3, 3, 'Computer Science Study Bot', TRUE, 5);

INSERT INTO bot_sources (bot_id, title, content, updated_by) VALUES
(1, 'Algebra basics',
 'To solve a linear equation, isolate the variable by performing the same operation on both sides.

The slope-intercept form of a line is y = mx + b, where m is the slope and b is the y-intercept.

A quadratic equation has the form ax^2 + bx + c = 0 and can often be solved by factorising.',
 1),
(1, 'Geometry formulas',
 'The area of a circle is pi times the radius squared. Its circumference is pi times the diameter.

The interior angles of a triangle always sum to 180 degrees; for a polygon with n sides the sum is (n-2) times 180.

Pythagoras'' theorem: in a right triangle, the square of the hypotenuse equals the sum of the squares of the other two sides.',
 2),
(2, 'Newton''s laws',
 'First law: an object stays at rest or in uniform motion unless acted on by a net external force.

Second law: the net force equals mass times acceleration, F = ma. Force is measured in newtons.

Third law: for every action there is an equal and opposite reaction.',
 3),
(2, 'Energy and power',
 'Kinetic energy is one half times mass times velocity squared. Gravitational potential energy near Earth is mgh.

Work is force times displacement and is measured in joules. Power is work divided by time, measured in watts.

Energy is never created or destroyed, only transferred or converted.',
 4),
(3, 'Data structures',
 'A stack is last-in, first-out: the last item pushed is the first popped.

A queue is first-in, first-out: items leave in the order they arrived.

A hash map stores key-value pairs and gives average constant-time lookup by key.',
 5),
(3, 'Algorithm complexity',
 'Big-O notation describes an upper bound on how an algorithm''s cost grows with input size.

Binary search runs in O(log n) but requires a sorted collection. Linear search is O(n) and works on anything.

Merge sort runs in O(n log n) in the average and worst case.',
 6);

-- A little history so the student's own view and the teacher's anonymised
-- view both have rows. GET_BOT_HISTORY_ANONYMOUS must never return student_id.
INSERT INTO bot_questions (bot_id, student_id, question_text, answer_text, engine_used) VALUES
(1,  7, 'How do I find the slope of a line?',
 'In slope-intercept form y = mx + b, the slope is m, the coefficient of x.', 'CLAUDE'),
(1,  8, 'What is the area of a circle?',
 'The area of a circle is pi times the radius squared.', 'CLAUDE'),
(2,  7, 'What is Newton''s second law?',
 'The net force on an object equals its mass times its acceleration, F = ma.', 'CLAUDE'),
(2,  9, 'What units is energy measured in?',
 'Energy and work are both measured in joules.', 'LOCAL'),
(3, 10, 'What is the difference between a stack and a queue?',
 'A stack is last-in, first-out. A queue is first-in, first-out.', 'CLAUDE'),
(3, 11, 'Why does binary search need a sorted list?',
 'Binary search discards half the range each step, which only works if the order tells it which half to keep.', 'CLAUDE');

-- =============================================================
--  VERIFICATION  -  expected counts are in the comments
-- =============================================================
SELECT 'courses'          AS tbl, COUNT(*) AS row_count, 3   AS expected FROM courses
UNION ALL SELECT 'users',            COUNT(*),  14 FROM users
UNION ALL SELECT 'course_teachers',  COUNT(*),   6 FROM course_teachers
UNION ALL SELECT 'questions',        COUNT(*), 100 FROM questions
UNION ALL SELECT 'tests',            COUNT(*),   6 FROM tests
UNION ALL SELECT 'test_questions',   COUNT(*),  60 FROM test_questions
UNION ALL SELECT 'enrollments',      COUNT(*),  18 FROM enrollments
UNION ALL SELECT 'test_submissions', COUNT(*),  18 FROM test_submissions
UNION ALL SELECT 'student_answers',  COUNT(*), 180 FROM student_answers
UNION ALL SELECT 'course_bots',      COUNT(*),   3 FROM course_bots
UNION ALL SELECT 'bot_sources',      COUNT(*),   6 FROM bot_sources
UNION ALL SELECT 'bot_questions',    COUNT(*),   6 FROM bot_questions;

-- Every student should show exactly 3 sat exams:
SELECT u.username, COUNT(*) AS exams_sat
FROM test_submissions s JOIN users u ON u.id = s.student_id
GROUP BY u.username ORDER BY u.username;

-- Grades must agree with the marked scripts (expect 0 rows):
SELECT s.id, s.original_score, SUM(CASE WHEN sa.student_answer = qu.correct_option
       THEN tq.points_worth ELSE 0 END) AS recomputed
FROM test_submissions s
JOIN student_answers sa ON sa.submission_id = s.id
JOIN questions qu       ON qu.id = sa.question_id
JOIN test_questions tq  ON tq.test_id = s.test_id AND tq.question_id = sa.question_id
GROUP BY s.id, s.original_score
HAVING recomputed <> s.original_score;
