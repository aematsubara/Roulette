package me.matsubara.roulette.util;

import com.cryptomorin.xseries.reflection.XReflection;
import com.cryptomorin.xseries.reflection.minecraft.MinecraftClassHandle;
import com.cryptomorin.xseries.reflection.minecraft.MinecraftMapping;
import com.cryptomorin.xseries.reflection.minecraft.MinecraftPackage;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// NOTE: Warning ("Usage of API marked for removal") disabled in IDEA.
public final class Reflection {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public static @Nullable Object getFieldValue(MethodHandle handle) {
        try {
            return handle.invoke();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public static MethodHandle getFieldGetter(Class<?> clazz, String name) {
        return getField(clazz, name, true);
    }

    public static MethodHandle getFieldSetter(Class<?> clazz, String name) {
        return getField(clazz, name, false);
    }

    private static @Nullable MethodHandle getField(@NotNull Class<?> clazz, String name, boolean isGetter) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);

            if (isGetter) return LOOKUP.unreflectGetter(field);
            return LOOKUP.unreflectSetter(field);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable MethodHandle getConstructor(@NotNull Class<?> clazz, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);

            return LOOKUP.unreflectConstructor(constructor);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable MethodHandle getPrivateConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            return MethodHandles.privateLookupIn(clazz, LOOKUP)
                    .findConstructor(clazz, MethodType.methodType(void.class, parameterTypes));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public static @Nullable MethodHandle getMethod(@NotNull Class<?> refc, String name, Class<?>... parameterTypes) {
        return getMethod(refc, name, true, parameterTypes);
    }

    public static @Nullable MethodHandle getMethod(@NotNull Class<?> refc, String name, boolean printStackTrace, Class<?>... parameterTypes) {
        try {
            Method method = refc.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return LOOKUP.unreflect(method);
        } catch (ReflectiveOperationException exception) {
            if (printStackTrace) exception.printStackTrace();
            return null;
        }
    }

    public static MethodHandle getMethod(Class<?> refc, String name, MethodType type) {
        return getMethod(refc, name, type, false, true);
    }

    public static @Nullable MethodHandle getMethod(Class<?> refc, String name, MethodType type, String... extraNames) {
        return getMethod(refc, name, type, false, true, extraNames);
    }

    public static @Nullable MethodHandle getMethod(Class<?> refc, String name, MethodType type, boolean isStatic, boolean printStackTrace, String... extraNames) {
        try {
            if (isStatic) return LOOKUP.findStatic(refc, name, type);

            Method method = refc.getMethod(name, type.parameterArray());
            if (!method.getReturnType().isAssignableFrom(type.returnType())) return null;

            return LOOKUP.unreflect(method);
        } catch (ReflectiveOperationException exception) {
            if (extraNames != null && extraNames.length > 0) {
                if (extraNames.length == 1) {
                    return getMethod(refc, extraNames[0], type, isStatic, printStackTrace);
                }
                for (String extra : extraNames) {
                    int index = ArrayUtils.indexOf(extraNames, extra);
                    String[] rest = ArrayUtils.remove(extraNames, index);
                    return getMethod(refc, extra, type, isStatic, printStackTrace, rest);
                }
            }
            if (printStackTrace) exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable Class<?> getUnversionedClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    public static @Nullable MethodHandle getField(Class<?> refc, Class<?> instc, String name, boolean isGetter, String... extraNames) {
        try {
            Field temp = getFieldHandleRaw(refc, instc, name);
            MethodHandle handle = temp != null ? (isGetter ? LOOKUP.unreflectGetter(temp) : LOOKUP.unreflectSetter(temp)) : null;

            if (handle != null) return handle;

            if (extraNames != null && extraNames.length > 0) {
                if (extraNames.length == 1) return getField(refc, instc, extraNames[0], isGetter);
                return getField(refc, instc, extraNames[0], isGetter, removeFirst(extraNames));
            }
        } catch (IllegalAccessException exception) {
            exception.printStackTrace();
        }

        return null;
    }

    private static @NotNull String[] removeFirst(@NotNull String[] array) {
        int length = array.length;

        String[] result = new String[length - 1];
        System.arraycopy(array, 1, result, 0, length - 1);

        return result;
    }

    @SuppressWarnings("unused")
    public static @Nullable Field getFieldRaw(Class<?> refc, Class<?> instc, String name, String... extraNames) {
        Field handle = getFieldHandleRaw(refc, instc, name);
        if (handle != null) return handle;

        if (extraNames != null && extraNames.length > 0) {
            if (extraNames.length == 1) return getFieldRaw(refc, instc, extraNames[0]);
            return getFieldRaw(refc, instc, extraNames[0], removeFirst(extraNames));
        }

        return null;
    }

    private static @Nullable Field getFieldHandleRaw(@NotNull Class<?> refc, Class<?> inscofc, String name) {
        for (Field field : refc.getDeclaredFields()) {
            field.setAccessible(true);

            if (!field.getName().equalsIgnoreCase(name)) continue;

            if (field.getType().isInstance(inscofc) || field.getType().isAssignableFrom(inscofc)) {
                return field;
            }
        }
        return null;
    }

    @SuppressWarnings("PatternValidation")
    public static @NotNull Class<?> getCraftClass(@Nullable String packageName, String className) {
        MinecraftClassHandle handle = XReflection.ofMinecraft();

        if (packageName != null) {
            handle.inPackage(MinecraftPackage.CB, packageName);
        } else {
            handle.inPackage(MinecraftPackage.CB);
        }

        return handle.named(className).unreflect();
    }

    @SuppressWarnings("PatternValidation")
    public static @NotNull Class<?> getNMSClass(@Nullable String packageName, String mojangName, String spigotName) {
        MinecraftClassHandle handle = XReflection.ofMinecraft();

        if (packageName != null) {
            handle.inPackage(MinecraftPackage.NMS, packageName);
        } else {
            handle.inPackage(MinecraftPackage.NMS);
        }

        return handle
                .map(MinecraftMapping.MOJANG, mojangName)
                .map(MinecraftMapping.SPIGOT, spigotName)
                .unreflect();
    }

    public static @NotNull Class<?> getNMSClass(@Nullable String packageName, String sameName) {
        return getNMSClass(packageName, sameName, sameName);
    }
}