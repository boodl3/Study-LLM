ALTER TABLE sources ADD COLUMN folder_name VARCHAR(500);
ALTER TABLE sources ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE sources s
SET sort_order = sub.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY notebook_id ORDER BY uploaded_at) - 1 AS rn
    FROM sources
) sub
WHERE s.id = sub.id;
