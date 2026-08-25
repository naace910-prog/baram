-- H2 file DB에 최초 시드 (동일 name 존재 시 스킵)
MERGE INTO raid_targets (id, name, drop_item_name, memo) KEY(name) VALUES
    (1, '해골왕', '해골왕의 뼈', NULL),
    (2, '흑룡',   '흑룡의 어금니', NULL),
    (3, '감룡',   '감룡의 어금니', NULL),
    (4, '묵룡',   '묵룡의 어금니', NULL),
    (5, '진룡',   '진룡의 어금니', NULL);
