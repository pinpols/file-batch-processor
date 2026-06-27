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
