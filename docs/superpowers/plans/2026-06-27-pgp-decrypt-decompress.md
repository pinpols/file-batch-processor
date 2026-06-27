# 导入 PGP 解密 + 解压(#5)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`).

**Goal:** 让导入在喂给 reader 之前能"解密(PGP)→解压(.gz/.zip)→落临时明文文件再解析";不加密不压缩则透传原文件。范围限导入侧;分发侧加密后置(另起 PR)。

**Architecture:** 在 `FileImportJobConfig.importReader` 构造 Resource 之前插入 `FilePreprocessor`:按显式参数/后缀判定 → 可选 PGP 解密 → 可选解压 → 写到专用临时目录的明文文件 → 用该临时文件构造 `FileSystemResource` 喂 reader。临时文件由 `importStep` 的 `StepExecutionListener` 在 afterStep 删除。透传(PLAIN)时直接用原文件、不建临时文件。

**Tech Stack:** Java 21 / Spring Batch / BouncyCastle(bcpg/bcpkix-jdk18on)/ java.util.zip。

**设计依据:** `docs/superpowers/specs/2026-06-27-pgp-decrypt-decompress-design.md`

**已核实的现状(main 分支):**
- `FileImportJobConfig.importReader`(@StepScope):`ImportJobParams.from(jobParameters)` → 若 `input.file.name` 非空则 `new FileSystemResource(params.getInputFileName())`(line ~82),否则 classpath 样例。**main 上无 PathSafety**(不依赖它)。reader 现为 8 参构造(含 #4 的 DocumentRecordReaderFactory + DocumentReadOptions)。
- `importStep`(line ~120):`StepBuilder("importStep").chunk(chunkSize).reader(reader).processor(processor).writer(writer)...`,已有 `ParseErrorRateGateListener`/`ShardContextListener` 等 listener 注入风格可参考。
- `ImportJobParams`:`JobParameterAccessor` + `KEY_*` 常量 + `from(...)` + getter。最近加了 excel.sheet.*。
- pom **无 BouncyCastle**;有 hutool/sshj/opencsv,Java 21。
- 导出侧 `FileExportService` 有 `ZipOutputStream`(.zip 写)可参考。

**关键设计决策:**
- **临时明文文件方案**(非纯流式):因 reader 重启恢复要求 Resource 可重复读同字节、分片要多次读;临时文件解密一次共享。
- 透传:detect=PLAIN 时返回原 `FileSystemResource(原路径)`,不进临时目录,存量导入零影响。
- 临时目录:`batch.io.temp-dir`(默认系统临时目录下专用子目录),文件名 UUID(断开穿越链),afterStep 必删。
- zip 只取首个普通 entry;zip slip 用 UUID 临时文件名规避(不用 entry 名)。
- 密钥:私钥文件路径 `batch.pgp.private-key-path`、passphrase `${BATCH_PGP_PASSPHRASE}`(env)。

---

## File Structure

新增 `src/main/java/com/example/filebatchprocessor/batch/preprocess/`:
- `FileKind.java` — enum(PLAIN, GZIP, ZIP)+ 是否加密 boolean 分开表达
- `DetectedType.java` — record(boolean encrypted, FileKind compression)
- `FileTypeDetector.java` — 后缀 + 显式参数判定
- `Decompressor.java` — gzip/zip 解压(InputStream→OutputStream)
- `PgpDecryptor.java` — 接口
- `BouncyCastlePgpDecryptor.java` — BC 实现
- `TempFileManager.java` — 临时目录管理 + 删除
- `FilePreprocessor.java` — 编排:detect→decrypt?→decompress?→落临时文件;返回 `PreprocessResult(Path plaintextPath, Path tempFileOrNull)`
- `PreprocessResult.java` — record

修改:
- `pom.xml` — +bcpg/bcpkix-jdk18on
- `params/ImportJobParams.java` — encrypted/compression 参数
- `batch/config/FileImportJobConfig.java` — importReader 插预处理 + importStep 挂清理 listener
- `src/main/resources/application.yml` — batch.io.temp-dir / batch.pgp.*

测试 `src/test/java/com/example/filebatchprocessor/unit/batch/preprocess/`:
- `FileTypeDetectorTest`、`DecompressorTest`、`BouncyCastlePgpDecryptorTest`、`FilePreprocessorTest`

---

## Task 1: BouncyCastle 依赖

**Files:** Modify `pom.xml`

- [ ] **Step 1: 加依赖**(`<dependencies>` 区,bcpg + bcpkix):
```xml
        <dependency>
            <groupId>org.bouncycastle</groupId>
            <artifactId>bcpg-jdk18on</artifactId>
            <version>1.78.1</version>
        </dependency>
        <dependency>
            <groupId>org.bouncycastle</groupId>
            <artifactId>bcpkix-jdk18on</artifactId>
            <version>1.78.1</version>
        </dependency>
```
注:1.78.1 若拉不到改最近 1.78.x/1.79;过 dependency-check CVE 门禁选最新稳定。

- [ ] **Step 2: 验证** `./mvnw dependency:tree 2>/dev/null | grep -i bouncycastle` 出现两项;`./mvnw -q -DskipTests test-compile` BUILD SUCCESS。

- [ ] **Step 3: 提交**
```bash
git add pom.xml
git commit -m "build: 加 BouncyCastle(bcpg/bcpkix)供导入 PGP 解密"
```

---

## Task 2: FileTypeDetector(后缀 + 显式参数判定)

**Files:**
- Create: `batch/preprocess/FileKind.java`、`DetectedType.java`、`FileTypeDetector.java`
- Test: `unit/batch/preprocess/FileTypeDetectorTest.java`

- [ ] **Step 1: 写失败测试**
```java
package com.example.filebatchprocessor.unit.batch.preprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.batch.preprocess.DetectedType;
import com.example.filebatchprocessor.batch.preprocess.FileKind;
import com.example.filebatchprocessor.batch.preprocess.FileTypeDetector;
import org.junit.jupiter.api.Test;

class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    @Test
    void detectsBySuffix() {
        DetectedType t = detector.detect("data.csv.gz.pgp", null, null);
        assertTrue(t.encrypted());
        assertEquals(FileKind.GZIP, t.compression());
    }

    @Test
    void plainWhenNoSuffix() {
        DetectedType t = detector.detect("data.csv", null, null);
        assertFalse(t.encrypted());
        assertEquals(FileKind.PLAIN, t.compression());
    }

    @Test
    void explicitOverridesSuffix() {
        // 文件名说 plain,但显式参数说加密 + zip
        DetectedType t = detector.detect("data.csv", Boolean.TRUE, "zip");
        assertTrue(t.encrypted());
        assertEquals(FileKind.ZIP, t.compression());
    }
}
```

- [ ] **Step 2:** `./mvnw test -Dtest=FileTypeDetectorTest -q` 确认编译失败。

- [ ] **Step 3: 实现**

`FileKind.java`:
```java
package com.example.filebatchprocessor.batch.preprocess;

public enum FileKind {
    PLAIN,
    GZIP,
    ZIP
}
```

`DetectedType.java`:
```java
package com.example.filebatchprocessor.batch.preprocess;

public record DetectedType(boolean encrypted, FileKind compression) {}
```

`FileTypeDetector.java`:
```java
package com.example.filebatchprocessor.batch.preprocess;

import java.util.Locale;
import org.springframework.stereotype.Component;

/** 判定文件是否 PGP 加密 + 压缩类型。显式参数优先于后缀。 */
@Component
public class FileTypeDetector {

    /**
     * @param fileName 文件名
     * @param explicitEncrypted 显式 input.file.encrypted(可空)
     * @param explicitCompression 显式 input.file.compression(gz/zip/none,可空)
     */
    public DetectedType detect(String fileName, Boolean explicitEncrypted, String explicitCompression) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);

        boolean encrypted = explicitEncrypted != null
                ? explicitEncrypted
                : (lower.endsWith(".pgp") || lower.endsWith(".gpg"));

        // 去掉加密后缀再看压缩
        String afterDecrypt = lower;
        if (afterDecrypt.endsWith(".pgp")) {
            afterDecrypt = afterDecrypt.substring(0, afterDecrypt.length() - 4);
        } else if (afterDecrypt.endsWith(".gpg")) {
            afterDecrypt = afterDecrypt.substring(0, afterDecrypt.length() - 4);
        }

        FileKind compression;
        if (explicitCompression != null && !explicitCompression.isBlank()) {
            compression = switch (explicitCompression.trim().toLowerCase(Locale.ROOT)) {
                case "gz", "gzip" -> FileKind.GZIP;
                case "zip" -> FileKind.ZIP;
                default -> FileKind.PLAIN;
            };
        } else if (afterDecrypt.endsWith(".gz")) {
            compression = FileKind.GZIP;
        } else if (afterDecrypt.endsWith(".zip")) {
            compression = FileKind.ZIP;
        } else {
            compression = FileKind.PLAIN;
        }

        return new DetectedType(encrypted, compression);
    }
}
```

- [ ] **Step 4:** `./mvnw test -Dtest=FileTypeDetectorTest -q` 确认 3 PASS。
- [ ] **Step 5: 提交**
```bash
git add src/main/java/com/example/filebatchprocessor/batch/preprocess/FileKind.java src/main/java/com/example/filebatchprocessor/batch/preprocess/DetectedType.java src/main/java/com/example/filebatchprocessor/batch/preprocess/FileTypeDetector.java src/test/java/com/example/filebatchprocessor/unit/batch/preprocess/FileTypeDetectorTest.java
git commit -m "feat(import): FileTypeDetector(PGP/压缩判定,显式优先后缀)"
```

---

## Task 3: Decompressor(gzip + zip 单 entry,zip slip 安全)

**Files:**
- Create: `batch/preprocess/Decompressor.java`
- Test: `unit/batch/preprocess/DecompressorTest.java`

- [ ] **Step 1: 写失败测试**
```java
package com.example.filebatchprocessor.unit.batch.preprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.batch.preprocess.Decompressor;
import com.example.filebatchprocessor.batch.preprocess.FileKind;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class DecompressorTest {

    private final Decompressor decompressor = new Decompressor();

    @Test
    void gunzipsToOriginal() throws Exception {
        byte[] original = "id,name\n1,alice\n".getBytes();
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(gz)) {
            g.write(original);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        decompressor.decompress(FileKind.GZIP, new ByteArrayInputStream(gz.toByteArray()), out);
        assertEquals(new String(original), out.toString());
    }

    @Test
    void unzipsFirstEntry() throws Exception {
        byte[] original = "id,name\n2,bob\n".getBytes();
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(zip)) {
            z.putNextEntry(new ZipEntry("data.csv"));
            z.write(original);
            z.closeEntry();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        decompressor.decompress(FileKind.ZIP, new ByteArrayInputStream(zip.toByteArray()), out);
        assertEquals(new String(original), out.toString());
    }
}
```

- [ ] **Step 2:** `./mvnw test -Dtest=DecompressorTest -q` 确认编译失败。

- [ ] **Step 3: 实现**
```java
package com.example.filebatchprocessor.batch.preprocess;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/** 解压:gzip 单流 / zip 取首个普通 entry(其余忽略)。 */
@Component
public class Decompressor {

    private static final long MAX_DECOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024; // 2GB 防解压炸弹

    public void decompress(FileKind kind, InputStream in, OutputStream out) throws Exception {
        switch (kind) {
            case GZIP -> copyBounded(new GZIPInputStream(in), out);
            case ZIP -> unzipFirst(in, out);
            case PLAIN -> copyBounded(in, out);
        }
    }

    private void unzipFirst(InputStream in, OutputStream out) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                // 只取首个普通文件 entry;entry 名不使用(规避 zip slip)
                copyBounded(zis, out);
                return;
            }
            throw new IllegalArgumentException("zip has no file entry");
        }
    }

    private void copyBounded(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > MAX_DECOMPRESSED_BYTES) {
                throw new IllegalStateException("decompressed size exceeds limit (zip bomb?)");
            }
            out.write(buf, 0, n);
        }
    }
}
```

- [ ] **Step 4:** `./mvnw test -Dtest=DecompressorTest -q` 确认 2 PASS。
- [ ] **Step 5: 提交**
```bash
git add src/main/java/com/example/filebatchprocessor/batch/preprocess/Decompressor.java src/test/java/com/example/filebatchprocessor/unit/batch/preprocess/DecompressorTest.java
git commit -m "feat(import): Decompressor(gzip/zip 首 entry, zip-slip 安全, 解压上限)"
```

---

## Task 4: PgpDecryptor(BouncyCastle)

**Files:**
- Create: `batch/preprocess/PgpDecryptor.java`、`BouncyCastlePgpDecryptor.java`
- Test: `unit/batch/preprocess/BouncyCastlePgpDecryptorTest.java`

> 实施者注意:BouncyCastle OpenPGP API(`org.bouncycastle.openpgp.*`)版本间略有差异。下面给出 1.78.x(bcpg-jdk18on)可用的解密实现与测试(测试内用 BC 生成密钥对 + 加密样本,无需外部 gpg 或二进制 fixture)。若某个类/方法签名在实际版本不符,据实微调(保持:用私钥+passphrase 解密 PGP 加密流 → 明文),不要改变接口契约。

- [ ] **Step 1: 写失败测试(测试内生成 RSA 密钥对 + 加密 → 解密断言)**

```java
package com.example.filebatchprocessor.unit.batch.preprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.batch.preprocess.BouncyCastlePgpDecryptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Date;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.junit.jupiter.api.Test;

class BouncyCastlePgpDecryptorTest {

    private static final char[] PASS = "test-pass".toCharArray();

    @Test
    void decryptsBackToPlaintext() throws Exception {
        java.security.Security.addProvider(new BouncyCastleProvider());

        // 1. 生成 RSA 密钥对 → PGPSecretKey
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        java.security.KeyPair jceKp = kpg.generateKeyPair();
        PGPKeyPair pgpKp = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, jceKp, new Date());
        PGPSecretKey secretKey = new PGPSecretKey(
                org.bouncycastle.openpgp.PGPSignature.DEFAULT_CERTIFICATION,
                pgpKp,
                "test@local",
                new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1),
                null,
                null,
                new JcaPGPContentSignerBuilder(pgpKp.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256),
                new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256)
                        .setProvider("BC")
                        .build(PASS));

        // 2. 用公钥加密一段明文
        byte[] plain = "id,name\n1,alice\n".getBytes();
        ByteArrayOutputStream encOut = new ByteArrayOutputStream();
        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                        .setWithIntegrityPacket(true)
                        .setSecureRandom(new SecureRandom())
                        .setProvider("BC"));
        encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(secretKey.getPublicKey()).setProvider("BC"));
        try (OutputStream armored = new ArmoredOutputStream(encOut);
                OutputStream encrypted = encGen.open(armored, new byte[4096])) {
            PGPLiteralDataGenerator lit = new PGPLiteralDataGenerator();
            try (OutputStream litOut =
                    lit.open(encrypted, PGPLiteralData.BINARY, "data", plain.length, new Date())) {
                litOut.write(plain);
            }
        }

        // 3. 解密(传 secretKey ring 字节 + passphrase)
        BouncyCastlePgpDecryptor decryptor = new BouncyCastlePgpDecryptor();
        ByteArrayOutputStream secretKeyRing = new ByteArrayOutputStream();
        secretKey.encode(secretKeyRing);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        decryptor.decrypt(
                new ByteArrayInputStream(encOut.toByteArray()),
                new ByteArrayInputStream(secretKeyRing.toByteArray()),
                PASS,
                out);

        assertEquals(new String(plain), out.toString());
    }
}
```

- [ ] **Step 2:** `./mvnw test -Dtest=BouncyCastlePgpDecryptorTest -q` 确认编译失败(类不存在)。

- [ ] **Step 3: 实现 PgpDecryptor 接口 + BC 实现**

`PgpDecryptor.java`:
```java
package com.example.filebatchprocessor.batch.preprocess;

import java.io.InputStream;
import java.io.OutputStream;

/** PGP 解密:加密流 + 私钥(secret key ring)+ passphrase → 明文。 */
public interface PgpDecryptor {
    void decrypt(InputStream encrypted, InputStream secretKeyRing, char[] passphrase, OutputStream out)
            throws Exception;
}
```

`BouncyCastlePgpDecryptor.java`(标准 BC OpenPGP 解密流程):
```java
package com.example.filebatchprocessor.batch.preprocess;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.Iterator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.util.io.Streams;
import org.springframework.stereotype.Component;

@Component
public class BouncyCastlePgpDecryptor implements PgpDecryptor {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public void decrypt(InputStream encrypted, InputStream secretKeyRing, char[] passphrase, OutputStream out)
            throws Exception {
        InputStream decoded = org.bouncycastle.openpgp.PGPUtil.getDecoderStream(encrypted);
        JcaPGPObjectFactory factory = new JcaPGPObjectFactory(decoded);

        Object first = factory.nextObject();
        PGPEncryptedDataList encList =
                (first instanceof PGPEncryptedDataList) ? (PGPEncryptedDataList) first
                        : (PGPEncryptedDataList) factory.nextObject();

        PGPSecretKeyRingCollection secretKeys =
                new PGPSecretKeyRingCollection(
                        org.bouncycastle.openpgp.PGPUtil.getDecoderStream(secretKeyRing),
                        new JcaKeyFingerprintCalculator());

        PGPPrivateKey privateKey = null;
        PGPPublicKeyEncryptedData encData = null;
        Iterator<?> it = encList.getEncryptedDataObjects();
        while (privateKey == null && it.hasNext()) {
            PGPPublicKeyEncryptedData ed = (PGPPublicKeyEncryptedData) it.next();
            PGPSecretKey secretKey = secretKeys.getSecretKey(ed.getKeyID());
            if (secretKey != null) {
                privateKey = secretKey.extractPrivateKey(
                        new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(passphrase));
                encData = ed;
            }
        }
        if (privateKey == null) {
            throw new IllegalArgumentException("no matching secret key for PGP message");
        }

        try (InputStream clear =
                encData.getDataStream(new JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(privateKey))) {
            JcaPGPObjectFactory plainFactory = new JcaPGPObjectFactory(clear);
            Object message = plainFactory.nextObject();
            if (message instanceof PGPCompressedData compressed) {
                plainFactory = new JcaPGPObjectFactory(compressed.getDataStream());
                message = plainFactory.nextObject();
            }
            if (message instanceof PGPLiteralData literal) {
                Streams.pipeAll(literal.getInputStream(), out);
            } else {
                throw new IllegalArgumentException("unexpected PGP message type: " + message.getClass());
            }
        }
    }
}
```

- [ ] **Step 4:** `./mvnw test -Dtest=BouncyCastlePgpDecryptorTest -q` 确认 PASS。若 BC API 签名不符,据实微调(常见:类包路径、`PGPUtil` vs `JcaPGPObjectFactory`),保持解密语义。

- [ ] **Step 5: 提交**
```bash
git add src/main/java/com/example/filebatchprocessor/batch/preprocess/PgpDecryptor.java src/main/java/com/example/filebatchprocessor/batch/preprocess/BouncyCastlePgpDecryptor.java src/test/java/com/example/filebatchprocessor/unit/batch/preprocess/BouncyCastlePgpDecryptorTest.java
git commit -m "feat(import): BouncyCastlePgpDecryptor(私钥+passphrase 解密)"
```

---

## Task 5: TempFileManager + FilePreprocessor(编排)

**Files:**
- Create: `batch/preprocess/TempFileManager.java`、`PreprocessResult.java`、`FilePreprocessor.java`
- Test: `unit/batch/preprocess/FilePreprocessorTest.java`

- [ ] **Step 1: 写失败测试(PLAIN 透传 + gzip 解压两场景,不涉 PGP 以免测试依赖密钥)**
```java
package com.example.filebatchprocessor.unit.batch.preprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.filebatchprocessor.batch.preprocess.Decompressor;
import com.example.filebatchprocessor.batch.preprocess.FilePreprocessor;
import com.example.filebatchprocessor.batch.preprocess.FileTypeDetector;
import com.example.filebatchprocessor.batch.preprocess.PreprocessResult;
import com.example.filebatchprocessor.batch.preprocess.TempFileManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePreprocessorTest {

    private FilePreprocessor newPreprocessor(Path tempDir) {
        TempFileManager tfm = new TempFileManager(tempDir.toString());
        return new FilePreprocessor(new FileTypeDetector(), new Decompressor(), null, tfm, "", "");
    }

    @Test
    void plainPassthroughReturnsOriginalNoTempFile(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("data.csv");
        Files.writeString(src, "id,name\n1,a\n");
        FilePreprocessor pre = newPreprocessor(dir.resolve("tmp"));
        PreprocessResult r = pre.prepare(src.toString(), null, null);
        assertEquals(src.toString(), r.plaintextPath().toString());
        assertNull(r.tempFileOrNull());
    }

    @Test
    void gzipDecompressedToTempFile(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("data.csv.gz");
        try (GZIPOutputStream g = new GZIPOutputStream(Files.newOutputStream(src))) {
            g.write("id,name\n2,b\n".getBytes());
        }
        FilePreprocessor pre = newPreprocessor(dir.resolve("tmp"));
        PreprocessResult r = pre.prepare(src.toString(), null, null);
        assertNotNull(r.tempFileOrNull());
        assertEquals("id,name\n2,b\n", Files.readString(r.plaintextPath()));
    }
}
```

- [ ] **Step 2:** `./mvnw test -Dtest=FilePreprocessorTest -q` 确认编译失败。

- [ ] **Step 3: 实现**

`PreprocessResult.java`:
```java
package com.example.filebatchprocessor.batch.preprocess;

import java.nio.file.Path;

/** 预处理结果:明文文件路径 + 需清理的临时文件(透传时为 null)。 */
public record PreprocessResult(Path plaintextPath, Path tempFileOrNull) {}
```

`TempFileManager.java`:
```java
package com.example.filebatchprocessor.batch.preprocess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 临时明文文件管理:专用目录 + UUID 名 + 显式删除。 */
@Slf4j
@Component
public class TempFileManager {

    private final Path baseDir;

    public TempFileManager(@Value("${batch.io.temp-dir:}") String tempDir) {
        this.baseDir = StringUtils.hasText(tempDir)
                ? Paths.get(tempDir)
                : Paths.get(System.getProperty("java.io.tmpdir"), "fbp-import-tmp");
    }

    public Path newTempFile(String suffix) throws IOException {
        Files.createDirectories(baseDir);
        return baseDir.resolve(UUID.randomUUID() + (suffix == null ? "" : suffix));
    }

    public void delete(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("failed to delete temp file {}", p, e);
        }
    }
}
```

`FilePreprocessor.java`:
```java
package com.example.filebatchprocessor.batch.preprocess;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 编排:detect → (PGP 解密) → (解压) → 落临时明文文件;PLAIN 透传原文件。 */
@Slf4j
@Component
public class FilePreprocessor {

    private final FileTypeDetector detector;
    private final Decompressor decompressor;
    private final PgpDecryptor pgpDecryptor; // 可能为 null(测试不涉 PGP 时)
    private final TempFileManager tempFileManager;
    private final String privateKeyPath;
    private final String passphrase;

    public FilePreprocessor(
            FileTypeDetector detector,
            Decompressor decompressor,
            PgpDecryptor pgpDecryptor,
            TempFileManager tempFileManager,
            @Value("${batch.pgp.private-key-path:}") String privateKeyPath,
            @Value("${batch.pgp.passphrase:}") String passphrase) {
        this.detector = detector;
        this.decompressor = decompressor;
        this.pgpDecryptor = pgpDecryptor;
        this.tempFileManager = tempFileManager;
        this.privateKeyPath = privateKeyPath;
        this.passphrase = passphrase;
    }

    public PreprocessResult prepare(String fileName, Boolean explicitEncrypted, String explicitCompression)
            throws Exception {
        DetectedType type = detector.detect(fileName, explicitEncrypted, explicitCompression);
        if (!type.encrypted() && type.compression() == FileKind.PLAIN) {
            return new PreprocessResult(Paths.get(fileName), null); // 透传
        }

        Path source = Paths.get(fileName);
        Path afterDecrypt = source;
        Path decryptTemp = null;
        if (type.encrypted()) {
            if (pgpDecryptor == null || !StringUtils.hasText(privateKeyPath)) {
                throw new IllegalStateException("file is encrypted but PGP decryptor/key not configured");
            }
            decryptTemp = tempFileManager.newTempFile(".dec");
            try (InputStream in = Files.newInputStream(source);
                    InputStream key = Files.newInputStream(Paths.get(privateKeyPath));
                    OutputStream out = Files.newOutputStream(decryptTemp)) {
                pgpDecryptor.decrypt(in, key, passphrase.toCharArray(), out);
            }
            afterDecrypt = decryptTemp;
        }

        Path plainTemp = tempFileManager.newTempFile(".plain");
        try (InputStream in = Files.newInputStream(afterDecrypt);
                OutputStream out = Files.newOutputStream(plainTemp)) {
            decompressor.decompress(type.compression(), in, out);
        } finally {
            tempFileManager.delete(decryptTemp); // 中间解密临时文件可立即删
        }
        return new PreprocessResult(plainTemp, plainTemp);
    }
}
```

- [ ] **Step 4:** `./mvnw test -Dtest=FilePreprocessorTest -q` 确认 2 PASS。
- [ ] **Step 5: 提交**
```bash
git add src/main/java/com/example/filebatchprocessor/batch/preprocess/PreprocessResult.java src/main/java/com/example/filebatchprocessor/batch/preprocess/TempFileManager.java src/main/java/com/example/filebatchprocessor/batch/preprocess/FilePreprocessor.java src/test/java/com/example/filebatchprocessor/unit/batch/preprocess/FilePreprocessorTest.java
git commit -m "feat(import): FilePreprocessor 编排(解密→解压→临时明文)+ TempFileManager"
```

---

## Task 6: 接入 importReader + 清理 listener + 参数 + 配置

**Files:**
- Modify: `params/ImportJobParams.java`、`batch/config/FileImportJobConfig.java`、`src/main/resources/application.yml`
- Test: `unit/params/ImportJobParamsEncryptionTest.java`

> 实施者先 Read `ImportJobParams.java` 与 `FileImportJobConfig.java`(importReader + importStep)全文。

- [ ] **Step 1: 写参数测试**
```java
package com.example.filebatchprocessor.unit.params;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.params.ImportJobParams;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportJobParamsEncryptionTest {

    @Test
    void parsesEncryptionParams() {
        ImportJobParams p = ImportJobParams.from(Map.of(
                "input.file.name", "x.csv.gz.pgp",
                "input.file.encrypted", "true",
                "input.file.compression", "gz"));
        assertEquals(Boolean.TRUE, p.getInputFileEncrypted());
        assertEquals("gz", p.getInputFileCompression());
    }
}
```

- [ ] **Step 2:** `./mvnw test -Dtest=ImportJobParamsEncryptionTest -q` 确认编译失败。

- [ ] **Step 3: 改 ImportJobParams** 加 `KEY_INPUT_FILE_ENCRYPTED="input.file.encrypted"`、`KEY_INPUT_FILE_COMPRESSION="input.file.compression"`;字段 `Boolean inputFileEncrypted`(用 acc 取 String 再 `Boolean.valueOf`,缺省 null)、`String inputFileCompression`(acc.getString,缺省 null);getter。注意 Boolean 缺省 null 表示"未显式指定"(交给后缀判定)。

- [ ] **Step 4: 改 FileImportJobConfig**
- importReader 注入 `FilePreprocessor filePreprocessor`;在 `if (StringUtils.hasText(params.getInputFileName()))` 分支里,先 `PreprocessResult pr = filePreprocessor.prepare(params.getInputFileName(), params.getInputFileEncrypted(), params.getInputFileCompression());` 然后 `resource = new FileSystemResource(pr.plaintextPath().toFile());`,并把 `pr.tempFileOrNull()` 暂存到一个 @StepScope 可见处用于清理。
  - 清理实现:最简——importStep 加一个 `StepExecutionListener`,afterStep 删临时文件。由于 reader 是 @StepScope、临时文件路径在 reader 创建时才知道,推荐:让 `FilePreprocessor.prepare` 把 temp 路径记录到一个 @StepScope 的持有者 bean(如 `ImportTempFileHolder`),listener 从该 holder 取并删。**新增 `batch/preprocess/ImportTempFileHolder.java`(@Component @StepScope,setter/getter 一个 Path 字段)**;importReader 把 pr.tempFileOrNull() set 进 holder;新增 listener bean `importTempCleanupListener`(StepExecutionListener,afterStep:holder.get() 非空则 tempFileManager.delete + holder.clear),挂到 importStep `.listener(importTempCleanupListener)`。
- importStep 在现有 `.listener(...)` 链上追加 `importTempCleanupListener`。

- [ ] **Step 5: 改 application.yml** 加:
```yaml
batch:
  io:
    temp-dir: ${BATCH_IO_TEMP_DIR:}        # 空=系统临时目录下 fbp-import-tmp
  pgp:
    private-key-path: ${BATCH_PGP_PRIVATE_KEY_PATH:}
    passphrase: ${BATCH_PGP_PASSPHRASE:}
```
(并入已有 batch: 顶层块,不要新建重复顶层键。)

- [ ] **Step 6: 跑** `./mvnw test -Dtest=ImportJobParamsEncryptionTest -q` PASS;`./mvnw -DskipTests test-compile` SUCCESS;`./mvnw test -Dtest='FileImportJobConfig*' -q` 行模式相关测试仍绿(importReader 改签名后既有测试需补 mock(FilePreprocessor)——若编译失败据实补,FilePreprocessor mock 默认 prepare 返回什么需 stub:让它返回透传 `new PreprocessResult(Paths.get(name), null)`,否则 NPE)。

- [ ] **Step 7: 提交**
```bash
git add src/main/java/com/example/filebatchprocessor/params/ImportJobParams.java src/main/java/com/example/filebatchprocessor/batch/config/FileImportJobConfig.java src/main/java/com/example/filebatchprocessor/batch/preprocess/ImportTempFileHolder.java src/main/resources/application.yml src/test/java/com/example/filebatchprocessor/unit/params/ImportJobParamsEncryptionTest.java
（如改了既有测试一并 add）
git commit -m "feat(import): importReader 接入预处理(解密/解压)+ 临时文件 step 清理 + 参数/配置"
```

---

## Task 7: 全量回归 + 端到端 + 文档

- [ ] **Step 1: 全量 unit-test** `./mvnw test -Punit-test 2>&1 | grep -E 'Tests run.*Skipped: [0-9]+$|BUILD' | tail -2` 要 0 失败 0 错误。
- [ ] **Step 2:(本机有 PG)集成** `./mvnw test -Pintegration-test 2>&1 | tail -3` 全绿。可选:加一个 IT 喂 `xxx.csv.gz`(不加密)走完整 import job 验证记录数正确(端到端解压)。
- [ ] **Step 3: 文档** 更新 `docs/user-guide/job-configuration-examples.md` 或新建 `docs/operations/encrypted-compressed-intake.md`:说明 input.file.encrypted/compression 参数、batch.pgp.private-key-path / BATCH_PGP_PASSPHRASE(env)、batch.io.temp-dir、临时明文落盘与 afterStep 清理、zip 只取首 entry、解压上限、私钥/passphrase 绝不入仓/日志。提交。

---

## Self-Review 结论

- **Spec 覆盖**:BC 依赖(T1)/ 类型判定(T2)/ 解压+zip-slip+炸弹上限(T3)/ PGP 解密(T4)/ 编排+临时文件+透传(T5)/ importReader 接入+清理 listener+参数+配置(T6)/ 回归+文档(T7)。分发侧加密按 spec **明确后置**,不在本计划。
- **占位符**:T4 的 BC 代码完整给出 + 标注版本微调点;T6 reader/JobConfig 改造给出 holder+listener 方案(实施者先 Read 原文件)。
- **类型一致**:`FileTypeDetector.detect(fileName, Boolean, String)→DetectedType(encrypted, compression)`、`Decompressor.decompress(FileKind, InputStream, OutputStream)`、`PgpDecryptor.decrypt(in, keyRing, char[], out)`、`FilePreprocessor.prepare(name, Boolean, String)→PreprocessResult(plaintextPath, tempFileOrNull)` 跨 Task 一致。
- **已知风险**:PGP 测试在测试内用 BC 生成密钥+加密(无外部 fixture);BC API 版本差异在 T4 标注微调;临时明文落盘安全(UUID 名 + afterStep 删 + 专用目录)在 T5/T6 落实。
