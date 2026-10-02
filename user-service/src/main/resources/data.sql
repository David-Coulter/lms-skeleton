-- Seed data for user-service (dev profile only).
-- Goes in: user-service/src/main/resources/data.sql
--
-- Matches the slide-deck profile schema: grade_level replaces grade/grade_number,
-- and academic_year is gone (user-service reads it from schedule /terms at
-- request time instead of storing it).
--
-- grade_level values are the ids from enrollment-service's grade_level table
-- ("junior", "sophomore"), not display names. Staff rows have none.
--
-- No explicit ids: the id column is IDENTITY, so letting Postgres assign them
-- keeps the sequence in step. profile_class rows look their parent up by
-- username instead of hardcoding a foreign key.

INSERT INTO profile (username, full_name, title, first_name, last_name, gender, role,
                     email, phone, location, bio, date_of_birth, school,
                     grade_level, address,
                     guardian_name, guardian_relation, guardian_email, guardian_phone,
                     pref_timezone, pref_notifications)
VALUES
  ('student1', 'Sam Student', NULL, 'Sam', 'Student', 'male', 'student',
   'student1@learninghub.local', '(408) 555-0123', 'Sunnyvale, CA',
   'I enjoy math and computer science.', DATE '2010-03-15',
   'Fremont High School', 'junior', 'Sunnyvale, CA',
   'Sarah Smith', 'Parent/Guardian', 'sarah.smith@gmail.com', '(408) 555-0124',
   'Pacific Time (PT)', 'Email & Push'),

  ('gadams', 'Grace Adams', NULL, 'Grace', 'Adams', 'female', 'student',
   'gadams@learninghub.local', '(408) 555-0155', 'Cupertino, CA',
   'Robotics club president.', DATE '2010-07-02',
   'Fremont High School', 'junior', 'Cupertino, CA',
   'Marcus Adams', 'Parent/Guardian', 'marcus.adams@gmail.com', '(408) 555-0156',
   'Pacific Time (PT)', 'Email only'),

  ('aclark', 'Aiden Clark', NULL, 'Aiden', 'Clark', 'male', 'student',
   'aclark@learninghub.local', '(408) 555-0177', 'Sunnyvale, CA',
   NULL, DATE '2011-01-20',
   'Fremont High School', 'sophomore', 'Sunnyvale, CA',
   'Dana Clark', 'Parent/Guardian', 'dana.clark@gmail.com', '(408) 555-0178',
   'Pacific Time (PT)', 'Email & Push'),

  ('janderson', 'Dr John Anderson', 'Dr', 'John', 'Anderson', 'male', 'teacher',
   'janderson@learninghub.local', '(408) 555-0201', 'Palo Alto, CA',
   'Teaches AP Pre Calculus and AP Physics.', NULL,
   'Fremont High School', NULL, NULL,
   NULL, NULL, NULL, NULL,
   'Pacific Time (PT)', 'Email only'),

  ('ppatel', 'Dr Priya Patel', 'Dr', 'Priya', 'Patel', 'female', 'teacher',
   'ppatel@learninghub.local', '(408) 555-0202', 'Mountain View, CA',
   'AP Chemistry.', NULL,
   'Fremont High School', NULL, NULL,
   NULL, NULL, NULL, NULL,
   'Pacific Time (PT)', 'Email & Push'),

  ('admin1', 'Alice Admin', NULL, 'Alice', 'Admin', 'female', 'admin',
   'admin1@learninghub.local', '(408) 555-0100', 'San Jose, CA',
   NULL, NULL, NULL, NULL, NULL,
   NULL, NULL, NULL, NULL,
   'Pacific Time (PT)', 'Email & Push')
ON CONFLICT (username) DO NOTHING;

-- Classes, attached to their owner by username.
-- The whole set is wrapped in a subquery so the NOT EXISTS guard applies to
-- every row, not just the last branch of the UNION. A re-run on an existing
-- volume is then a no-op instead of a duplicate-key failure.
INSERT INTO profile_class (profile_id, name, color)
SELECT seed.id, seed.name, seed.color
FROM (
    SELECT id, 'AP Pre Calculus' AS name, 'blue'   AS color FROM profile WHERE username = 'student1'
    UNION ALL
    SELECT id, 'AP Physics 2',             'purple'         FROM profile WHERE username = 'student1'
    UNION ALL
    SELECT id, 'AP Chemistry',             'red'            FROM profile WHERE username = 'student1'
    UNION ALL
    SELECT id, 'AP Pre Calculus',          'blue'           FROM profile WHERE username = 'gadams'
    UNION ALL
    SELECT id, 'AP Chemistry',             'red'            FROM profile WHERE username = 'gadams'
    UNION ALL
    SELECT id, 'Geometry',                 'green'          FROM profile WHERE username = 'aclark'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM profile_class);
