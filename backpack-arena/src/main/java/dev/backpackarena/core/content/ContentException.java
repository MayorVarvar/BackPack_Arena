package dev.backpackarena.core.content;

import java.util.List;

/** Ошибки конфигурации контента — все сразу. */
public class ContentException extends RuntimeException {

    private final List<String> problems;

    public ContentException(List<String> problems) {
        super("Ошибки в конфигурации (" + problems.size() + "):\n - " + String.join("\n - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
