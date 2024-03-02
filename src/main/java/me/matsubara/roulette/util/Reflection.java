package me.matsubara.roulette.util;

import com.cryptomorin.xseries.ReflectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Reflection {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

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

    public static @Nullable Object getFieldValue(MethodHandle handle) {
        try {
            return handle.invoke();
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static MethodHandle getConstructor(Class<?> refc, Class<?>... types) {
        return getConstructor(refc, true, types);
    }

    public static @Nullable MethodHandle getConstructor(@NotNull Class<?> refc, boolean printStackTrace, Class<?>... types) {
        try {
            Constructor<?> constructor = refc.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return LOOKUP.unreflectConstructor(constructor);
        } catch (ReflectiveOperationException exception) {
            if (printStackTrace) exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable MethodHandle getMethod(@NotNull Class<?> refc, String name, Class<?>... parameterTypes) {
        try {
            Method method = refc.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return LOOKUP.unreflect(method);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
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
            if (ReflectionUtils.MINOR_NUMBER > 17) {
                Method method = refc.getMethod(name, type.parameterArray());
                if (!method.getReturnType().isAssignableFrom(type.returnType())) {
                    throw new NoSuchMethodException();
                }
                return LOOKUP.unreflect(method);
            }
            return LOOKUP.findVirtual(refc, name, type);
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
            exception.printStackTrace();
            return null;
        }
    }
}