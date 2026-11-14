package dev.backpackarena.plugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Короткие помощники для сообщений (Adventure). */
public final class Msg {

    private static final Component PREFIX = Component.text("[Арена] ", NamedTextColor.GOLD);

    private Msg() {
    }

    public static void info(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.WHITE)));
    }

    public static void good(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GREEN)));
    }

    public static void bad(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.RED)));
    }

    public static void bar(Player to, String text, TextColor color) {
        to.sendActionBar(Component.text(text, color));
    }

    /** Текст без курсива (у названий и описаний предметов курсив включён по умолчанию). */
    public static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
