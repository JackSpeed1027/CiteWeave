package dev.citeweave.source;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SourceRepository {
    record Summary(long id, String title, String kind, String filename, OffsetDateTime createdAt) {}
    record Detail(Summary source, String content) {}

    private static final String COLUMNS = "id, title, kind, filename, created_at";
    private static final RowMapper<Summary> SUMMARY = (rs, row) -> new Summary(
            rs.getLong("id"), rs.getString("title"), rs.getString("kind"),
            rs.getString("filename"), rs.getObject("created_at", OffsetDateTime.class));
    private static final RowMapper<Detail> DETAIL = (rs, row) ->
            new Detail(SUMMARY.mapRow(rs, row), rs.getString("content"));

    private final JdbcClient jdbc;

    SourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Summary> list() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sources ORDER BY created_at DESC, id DESC")
                .query(SUMMARY).list();
    }

    Optional<Detail> find(long id) {
        return jdbc.sql("SELECT " + COLUMNS + ", content FROM sources WHERE id = :id")
                .param("id", id).query(DETAIL).optional();
    }

    Detail insert(String title, String kind, String filename, String content) {
        return jdbc.sql("""
                INSERT INTO sources (title, kind, filename, content)
                VALUES (:title, :kind, :filename, :content)
                RETURNING """ + " " + COLUMNS + ", content")
                .param("title", title).param("kind", kind).param("filename", filename)
                .param("content", content).query(DETAIL).single();
    }
}
