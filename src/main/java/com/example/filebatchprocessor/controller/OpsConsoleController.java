package com.example.filebatchprocessor.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops")
public class OpsConsoleController {

    @GetMapping(value = "/console", produces = MediaType.TEXT_HTML_VALUE)
    public String console() {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>Batch Ops Console</title>
                  <style>
                    body{font-family:Menlo,Consolas,monospace;background:#f5f7fb;color:#1f2937;margin:24px}
                    .card{background:#fff;border:1px solid #dbe2ea;border-radius:12px;padding:16px;max-width:980px}
                    h1{margin:0 0 12px 0}
                    ul{line-height:1.8}
                    code{background:#eef2f7;padding:2px 6px;border-radius:6px}
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>内建运维控制台</h1>
                    <p>认证后可直接访问以下接口：</p>
                    <ul>
                      <li><a href="/ops/dashboard">/ops/dashboard</a>：运行看板</li>
                      <li><a href="/ops/tasks">/ops/tasks</a>：任务清单</li>
                      <li><a href="/ops/change-requests">/ops/change-requests</a>：变更单</li>
                      <li><a href="/ops/audit">/ops/audit</a>：审计日志</li>
                    </ul>
                    <p>建议：用 <code>operator</code> 发起变更单，用 <code>admin</code> 审批并应用。</p>
                  </div>
                </body>
                </html>
                """;
    }
}

