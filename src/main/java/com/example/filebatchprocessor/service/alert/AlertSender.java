package com.example.filebatchprocessor.service.alert;

/** 可插拔告警发送渠道。新增渠道 = 新增一个 @Component 实现。 */
public interface AlertSender {
    String channel();

    boolean isEnabled();

    /** 允许抛异常,由 AlertDispatcher 隔离,不影响其它渠道。 */
    void send(AlertEvent event) throws Exception;
}
