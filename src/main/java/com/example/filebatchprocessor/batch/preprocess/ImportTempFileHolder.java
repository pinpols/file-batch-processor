package com.example.filebatchprocessor.batch.preprocess;

import java.nio.file.Path;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

/**
 * 在 reader 创建预处理临时明文文件时记录其路径,供 step 的清理 listener 在 afterStep 删除。
 * @StepScope 保证每个 step 执行独立持有,避免并发互串。
 */
@Component
@StepScope
public class ImportTempFileHolder {

    private Path tempFile;

    public Path get() {
        return tempFile;
    }

    public void set(Path tempFile) {
        this.tempFile = tempFile;
    }

    public void clear() {
        this.tempFile = null;
    }
}
