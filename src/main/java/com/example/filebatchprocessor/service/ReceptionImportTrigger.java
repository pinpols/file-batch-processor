package com.example.filebatchprocessor.service;

/**
 * 清单驱动入库:到达组对账通过后的导入触发钩子。
 *
 * <p>抽成可注入接口便于测试 verify;生产默认实现见 {@link DefaultReceptionImportTrigger}。
 */
@FunctionalInterface
public interface ReceptionImportTrigger {

    /**
     * 触发指定文件接收队列行的导入。
     *
     * @param queueId 文件接收队列行 id({@code ReceptionGroupMember.actualQueueId})
     */
    void triggerImport(Long queueId);
}
