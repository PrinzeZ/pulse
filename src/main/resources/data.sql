-- Clear existing data first (in FK-safe order) so every app restart
-- gives a clean, predictable demo state instead of piling up duplicates
TRUNCATE TABLE alerts, stock_entries, users, medicines, hospitals, districts, states RESTART IDENTITY CASCADE;

-- 1. Hierarchy + hospitals — Kerala, 2 districts, 3 hospitals
INSERT INTO states (name) VALUES ('Kerala');

INSERT INTO districts (state_id, name)
SELECT s.state_id, d.name
FROM (VALUES ('Ernakulam'), ('Kozhikode')) AS d(name)
CROSS JOIN states s
WHERE s.name = 'Kerala';

INSERT INTO hospitals (name, district, district_id)
SELECT v.name, v.district, d.district_id
FROM (VALUES
    ('Kozhikode General Hospital', 'Kozhikode'),
    ('Kozhikode District Hospital', 'Kozhikode'),
    ('Ernakulam General Hospital', 'Ernakulam')
) AS v(name, district)
JOIN districts d ON LOWER(d.name) = LOWER(v.district)
JOIN states s ON s.state_id = d.state_id AND s.name = 'Kerala';

-- 2. Medicines — ~10 across categories, varied thresholds
INSERT INTO medicines (name, category, threshold) VALUES
('Paracetamol',  'Analgesic',          50),
('Insulin',      'Hormone',            20),
('Amoxicillin',  'Antibiotic',         30),
('Metformin',    'Antidiabetic',       40),
('Amlodipine',   'Antihypertensive',   25),
('Omeprazole',   'Antacid',            30),
('Cetirizine',   'Antihistamine',      20),
('Azithromycin', 'Antibiotic',         15),
('Salbutamol',   'Bronchodilator',     10),
('ORS Sachets',  'Rehydration',        100);

-- 3. Users — 2 staff + 1 admin (real BCrypt hashes)
INSERT INTO users (name, username, password, role, state_id, district_id, hospital_id) VALUES
('Staff One (Kozhikode)', 'staff_kozhikode', '$2a$12$TuD0H2ddT7/pX2FN4/cChumeE3PT7iCRaMwcKLcvU/gCAoBUtIFR.', 'STAFF', 1, 2, 1),
('Staff Two (Ernakulam)', 'staff_ernakulam', '$2a$12$TuD0H2ddT7/pX2FN4/cChumeE3PT7iCRaMwcKLcvU/gCAoBUtIFR.', 'STAFF', 1, 1, 3),
('Admin User',            'admin_main',      '$2a$12$iM3XCqh7eEdzQ.tcmASEXuAsMeKCUv8w.rLnZCm1PxNmofMXo3QBe', 'ADMIN', NULL, NULL, NULL);

-- 4. Stock entries — GREEN / YELLOW / RED demo cases across all 3 hospitals
INSERT INTO stock_entries (hospital_id, medicine_id, quantity, last_updated) VALUES
(1, 1, 200, CURRENT_DATE),
(1, 2, 5,   CURRENT_DATE),
(1, 3, 0,   CURRENT_DATE),
(1, 4, 80,  CURRENT_DATE),
(1, 5, 10,  CURRENT_DATE),
(2, 6, 60,  CURRENT_DATE),
(2, 7, 0,   CURRENT_DATE),
(2, 8, 5,   CURRENT_DATE),
(2, 9, 40,  CURRENT_DATE),
(2, 10, 150, CURRENT_DATE),
(3, 1, 10,  CURRENT_DATE),
(3, 2, 30,  CURRENT_DATE),
(3, 3, 0,   CURRENT_DATE),
(3, 4, 15,  CURRENT_DATE),
(3, 5, 50,  CURRENT_DATE);