package com.example.filebatchprocessor.unit.batch.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.batch.writer.FileImportRecordWriter;
import com.example.filebatchprocessor.batch.writer.strategy.ChunkImportStrategy;
import com.example.filebatchprocessor.batch.writer.strategy.ImportContext;
import com.example.filebatchprocessor.exception.TransientImportException;
import com.example.filebatchprocessor.model.FileRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.test.MetaDataInstanceFactory;

class FileImportRecordWriterTest {

    private ChunkImportStrategy batchStrategy;
    private ChunkImportStrategy fallbackStrategy;
    private FileImportRecordWriter writer;

    @BeforeEach
    void setup() {
        batchStrategy = org.mockito.Mockito.mock(ChunkImportStrategy.class);
        fallbackStrategy = org.mockito.Mockito.mock(ChunkImportStrategy.class);
        writer = new FileImportRecordWriter("2026-06-26", batchStrategy, fallbackStrategy, 1000);
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        writer.beforeStep(stepExecution);
    }

    private FileRecord record(String name) {
        FileRecord r = new FileRecord();
        r.setName(name);
        r.setDescription("d-" + name);
        return r;
    }

    @Test
    void 全新记录走批量快路径不触发降级() throws Exception {
        when(batchStrategy.persist(anyList(), any(ImportContext.class))).thenReturn(2);

        writer.write(Chunk.of(record("a"), record("b")));

        verify(batchStrategy, times(1)).persist(anyList(), any(ImportContext.class));
        verifyNoInteractions(fallbackStrategy);
    }

    @Test
    void 批量失败时降级到逐条路径() throws Exception {
        when(batchStrategy.persist(anyList(), any(ImportContext.class)))
                .thenThrow(new RuntimeException("batch insert failed"));
        when(fallbackStrategy.persist(anyList(), any(ImportContext.class))).thenReturn(2);

        writer.write(Chunk.of(record("a"), record("b")));

        verify(batchStrategy, times(1)).persist(anyList(), any(ImportContext.class));
        verify(fallbackStrategy, times(1)).persist(anyList(), any(ImportContext.class));
    }

    @Test
    void 瞬时异常直接上抛交给重试不降级() throws Exception {
        when(batchStrategy.persist(anyList(), any(ImportContext.class)))
                .thenThrow(new TransientImportException("transient", new RuntimeException()));

        assertThatThrownBy(() -> writer.write(Chunk.of(record("a")))).isInstanceOf(TransientImportException.class);

        verifyNoInteractions(fallbackStrategy);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void 批内重复键只持久化一次() throws Exception {
        when(batchStrategy.persist(anyList(), any(ImportContext.class))).thenReturn(1);

        // 同名 -> 同 businessKey,跨两个 chunk
        writer.write(Chunk.of(record("dup")));
        writer.write(Chunk.of(record("dup")));

        ArgumentCaptor<List<FileRecord>> captor = ArgumentCaptor.forClass((Class) List.class);
        // 第一次持久化 1 条,第二次因去重无新记录,batchStrategy 不应再被调用
        verify(batchStrategy, times(1)).persist(captor.capture(), any(ImportContext.class));
        assertThat(captor.getValue()).hasSize(1);
    }
}
