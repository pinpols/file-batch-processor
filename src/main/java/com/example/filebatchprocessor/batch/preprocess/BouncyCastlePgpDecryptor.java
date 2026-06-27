package com.example.filebatchprocessor.batch.preprocess;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.Iterator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPLiteralData;
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
