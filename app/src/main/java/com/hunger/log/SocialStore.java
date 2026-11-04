package com.hunger.log;

import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

final class SocialStore {
    private static SecretKey key() throws Exception {
        java.security.KeyStore store = java.security.KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias("huggy-session")) return (SecretKey) store.getKey("huggy-session", null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder("huggy-session",
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    static synchronized JSONObject read(Context context, String name) throws Exception {
        String raw = context.getSharedPreferences("social", 0).getString(name, "");
        if (raw.isEmpty()) return new JSONObject();
        JSONObject envelope = new JSONObject(raw);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128,
                Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)));
        byte[] data = cipher.doFinal(Base64.decode(envelope.getString("data"), Base64.NO_WRAP));
        return new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8));
    }

    static synchronized void write(Context context, String name, JSONObject value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject().put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
        if (!context.getSharedPreferences("social", 0).edit().putString(name, envelope.toString()).commit())
            throw new java.io.IOException("계정 정보를 저장하지 못했습니다.");
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences("social", 0).edit().clear().commit();
    }
}
