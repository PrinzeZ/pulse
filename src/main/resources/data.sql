-- Clear existing data first (in FK-safe order) so every app restart
-- gives a clean, predictable demo state instead of piling up duplicates
TRUNCATE TABLE alerts, stock_entries, users, medicines, hospitals RESTART IDENTITY CASCADE;

-- 1. Hospitals — 3 hospitals, 2 districts
INSERT INTO hospitals (name, district) VALUES
('Kozhikode General Hospital', 'Kozhikode'),
('Kozhikode District Hospital', 'Kozhikode'),
('Ernakulam General Hospital', 'Ernakulam');

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
INSERT INTO users (name, username, password, role, hospital_id) VALUES
('Staff One (Kozhikode)', 'staff_kozhikode', '$2a$12$TuD0H2ddT7/pX2FN4/cChumeE3PT7iCRaMwcKLcvU/gCAoBUtIFR.', 'STAFF', 1),
('Staff Two (Ernakulam)', 'staff_ernakulam', '$2a$12$TuD0H2ddT7/pX2FN4/cChumeE3PT7iCRaMwcKLcvU/gCAoBUtIFR.', 'STAFF', 3),
('Admin User',            'admin_main',      '$2a$12$iM3XCqh7eEdzQ.tcmASEXuAsMeKCUv8w.rLnZCm1PxNmofMXo3QBe', 'ADMIN', NULL);

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
