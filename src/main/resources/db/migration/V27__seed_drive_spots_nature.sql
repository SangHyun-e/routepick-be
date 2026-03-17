-- V27: Add additional nature-focused drive spots

INSERT INTO drive_spots
    (name, lat, lng, region, spot_type, themes, view_score, drive_suitability, stay_minutes, is_active, source_type)
VALUES
    ('서울숲', 37.5444, 127.0370, '서울', '공원', 'nature,park,walk', 0.78, 0.68, 60, 1, 'CURATED'),
    ('양재시민의숲', 37.4719, 127.0353, '서울', '숲', 'nature,forest,walk', 0.77, 0.66, 60, 1, 'CURATED'),
    ('북서울꿈의숲', 37.6204, 127.0417, '서울', '공원', 'nature,park,walk', 0.76, 0.65, 60, 1, 'CURATED'),
    ('길동생태공원', 37.5450, 127.1545, '서울', '공원', 'nature,eco,walk', 0.75, 0.64, 50, 1, 'CURATED'),
    ('불암산 전망대', 37.6723, 127.0822, '서울', '전망대', 'nature,view,drive', 0.82, 0.72, 50, 1, 'CURATED'),
    ('인왕산 전망대', 37.5869, 126.9588, '서울', '전망대', 'nature,view,walk', 0.81, 0.70, 50, 1, 'CURATED'),
    ('광릉숲', 37.7530, 127.1594, '경기', '숲', 'nature,forest,walk', 0.83, 0.71, 60, 1, 'CURATED'),
    ('남양주 물의정원', 37.5657, 127.3080, '경기', '강변', 'nature,river,walk', 0.80, 0.70, 60, 1, 'CURATED'),
    ('일산호수공원', 37.6595, 126.7680, '경기', '호수', 'nature,lake,walk', 0.82, 0.71, 60, 1, 'CURATED'),
    ('가평 자라섬', 37.8139, 127.5151, '경기', '강변', 'nature,river,walk', 0.81, 0.70, 70, 1, 'CURATED'),
    ('시흥 갯골생태공원', 37.3877, 126.7834, '경기', '강변', 'nature,eco,walk', 0.79, 0.68, 60, 1, 'CURATED'),
    ('양평 들꽃수목원', 37.5054, 127.5375, '경기', '수목원', 'nature,forest,walk', 0.84, 0.72, 70, 1, 'CURATED');
