package su.nightexpress.nightcore.util.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.Placeholders;
import su.nightexpress.nightcore.util.Plugins;
import su.nightexpress.nightcore.util.text.NightMessage;
import su.nightexpress.nightcore.util.text.TextRoot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class Replacer {

    private final PlaceholderList<Object>     placeholders;
    private final List<UnaryOperator<String>> replacers;

    public Replacer() {
        this.placeholders = new PlaceholderList<>();
        this.replacers = new ArrayList<>();
    }

    @NotNull
    public static Replacer create() {
        return new Replacer();
    }

    public void clear() {
        this.placeholders.clear();
        this.replacers.clear();
    }

    @NotNull
    public List<UnaryOperator<String>> getReplacers() {
        List<UnaryOperator<String>> replacers = new ArrayList<>();
        replacers.add(this.placeholders.replacer(this));
        replacers.addAll(this.replacers);
        return replacers;
    }

    @NotNull
    @Deprecated
    public TextRoot getReplaced(@NotNull String source) {
        return this.getReplaced(NightMessage.from(source));
    }

    @NotNull
    @Deprecated
    public TextRoot getReplaced(@NotNull TextRoot source) {
//        TextRoot root = source.copy();
//        this.getReplacers().forEach(root::replace);
//        return root;
        return this.apply(source);
    }

    @NotNull
    public TextRoot apply(@NotNull TextRoot source) {
        TextRoot root = source.copy();
        this.getReplacers().forEach(root::replace);
        return root;
    }

    @NotNull
    @Deprecated
    public String getReplacedRaw(@NotNull String source) {
//        String result = source;
//        for (UnaryOperator<String> operator : this.getReplacers()) {
//            result = operator.apply(result);
//        }
//        return result;
        return this.apply(source);
    }

    @NotNull
    public String apply(@NotNull String source) {
        String result = source;
        for (UnaryOperator<String> operator : this.getReplacers()) {
            result = operator.apply(result);
        }
        return result;
    }

    @NotNull
    public List<String> apply(@NotNull List<String> list) {
//        List<String> replaced = new ArrayList<>();
//        list.forEach(line -> replaced.add(this.apply(line)));
//        return replaced;

        List<String> result = new ArrayList<>(list);
        for (UnaryOperator<String> operator : this.getReplacers()) {
            result = replaceList(result, operator);
        }

        return result;
    }

    @NotNull
    public ItemStack apply(@NotNull ItemStack itemStack) {
        ItemUtil.editMeta(itemStack, this::apply);
        return itemStack;
    }

    @NotNull
    public ItemMeta apply(@NotNull ItemMeta meta) {
        String displayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        List<String> lore = meta.getLore();

        if (displayName != null) {
            meta.setDisplayName(this.apply(displayName));
        }
        if (lore != null) {
            meta.setLore(this.apply(lore));
        }

        return meta;
    }

    @NotNull
    private static List<String> replaceList(@NotNull List<String> lore, @NotNull UnaryOperator<String> operator) {
        List<String> replaced = new ArrayList<>();
        for (String line : lore) {
            if (!line.isBlank()) {
                line = operator.apply(line);
                if (line.isBlank()) continue;

                replaced.addAll(Arrays.asList(line.split("\n")));
            }
            else replaced.add(line);
        }
        return replaced;
    }

    @NotNull
    public Replacer replacePlaceholderAPI(@NotNull Player player) {
        if (!Plugins.hasPlaceholderAPI()) return this;

        return this.replace(line -> PlaceholderAPI.setPlaceholders(player, line));
    }

    @NotNull
    public Replacer replace(@NotNull String key, @NotNull Consumer<List<String>> replacer) {
        List<String> list = new ArrayList<>();
        replacer.accept(list);

        return this.replace(key, list);
    }

    @NotNull
    public Replacer replace(@NotNull String key, @NotNull List<String> replacer) {
        return this.replace(key, () -> String.join(Placeholders.TAG_LINE_BREAK, replacer));
    }

    @NotNull
    public <T> Replacer replace(@NotNull T source, @NotNull PlaceholderList<T> placeholders) {
        return this.replace(placeholders.replacer(source));
    }

    @NotNull
    public Replacer replace(@NotNull String key, @NotNull Supplier<String> value) {
        this.placeholders.add(key, value);
        return this;
    }

    @NotNull
    public Replacer replace(@NotNull String key, @NotNull Object value) {
        this.placeholders.add(key, String.valueOf(value));
        return this;
    }

    @NotNull
    public Replacer replace(@NotNull UnaryOperator<String> replacer) {
        this.replacers.add(replacer);
        return this;
    }
}
