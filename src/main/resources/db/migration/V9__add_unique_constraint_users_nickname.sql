-- users.nickname UNIQUE 인덱스 추가

ALTER TABLE users
    MODIFY nickname VARCHAR(40) NOT NULL;

CREATE UNIQUE INDEX uk_users_nickname
    ON users (nickname);