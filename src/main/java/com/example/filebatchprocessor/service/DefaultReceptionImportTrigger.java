package com.example.filebatchprocessor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link ReceptionImportTrigger} 的生产默认实现(v1 钩子)。
 *
 * <p>当前仅记录日志占位;真正的导入触发(投递入库 job)留待后续接入。
 */
@Slf4j
@Component
public class DefaultReceptionImportTrigger implements ReceptionImportTrigger {

    @Override
    public void triggerImport(Long queueId) {
        // TODO: 接入真正的入库 job 触发(v1 仅占位钩子)
        log.info("[reception-import] 占位触发导入: queueId={}", queueId);
    }
}
