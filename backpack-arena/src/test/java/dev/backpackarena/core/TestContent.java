package dev.backpackarena.core;

import dev.backpackarena.core.content.ContentLoader;
import dev.backpackarena.core.content.GameContent;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Загружает контент из src/main/resources — те же файлы, что получит сервер. */
public final class TestContent {

    private static GameContent cached;

    private TestContent() {
    }

    public static synchronized GameContent get() {
        if (cached == null) {
            cached = ContentLoader.load(TestContent::read);
        }
        return cached;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(String name) {
        try (InputStream in = TestContent.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Нет ресурса " + name);
            }
            return new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
