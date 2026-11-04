package com.hunger.log;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppUpdatesTest {
    private String manifest(String name, String url) {
        return "{\"versionCode\":18,\"versionName\":\"" + name + "\",\"url\":\"" + url
                + "\",\"sha256\":\"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\"}";
    }
    @Test public void acceptsOnlyMatchingRepositoryReleaseAsset() throws Exception {
        AppUpdates.Release release = new AppUpdates.Release(manifest("2.3", AppUpdates.BASE + "download/huggy-v2.3/HUGGY-2.3.apk"));
        assertEquals(18, release.code);
        assertEquals("2.3", release.name);
        for (String url : new String[]{"https://example.com/malware.apk", AppUpdates.BASE + "download/huggy-v2.2/HUGGY-2.2.apk", "http://github.com/hojung1231/asdf/releases/download/huggy-v2.3/HUGGY-2.3.apk"}) {
            try { new AppUpdates.Release(manifest("2.3", url)); fail("Untrusted or mismatched URL accepted"); }
            catch (java.io.IOException expected) { }
        }
    }
    @Test public void verifiesApkBytesBeforeInstallation() throws Exception {
        java.io.File file = java.io.File.createTempFile("huggy-update", ".apk");
        try {
            String emptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            assertTrue(AppUpdates.matches(file, emptyHash));
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { out.write(1); }
            assertFalse(AppUpdates.matches(file, emptyHash));
        } finally { file.delete(); }
    }
}
