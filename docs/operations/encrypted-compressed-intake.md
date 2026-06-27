# 加密(PGP)/压缩文件导入

导入支持读取经 **PGP 加密** 和/或 **压缩** 的源文件。预处理在导入读取前透明完成:先解密、再解压,得到明文后按既有 CSV/Excel 流程导入。存量未加密未压缩的导入零影响。

## 支持的格式

- 加密:PGP(armored / 二进制)。
- 压缩:`.gz`(gzip)、`.zip`(仅取压缩包内 **首个普通 entry**)。

## Job 参数

| 参数 | 取值 | 说明 |
| --- | --- | --- |
| `input.file.encrypted` | `true` / `false` | 是否为 PGP 加密文件。缺省时按文件后缀 `.pgp` / `.gpg` 自动判定。 |
| `input.file.compression` | `gz` / `zip` / `none` | 压缩方式。缺省时按文件后缀 `.gz` / `.zip` 自动判定。 |

显式参数 **优先于** 后缀推断。例如文件名为 `order.csv.gz.pgp` 但实际未压缩,可显式传 `input.file.compression=none` 覆盖后缀推断。

## PGP 私钥与口令(密钥不入仓、不进日志)

- 私钥文件路径:`batch.pgp.private-key-path`,指向 **armored 私钥文件**。
- 口令(passphrase):走环境变量 `BATCH_PGP_PASSPHRASE`,在 `application.yml` 中以

  ```yaml
  batch:
    pgp:
      private-key-path: /etc/file-batch-processor/secrets/import-private.asc
      passphrase: ${BATCH_PGP_PASSPHRASE:}
  ```

  方式注入。

> **安全红线:私钥文件与 passphrase 绝不提交到代码仓库、绝不写入任何日志。** 私钥文件以受限权限存放于部署机密目录,passphrase 仅经环境变量下发。

## 临时明文文件

- 解密 / 解压后的明文写入 `batch.io.temp-dir` 指定目录(默认:系统临时目录下的 `fbp-import-tmp`)。
- 临时文件以 **UUID 命名**,避免冲突与可预测路径。
- 导入 step 结束时(`afterStep`)自动删除临时明文,无残留。

```yaml
batch:
  io:
    temp-dir: ${java.io.tmpdir}/fbp-import-tmp
```

## 安全说明

- **zip slip 防护**:zip 仅取压缩包内首个普通 entry 的内容,**不使用 entry 名** 作为落盘路径,因此不存在 `../` 目录穿越风险。
- **解压炸弹防护**:解压输出设 **2GB 上限**,超过即中止,防止压缩炸弹耗尽磁盘 / 内存。

## 透传(零影响)

未加密且未压缩的文件 **直接使用原文件读取**,不落临时文件、不经解密 / 解压链路,行为与历史导入完全一致。

## 配置示例

文件 `order.csv.gz.pgp`(先 gzip 压缩,再 PGP 加密)的 task 参数:

```properties
input.file.name=order.csv.gz.pgp
input.file.encrypted=true
input.file.compression=gz
```

口令通过环境变量提供:

```bash
export BATCH_PGP_PASSPHRASE='********'
```
