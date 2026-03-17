-- V26: Seed curated drive_spots

DELETE FROM drive_spots;

INSERT INTO drive_spots
    (name, lat, lng, region, spot_type, themes, view_score, drive_suitability, stay_minutes, is_active, source_type)
VALUES
    ('북악스카이웨이 팔각정', 37.5964, 126.9672, '서울', '전망대', 'night,view,drive', 0.88, 0.82, 40, 1, 'CURATED'),
    ('응봉산 전망대', 37.5509, 127.0227, '서울', '전망대', 'night,view,river', 0.82, 0.70, 40, 1, 'CURATED'),
    ('반포한강공원', 37.5095, 126.9956, '서울', '공원', 'night,river,drive', 0.78, 0.72, 50, 1, 'CURATED'),
    ('여의도한강공원', 37.5285, 126.9346, '서울', '공원', 'night,river,drive', 0.76, 0.70, 50, 1, 'CURATED'),
    ('하늘공원', 37.5683, 126.8859, '서울', '공원', 'nature,sunset,walk', 0.80, 0.66, 60, 1, 'CURATED'),
    ('북한산 둘레길 우이령 입구', 37.6632, 127.0125, '서울', '둘레길', 'nature,trail,forest', 0.79, 0.70, 50, 1, 'CURATED'),
    ('아차산 전망대', 37.5517, 127.1035, '서울', '전망대', 'nature,view,drive', 0.86, 0.74, 50, 1, 'CURATED'),
    ('광교호수공원', 37.2894, 127.0645, '경기', '호수', 'nature,night,lake', 0.84, 0.72, 60, 1, 'CURATED'),
    ('남한산성 전망대', 37.4789, 127.1818, '경기', '전망대', 'nature,view,drive', 0.84, 0.76, 70, 1, 'CURATED'),
    ('팔당호 전망대', 37.5672, 127.2433, '경기', '호수', 'nature,lake,drive', 0.87, 0.80, 45, 1, 'CURATED'),
    ('두물머리', 37.5289, 127.3352, '경기', '강변', 'nature,sunrise,walk', 0.83, 0.72, 60, 1, 'CURATED'),
    ('포천 산정호수', 38.0746, 127.3025, '경기', '호수', 'nature,lake,walk', 0.85, 0.73, 70, 1, 'CURATED'),
    ('아침고요수목원', 37.7433, 127.3524, '경기', '수목원', 'nature,forest,walk', 0.88, 0.69, 90, 1, 'CURATED'),
    ('포레스트 아웃팅스 파주', 37.8007, 126.6833, '경기', '카페', 'cafe,large,view', 0.74, 0.72, 80, 1, 'CURATED'),
    ('카페 대너리스 남양주', 37.6516, 127.2398, '경기', '카페', 'cafe,view,drive', 0.76, 0.74, 70, 1, 'CURATED'),
    ('양평 보나리베 카페', 37.4849, 127.4985, '경기', '카페', 'cafe,river,view', 0.75, 0.72, 70, 1, 'CURATED');
