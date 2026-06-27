package com.example.filebatchprocessor.batch.preprocess;

import java.io.InputStream;
import java.io.OutputStream;

/** PGP 解密:加密流 + 私钥(secret key ring)+ passphrase → 明文。 */
public interface PgpDecryptor {
    void decrypt(InputStream encrypted, InputStream secretKeyRing, char[] passphrase, OutputStream out)
            throws Exception;
}
