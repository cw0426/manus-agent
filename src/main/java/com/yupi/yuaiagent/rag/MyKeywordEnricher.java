package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 AI 的文档元信息增强器（为文档补充元信息）
 */
@Component
@Slf4j
public class MyKeywordEnricher {

    @Resource
    private ChatModel dashscopeChatModel;

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
    private static final int BATCH_SIZE = 3;

    /**
     * 批次之间的间隔（毫秒）
     */
    private static final long BATCH_DELAY_MS = 2000;

    public List<Document> enrichDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return documents;
        }

        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(dashscopeChatModel, 5);

        // 分批处理文档
        List<Document> allEnrichedDocuments = new ArrayList<>();
        int totalBatches = (int) Math.ceil((double) documents.size() / BATCH_SIZE);

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int fromIndex = batchIndex * BATCH_SIZE;
            int toIndex = Math.min(fromIndex + BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(fromIndex, toIndex);

            log.info("处理第 {}/{} 批文档，共 {} 个文档", batchIndex + 1, totalBatches, batch.size());

            List<Document> enrichedBatch = enrichBatchWithRetry(keywordMetadataEnricher, batch, batchIndex + 1);
            allEnrichedDocuments.addAll(enrichedBatch);

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

        log.info("文档关键词增强完成，共处理 {} 个文档", allEnrichedDocuments.size());
        return allEnrichedDocuments;
    }

    /**
     * 带重试机制的单批文档增强方法
     */
    private List<Document> enrichBatchWithRetry(KeywordMetadataEnricher enricher, List<Document> batch, int batchNum) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return enricher.apply(batch);
            } catch (Exception e) {
                if (isRateLimitError(e)) {
                    long delay = BASE_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1); // 指数退避：3s, 6s, 12s, 24s, 48s
                    log.warn("第 {} 批文档遇到限流，第 {} 次重试，等待 {} 毫秒后重试...", batchNum, attempt, delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.warn("第 {} 批文档关键词增强失败，跳过增强: {}", batchNum, e.getMessage());
                    return batch;
                }
            }
        }
        log.warn("第 {} 批文档关键词增强多次重试后仍失败，返回原始文档", batchNum);
        return batch;
    }

    /**
     * 判断是否为限流错误
     */
    private boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("429") || message.contains("RateQuota") || message.contains("rate limit"));
    }
}
