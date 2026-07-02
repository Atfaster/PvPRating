package dev.pvprating.compats;

import static dev.pvprating.configs.Config.*;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public class TownyCompat {

    public static String ratingSkipReason(ServerPlayer killer, ServerPlayer target) throws ReflectiveOperationException {
        Class<?> bukkitClass = findClass("org.bukkit.Bukkit");
        if (bukkitClass == null) return null;

        Object pluginManager = invokeNoArg(bukkitClass, null, "getPluginManager");
        Object townyPlugin = invoke(pluginManager, "getPlugin", "Towny");
        if (townyPlugin == null || !invokeBooleanNoArg(townyPlugin, "isEnabled")) return null;

        Object bukkitKiller = invoke(bukkitClass, null, "getPlayer", killer.getUUID());
        Object bukkitTarget = invoke(bukkitClass, null, "getPlayer", target.getUUID());
        if (bukkitKiller == null || bukkitTarget == null) return null;

        ClassLoader townyLoader = townyPlugin.getClass().getClassLoader();
        Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, townyLoader);
        Object towny = invokeNoArg(townyApiClass, null, "getInstance");

        Object killerResident = invoke(towny, "getResident", bukkitKiller);
        Object targetResident = invoke(towny, "getResident", bukkitTarget);
        if (killerResident == null || targetResident == null) return null;

        if (TownyDisableRatingSameTown.get() && sameTown(killerResident, targetResident)) {
            return "message.pvprating.reason.towny.same_town";
        }

        if (TownyDisableRatingSameNation.get() && sameNation(killerResident, targetResident)) {
            return "message.pvprating.reason.towny.same_nation";
        }

        if (TownyDisableRatingAlliedNations.get() && alliedNations(killerResident, targetResident)) {
            return "message.pvprating.reason.towny.allied_nations";
        }

        if (TownyDisableRatingMutualFriends.get() && mutualFriends(killerResident, targetResident)) {
            return "message.pvprating.reason.towny.mutual_friends";
        }

        return null;
    }

    private static boolean sameTown(Object killer, Object target) throws ReflectiveOperationException {
        Object killerTown = invokeNoArg(killer, "getTownOrNull");
        Object targetTown = invokeNoArg(target, "getTownOrNull");
        return killerTown != null && killerTown.equals(targetTown);
    }

    private static boolean sameNation(Object killer, Object target) throws ReflectiveOperationException {
        Object killerNation = invokeNoArg(killer, "getNationOrNull");
        Object targetNation = invokeNoArg(target, "getNationOrNull");
        return killerNation != null && killerNation.equals(targetNation);
    }

    private static boolean alliedNations(Object killer, Object target) throws ReflectiveOperationException {
        Object killerNation = invokeNoArg(killer, "getNationOrNull");
        Object targetNation = invokeNoArg(target, "getNationOrNull");
        return killerNation != null && targetNation != null && invokeBoolean(killerNation, "hasMutualAlly", targetNation);
    }

    private static boolean mutualFriends(Object killer, Object target) throws ReflectiveOperationException {
        return invokeBoolean(killer, "hasFriend", target) && invokeBoolean(target, "hasFriend", killer);
    }

    private static Class<?> findClass(String className) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader modLoader = TownyCompat.class.getClassLoader();

        Class<?> found = findClass(className, contextLoader);
        if (found != null) return found;

        return findClass(className, modLoader);
    }

    private static Class<?> findClass(String className, ClassLoader classLoader) {
        if (classLoader == null) return null;

        try {
            return Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        return invokeNoArg(target.getClass(), target, methodName);
    }

    private static Object invokeNoArg(Class<?> targetClass, Object target, String methodName) throws ReflectiveOperationException {
        Method method = targetClass.getMethod(methodName);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String methodName, Object argument) throws ReflectiveOperationException {
        return invoke(target.getClass(), target, methodName, argument);
    }

    private static Object invoke(Class<?> targetClass, Object target, String methodName, Object argument) throws ReflectiveOperationException {
        Method method = findCompatibleMethod(targetClass, methodName, argument);
        return method.invoke(target, argument);
    }

    private static boolean invokeBoolean(Object target, String methodName, Object argument) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(invoke(target, methodName, argument));
    }

    private static boolean invokeBooleanNoArg(Object target, String methodName) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(invokeNoArg(target, methodName));
    }

    private static Method findCompatibleMethod(Class<?> targetClass, String methodName, Object argument) throws NoSuchMethodException {
        Class<?> argumentClass = argument.getClass();

        for (Method method : targetClass.getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getName().equals(methodName)
                    && parameterTypes.length == 1
                    && parameterTypes[0].isAssignableFrom(argumentClass)) {
                return method;
            }
        }

        throw new NoSuchMethodException(targetClass.getName() + "." + methodName + "(" + argumentClass.getName() + ")");
    }
}
