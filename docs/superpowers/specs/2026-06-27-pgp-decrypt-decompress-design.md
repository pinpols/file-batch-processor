# 设计:导入 PGP 解密 + 解压(.gz/.zip),分发侧可选加密

> 缺口 #5。日期 2026-06-27。状态:已批准设计,待写实现计划。

## 目标

企业文件常以 PGP 加密 + 压缩送达。让导入在喂给 reader 前能"解密(PGP)→解压(.gz/.zip)→再解析";分发侧可选 PGP 加密产物。严格单体单租户。

## 现状(file:line)

- 导入 reader 从 `Resource.getInputStream()` 逐行读;`open()` 重启时重新打开流并 readLine() 跳已读行(`FileImportRecordReader.java:129-145`)。Resource 在 `FileImportJobConfig.java:80-89` 构造(已 PathSafety.confine 限定 input.file.name)。
- checksum 是对解析后每行算 Adler32(`:109,152`),与文件原始字节无关;分片按行号(`:99-104`)。
- 导出已有单 entry zip(`FileExportService.java:214-217`)。分发取本地文件(`SftpFileDistributor.java:89`)。
- pom **无 BouncyCastle**;有 sshj/hutool/opencsv,Java 21。

## 方案

**在 reader 构造 Resource 之前,把文件解密+解压到临时明文文件,再用 FileSystemResource(临时文件)喂 reader。**(方案 a,非纯流式)

**为何不纯流式(方案 b)**:① 重启恢复要求 Resource 可多次 getInputStream() 且每次同字节——PGP 解密流不可重入;② 分片下每 shard 各读一遍全文件,流式要解密 N 次。临时文件解密一次共享。③ 职责隔离,reader 不感知加密/压缩。代价是临时明文落盘(见风险)。

checksum/分片作用在明文行上,**与方案无关、无需改动**。

## 范围边界

**做**:PGP 解密(导入侧,核心);压缩 .gz(单流)+ .zip(只取首个普通 entry,多 entry 忽略+WARN);透传(PLAIN 时零拷贝直接返回原 Resource,存量导入零影响);分发侧 PGP 加密最小可选版(task 显式标记才加密,单公钥不签名)——可后置到第二个 PR。

**不做(YAGNI)**:KMS/Vault/密钥轮换(私钥路径 + passphrase 走 env);zip 多 entry 合并/并行导入;PGP 签名;大 Excel 等无关项。

## 组件/接口

```
batch/preprocess/
  FilePreprocessor(prepare(Resource raw, Hints) -> PreprocessResult{plaintext, tempFileOrNull})
  FilePreprocessChain(detect -> decrypt? -> decompress? -> 落临时文件)
  detect/FileTypeDetector(后缀 + 显式参数 + 魔数交叉校验;显式 > 后缀)
  decrypt/PgpDecryptor + BouncyCastlePgpDecryptor
  decompress/Decompressor + GzipDecompressor + ZipDecompressor(单entry + zip slip 防护)
  TempFileManager(专用 0700 临时目录、UUID 名、step afterStep 必删、启动清扫)
service/distribution/encrypt/ PgpEncryptor + BouncyCastlePgpEncryptor(分发侧, 可后置)
```

判定:job param `input.file.encrypted` / `input.file.compression`(显式优先)+ 后缀推断。配置:`batch.pgp.private-key-path/public-key-path`、`batch.pgp.passphrase=${BATCH_PGP_PASSPHRASE}`、`batch.io.temp-dir`。

衔接:预处理插在 PathSafety.confine 之后、reader 构造之前;临时明文写独立 temp-dir(不在 input-base-dir 内,reader 拿到的临时 Resource 不再过 confine);importStep 挂 StepExecutionListener afterStep 删临时文件。

## 文件清单

新增 `batch/preprocess/**` + `service/distribution/encrypt/**`。改:`FileImportJobConfig`(预处理插入 + 清理 listener)、`ImportJobParams`(encrypted/compression)、分发侧 `FileDistributorDispatcher`(统一加密分支,避免改每个协议)、`pom.xml`(+bcpg/bcpkix-jdk18on)。Flyway 无(纯文件能力)。

## 风险

1. BouncyCastle:MIT-style 许可可商用;过 dependency-check CVE 门禁(选最新稳定固定版,进 nightly 复检);包体偏大。
2. 私钥/passphrase:passphrase 只走 env,私钥文件不入仓(.gitignore + CI 扫描),绝不入日志;注意 `FileImportRecordReader:114` 会打印整行,敏感导入需评估降级该日志。
3. 临时明文落盘泄漏面(方案 a 主代价):0700 专用目录 + UUID 名 + afterStep finally 必删 + 启动清扫 + 绝不写到 input-base-dir/导出目录。
4. 大文件:解密/解压用流 copy 内存恒定,但落临时文件需等量磁盘(预检空间 fail-fast);zip 防解压炸弹(设解压上限字节)。
5. zip slip:最稳=丢弃 entry 名只用 UUID;若保留名则 PathSafety.confine(tempDir, entryName)。
6. 与 input-base-dir 关系:源密文仍须在 base-dir 内;临时明文在独立 temp-dir 不经 confine(可信生成)。

## 测试计划

- 单测(引 BouncyCastle test 依赖造数据):测试用 PGP 密钥对(标注非生产、可入仓)+ 加密小样本 → BouncyCastlePgpDecryptor 解密字节相等;错误 passphrase 异常;Gzip/Zip 解压字节相等;**zip slip 用例**(entry 含 ../)被拒;多 entry 只取首个+WARN;解压炸弹中止;FileTypeDetector 优先级矩阵 + PLAIN 透传返回原 Resource;TempFileManager afterStep 必删(成功/异常);分发侧 round-trip。
- 集成:端到端喂 `xxx.csv.gz.pgp` → 预处理 → reader 正常解析、记录数/checksum 正确;重启恢复用例(临时文件可重读、跳行正确);分片用例(解密一次共享、各 shard 不重叠且并集完整)。
