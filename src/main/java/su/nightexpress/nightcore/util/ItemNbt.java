package su.nightexpress.nightcore.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.nightcore.NightCore;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ItemNbt {

    private static final Class<?> ITEM_STACK_CLASS   = Reflex.getClass("net.minecraft.world.item", "ItemStack");
    private static final Class<?> COMPOUND_TAG_CLASS = Reflex.getClass("net.minecraft.nbt", "NBTTagCompound");
    private static final Class<?> NBT_IO_CLASS       = Reflex.getClass("net.minecraft.nbt", "NBTCompressedStreamTools");

    private static final Class<?> CRAFT_ITEM_STACK_CLASS = Reflex.getClass(Version.CRAFTBUKKIT_PACKAGE + ".inventory", "CraftItemStack");

    private static final Method CRAFT_ITEM_STACK_AS_NMS_COPY    = Reflex.getMethod(CRAFT_ITEM_STACK_CLASS, "asNMSCopy", ItemStack.class);
    private static final Method CRAFT_ITEM_STACK_AS_BUKKIT_COPY = Reflex.getMethod(CRAFT_ITEM_STACK_CLASS, "asBukkitCopy", ITEM_STACK_CLASS);

    private static final Method NBT_IO_WRITE = Reflex.getMethod(NBT_IO_CLASS, "a", COMPOUND_TAG_CLASS, DataOutput.class);
    private static final Method NBT_IO_READ  = Reflex.getMethod(NBT_IO_CLASS, "a", DataInput.class);

    // For 1.20.6+
    private static Method MINECRAFT_SERVER_REGISTRY_ACCESS;
    private static Method ITEM_STACK_PARSE_OPTIONAL;
    private static Method ITEM_STACK_SAVE_OPTIONAL;

    // For 1.20.4 and below.
    private static Constructor<?> NBT_TAG_COMPOUND_NEW;
    private static Method         NMS_ITEM_OF;
    private static Method         NMS_SAVE;

    static {
        if (Version.isAtLeast(Version.MC_1_20_6)) {
            Class<?> minecraftServerClass = Reflex.getClass("net.minecraft.server", "MinecraftServer");
            Class<?> holderLookupProviderClass = Reflex.getInnerClass("net.minecraft.core.HolderLookup", "Provider");

            MINECRAFT_SERVER_REGISTRY_ACCESS = Reflex.getMethod(minecraftServerClass, "bc");
            ITEM_STACK_PARSE_OPTIONAL = Reflex.getMethod(ITEM_STACK_CLASS, "a", holderLookupProviderClass, COMPOUND_TAG_CLASS);
            ITEM_STACK_SAVE_OPTIONAL  = Reflex.getMethod(ITEM_STACK_CLASS, "b", holderLookupProviderClass);
        }
        else {
            NBT_TAG_COMPOUND_NEW = Reflex.getConstructor(COMPOUND_TAG_CLASS);
            NMS_ITEM_OF          = Reflex.getMethod(ITEM_STACK_CLASS, "a", COMPOUND_TAG_CLASS);
            NMS_SAVE             = Reflex.getMethod(ITEM_STACK_CLASS, "b", COMPOUND_TAG_CLASS);
        }
    }

    private static boolean useRegistry;
    private static Object registryAccess;

    public static boolean setup(@NotNull NightCore core) {
        if (Version.isBehind(Version.MC_1_20_6)) return true;

        useRegistry = true;

        Class<?> craftServerClass = Reflex.getClass(Version.CRAFTBUKKIT_PACKAGE, "CraftServer");
        if (craftServerClass == null) {
            core.error("Could not find 'CraftServer' class in craftbukkit package: '" + Version.CRAFTBUKKIT_PACKAGE + "'.");
            return false;
        }

        Method getServer = Reflex.getMethod(craftServerClass, "getServer");
        if (getServer == null || MINECRAFT_SERVER_REGISTRY_ACCESS == null) {
            core.error("Could not find proper class(es) for ItemStack compression util.");
            return false;
        }

        try {
            Object craftServer = craftServerClass.cast(Bukkit.getServer());
            Object minecraftServer = getServer.invoke(craftServer);
            registryAccess = MINECRAFT_SERVER_REGISTRY_ACCESS.invoke(minecraftServer);
            return true;
        }
        catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    @Nullable
    public static String compress(@NotNull ItemStack item) {
        if (CRAFT_ITEM_STACK_AS_NMS_COPY == null || NBT_IO_WRITE == null) {
            return null;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(outputStream);
        try {
            Object compoundTag;
            Object itemStack = CRAFT_ITEM_STACK_AS_NMS_COPY.invoke(null, item);

            if (useRegistry) {
                if (ITEM_STACK_SAVE_OPTIONAL == null) return null;

                compoundTag = ITEM_STACK_SAVE_OPTIONAL.invoke(itemStack, registryAccess);
            }
            else {
                if (NBT_TAG_COMPOUND_NEW == null || NMS_SAVE == null) return null;

                compoundTag = NBT_TAG_COMPOUND_NEW.newInstance();
                NMS_SAVE.invoke(itemStack, compoundTag);
            }

            NBT_IO_WRITE.invoke(null, compoundTag, dataOutput);

            return new BigInteger(1, outputStream.toByteArray()).toString(32);
        }
        catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static ItemStack decompress(@NotNull String compressed) {
        if (NBT_IO_READ == null || CRAFT_ITEM_STACK_AS_BUKKIT_COPY == null) {
            throw new UnsupportedOperationException("Unsupported server version!");
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(new BigInteger(compressed, 32).toByteArray());
        try {
            Object compoundTag = NBT_IO_READ.invoke(null, new DataInputStream(inputStream));
            Object itemStack;

            if (useRegistry) {
                if (ITEM_STACK_PARSE_OPTIONAL == null) return null;

                itemStack = ITEM_STACK_PARSE_OPTIONAL.invoke(null, registryAccess, compoundTag);
            }
            else {
                if (NMS_ITEM_OF == null) return null;

                itemStack = NMS_ITEM_OF.invoke(null, compoundTag);
            }

            return (ItemStack) CRAFT_ITEM_STACK_AS_BUKKIT_COPY.invoke(null, itemStack);
        }
        catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @NotNull
    public static List<String> compress(@NotNull ItemStack[] items) {
        return compress(Arrays.asList(items));
    }

    @NotNull
    public static List<String> compress(@NotNull List<ItemStack> items) {
        return new ArrayList<>(items.stream().map(ItemNbt::compress).filter(Objects::nonNull).toList());
    }

    public static ItemStack[] decompress(@NotNull List<String> list) {
        List<ItemStack> items = list.stream().map(ItemNbt::decompress).filter(Objects::nonNull).toList();
        return items.toArray(new ItemStack[list.size()]);
    }
}
