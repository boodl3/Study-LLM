ALTER TABLE users ADD COLUMN username VARCHAR(50);

UPDATE users SET username = split_part(email, '@', 1) || '_' || substr(id::text, 1, 8)
WHERE username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);
