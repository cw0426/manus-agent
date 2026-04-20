package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 恋爱大师向量数据库配置（初始化基于内存的向量数据库 Bean）
 */
@Configuration
@Slf4j
public class LoveAppVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRIES = 5;

    /**
     * 基础重试间隔（毫秒）- 使用指数退避
     */
    private static final long BASE_RETRY_DELAY_MS = 3000;

    /**
     * 每批处理的文档数量
     */
    private static final int BATCH_SIZE = 5;

    /**
     * 批次之间的间隔（毫秒）
     */
    private static final long BATCH_DELAY_MS = 2000;

    @Bean
    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        try {
            // 加载文档
            List<Document> documentList = loveAppDocumentLoader.loadMarkdowns();
            // 自动补充关键词元信息
            List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documentList);
            // 分批添加到向量存储
            addDocumentsInBatches(simpleVectorStore, enrichedDocuments);
        } catch (Exception e) {
            // Embedding 模型额度不足或其他异常时，降级为空 VectorStore，不影响应用启动
            log.warn("恋爱大师向量数据库初始化失败，RAG 功能将不可用: {}", e.getMessage());
        }
        return simpleVectorStore;
    }

    /**
     * 分批添加文档到向量存储
     */
    private void addDocumentsInBatches(SimpleVectorStore vectorStore, List<Document> documents) {
        if (documents.isEmpty()) {
            log.info("没有文档需要添加到向量数据库");
            return;
        }

        int totalBatches = (int) Math.ceil((double) documents.size() / BATCH_SIZE);
        int successCount = 0;

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int fromIndex = batchIndex * BATCH_SIZE;
            int toIndex = Math.min(fromIndex + BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(fromIndex, toIndex);

            log.info("向量存储处理第 {}/{} 批文档，共 {} 个文档", batchIndex + 1, totalBatches, batch.size());

            if (addBatchWithRetry(vectorStore, batch, batchIndex + 1)) {
                successCount += batch.size();
            }

            // 批次之间加入延迟，避免触发限流
            if (batchIndex < totalBatches - 1) {
                try {
                    Thread.sleep(BATCH_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("恋爱大师向量数据库初始化完成，成功加载 {}/{} 个文档片段", successCount, documents.size());
    }

    /**
     * 带重试机制的单批文档添加方法
     */
    private boolean addBatchWithRetry(SimpleVectorStore vectorStore, List<Document> batch, int batchNum) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                vectorStore.add(batch);
                return true;
            } catch (Exception e) {
                if (isRateLimitError(e)) {
                    long delay = BASE_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1); // 指数退避：3s, 6s, 12s, 24s, 48s
                    log.warn("第 {} 批向量存储遇到限流，第 {} 次重试，等待 {} 毫秒后重试...", batchNum, attempt, delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    log.warn("第 {} 批向量存储添加失败: {}", batchNum, e.getMessage());
                    return false;
                }
            }
        }
        log.warn("第 {} 批向量存储多次重试后仍失败", batchNum);
        return false;
    }

    /**
     * 判断是否为限流错误
     */
    private boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("429") || message.contains("RateQuota") || message.contains("rate limit"));
    }
}
