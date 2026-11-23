package dev.citeweave.source;

import dev.citeweave.source.DocumentEvidenceReader.IndexSource;
import dev.citeweave.source.EvidenceLexicalMapper.Chunk;
import dev.citeweave.source.EvidenceLexicalMapper.Key;
import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 版本化词面索引。复用现有证据切片、词项和跨资料排序规则。 */
@Service
class EvidenceLexicalIndex {
    private static final String INDEX_VERSION = "lexical-v1";
    private static final int CHUNK_BATCH = 128;
    private static final int PER_SOURCE_LIMIT = 2;
    private final DocumentEvidenceReader documents;
    private final EvidenceLexicalMapper mapper;
    private final TransactionTemplate transaction;
    private final boolean enabled;

    EvidenceLexicalIndex(DocumentEvidenceReader documents, EvidenceLexicalMapper mapper,
                         PlatformTransactionManager manager, @Value("${citeweave.retrieval.lexical-index-enabled:false}") boolean enabled) {
        this.documents = documents;
        this.mapper = mapper;
        this.enabled = enabled;
        transaction = new TransactionTemplate(manager);
    }

    /** 保持旧模式可用。启用时，先校验资料，再只查该资料当前的版本。 */
    List<Evidence> search(List<Long> ids, String query) {
        if (!enabled) {
            return TranscriptQuestionService.search(ids.stream().map(documents::readForMixedQuestion).toList(), query);
        }
        var sources = ids.stream().map(documents::indexSource).toList();
        String queryTerms = joined(TranscriptQuestionService.queryTerms(query));
        var groups = new ArrayList<List<Evidence>>(sources.size());
        for (var source : sources) {
            var key = new Key(source.id(), source.version(), INDEX_VERSION);
            ensure(key, source);
            groups.add(queryTerms.isEmpty() ? List.of() : mapper.search(key, queryTerms, PER_SOURCE_LIMIT));
        }
        return TranscriptQuestionService.interleave(groups);
    }

    private void ensure(Key key, IndexSource source) {
        if (mapper.exists(key)) return;

        // 切片和分词在事务外完成；只有首个成功插入版本的请求写片段。
        var evidence = documents.readForMixedQuestion(source);
        var chunks = new ArrayList<Chunk>();
        for (int ordinal = 0; ordinal < evidence.size(); ordinal++) {
            var item = evidence.get(ordinal);
            if (item.text().length() > TranscriptQuestionService.MAX_CUE_LENGTH) continue;
            String terms = joined(TranscriptQuestionService.terms(item.text()));
            if (!terms.isEmpty()) chunks.add(new Chunk(ordinal, item, terms));
        }

        transaction.executeWithoutResult(status -> {
            if (mapper.reserve(key) == 0) return;
            for (int offset = 0; offset < chunks.size(); offset += CHUNK_BATCH) {
                mapper.insertChunks(key, chunks.subList(offset, Math.min(offset + CHUNK_BATCH, chunks.size())));
            }
        });
    }

    /** 分词器只生成不含空白的词项，按固定顺序序列化便于对照。 */
    private static String joined(Collection<String> terms) {
        return String.join(" ", terms.stream().sorted().toList());
    }
}
