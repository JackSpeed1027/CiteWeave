CREATE TABLE bilibili_subtitle_tracks (
    source_id bigint PRIMARY KEY REFERENCES sources(id),
    bvid varchar(12) NOT NULL,
    page integer NOT NULL CHECK (page > 0),
    cid varchar(19) NOT NULL CHECK (cid ~ '^[1-9][0-9]{0,18}$'),
    video_title text NOT NULL,
    part_title text NOT NULL,
    track_source varchar(16) NOT NULL CHECK (track_source = 'USER_UPLOAD')
);
