package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.citeweave.source.EvidenceLexicalMapper.Chunk;
import dev.citeweave.source.EvidenceLexicalMapper.Key;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 验证真实 PostgreSQL 中的排序一致、版本隔离以及事务发布。 */
@SpringBootTest(properties = {"citeweave.retrieval.lexical-index-enabled=true", "citeweave.tasks.worker-enabled=false"})
@EnabledIfEnvironmentVariable(named = "CITEWEAVE_LEXICAL_INDEX_TEST", matches = "1")
class EvidenceLexicalIndexIntegrationTest {
    @Autowired EvidenceLexicalIndex index;
    @Autowired EvidenceLexicalMapper mapper;
    @Autowired DocumentEvidenceReader documents;
    @Autowired SourceRepository sources;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void matchesOldWordsAndEvidenceOrderingOnFrozenQueries() throws Exception {
        Path root = Path.of("..", "sample-data", "retrieval", "transcript-v1");
        var dataset = json.readValue(root.resolve("questions.json").toFile(), TranscriptRetrievalBenchmarkTest.Dataset.class);
        var ids = new HashMap<String, Long>();
        for (var entry : dataset.sources().entrySet()) {
            var filename = entry.getValue();
            ids.put(entry.getKey(), sources.insert(entry.getKey(), "SRT", filename,
                    Files.readString(root.resolve(filename))).source().id());
        }

        for (var question : dataset.questions()) {
            long id = ids.get(question.source());
            var expected = TranscriptQuestionService.search(List.of(documents.readForMixedQuestion(id)), question.text());
            assertEquals(expected, index.search(List.of(id), question.text()), question.id());
        }
        System.out.println("PostgreSQL ordered evidence matched " + dataset.questions().size() + " frozen queries");
    }

    @Test
    void buildsOneCompleteGenerationAndDoesNotReadOutdatedEvidence() throws Exception {
        long id = sources.insert("版本化词面夹具", "MARKDOWN", "lexical.md", "缓存回填复核。\n\n缓存统计输出。")
                .source().id();
        var gate = new CountDownLatch(2);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> searchAtGate(gate, id));
            var second = workers.submit(() -> searchAtGate(gate, id));
            assertEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        var original = index.search(List.of(id), "缓存");
        assertEquals(2, original.size());
        assertEquals(1, countGenerations(id));
        assertEquals(2, jdbc.sql("SELECT count(*) FROM lexical_evidence_chunks WHERE source_id = :id")
                .param("id", id).query(Integer.class).single());

        var bad = new Key(id, original.getFirst().sourceVersionId(), "lexical-bad-fixture");
        var transaction = new TransactionTemplate(transactions);
        assertThrows(DataAccessException.class, () -> transaction.executeWithoutResult(status -> {
            mapper.reserve(bad);
            mapper.insertChunks(bad, List.of(new Chunk(-1, original.getFirst(), "缓存")));
        }));
        assertEquals(1, countGenerations(id));
        assertEquals(original, index.search(List.of(id), "缓存"));

        // 使用独立夹具模拟源版本切换，旧索引仍在，但新查询只读最新版本。
        jdbc.sql("UPDATE sources SET content = :text WHERE id = :id").param("id", id)
                .param("text", "Redis 无需等待。").update();
        assertTrue(index.search(List.of(id), "缓存").isEmpty());
        var latest = index.search(List.of(id), "Redis").getFirst();
        assertNotEquals(original.getFirst().sourceVersionId(), latest.sourceVersionId());
        assertEquals(latest, documents.readEvidence(id, latest.evidenceId()));
        assertEquals(2, countGenerations(id));
    }

    private int countGenerations(long id) {
        return jdbc.sql("SELECT count(*) FROM lexical_evidence_generations WHERE source_id = :id")
                .param("id", id).query(Integer.class).single();
    }

    private Object searchAtGate(CountDownLatch gate, long id) throws InterruptedException {
        gate.countDown();
        assertTrue(gate.await(5, TimeUnit.SECONDS));
        return index.search(List.of(id), "缓存");
    }
}
