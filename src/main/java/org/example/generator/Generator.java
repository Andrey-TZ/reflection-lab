package org.example.generator;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Generator {

    private final static int MAX_DEPTH = 3;
    private final static Random RANDOM = new Random(52);
    private static boolean allow_nullable = true;

    public static void setSeed(long seed) {
        RANDOM.setSeed(seed);
    }

    public static int getMaxDepth() {
        return MAX_DEPTH;
    }

    private record GenerationContext(int depth, Generatable meta) {
        int size() {
            return meta != null ? meta.size() : 3;
        }

        int min() {
            return meta != null ? meta.min() : 1;
        }

        int max() {
            return meta != null ? meta.max() : 1000;
        }

        Class<?>[] implementations() {
            return meta != null ? meta.implementations() : new Class<?>[0];
        }

        GenerationContext nextDepth(Generatable newMeta) {
            return new GenerationContext(depth + 1, newMeta);
        }
    }

    public Object generateValueOfType(Class<?> clazz) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        int randomConstructorIndex = new Random().nextInt(constructors.length);
        Constructor<?> randomConstructor = constructors[randomConstructorIndex];
        return randomConstructor.newInstance(111);
    }

    public static <T> T generate(Class<T> clazz) {
        Generatable classMeta = clazz.getAnnotation(Generatable.class);
        return (T) generateByType(clazz, new GenerationContext(0, classMeta));
    }

    private static Object generateByType(Type type, GenerationContext ctx) {
        if (ctx.depth > MAX_DEPTH) {
            if (allow_nullable) return null;
            throw new IllegalStateException("Превышена максимальная глубина генерации!");
        }

        if (type instanceof ParameterizedType parameterizedType) {
            Class<?> rawClass = parameterizedType.getClass();
            Type[] argumentsTypes = parameterizedType.getActualTypeArguments();
            return generateParametrized(rawClass, argumentsTypes, ctx);
        }

        if (type instanceof Class<?> clazz) return generateClass(clazz, ctx);

        throw new IllegalArgumentException("Тип " + type + " не поддерживается");
    }

    private static Object generateClass(Class<?> clazz, GenerationContext ctx) {
        var random = ThreadLocalRandom.current();
        int min = ctx.min();
        int max = ctx.max();

        if (clazz == int.class || clazz == Integer.class) return random.nextInt(min, max);
        if (clazz == short.class || clazz == Short.class) return random.nextInt(min, max);
        if (clazz == byte.class || clazz == Byte.class) return random.nextInt(-128, 127);
        if (clazz == long.class || clazz == Long.class) return random.nextLong(min, max);
        if (clazz == double.class || clazz == Double.class) return random.nextDouble(min, max);
        if (clazz == float.class || clazz == Float.class) return random.nextFloat(min, max);
        if (clazz == boolean.class || clazz == Boolean.class) return random.nextBoolean();
        if (clazz == char.class || clazz == Character.class) return random.nextInt('a', 'z' + 1);

        if (clazz == String.class) {
            int length = Math.max(1, ctx.size());
            var randomString = new StringBuilder();
            for (int i = 0; i < length; i++) {
                randomString.append((char) random.nextInt('a', 'z' + 1));
            }
            return randomString.toString();
        }

        if (clazz.isEnum()) {
            Object[] constants = clazz.getEnumConstants();
            if (constants.length == 0) throw new IllegalArgumentException("provided enum must be not empty");
            return constants[random.nextInt(constants.length)];
        }

        if (clazz.isArray()) {
            var componentType = clazz.getComponentType();
            Object array = Array.newInstance(componentType, ctx.size());
            for (int i = 0; i < ctx.size(); i++) {
                Array.set(array, i, generateByType(componentType, ctx.nextDepth(componentType.getAnnotation(Generatable.class))));
            }
            return array;
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            return generateParametrized(clazz, new Type[]{}, ctx);
        }

        if (Map.class.isAssignableFrom(clazz)) {
            return generateParametrized(clazz, new Type[]{}, ctx);
        }

        Generatable typeAnnotation = clazz.getAnnotation(Generatable.class);
        if (typeAnnotation == null) {
            if (ctx.depth() == 0) {
                throw new IllegalArgumentException("Класс " + clazz.getName() + " не помечен аннотацией @Generatable!");
            }
            return null;
        }

        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            generateAbstractOrInterface(clazz, typeAnnotation, ctx);
        }

        return instancePojo(clazz, ctx);
    }

    private static Object generateAbstractOrInterface(Class<?> clazz, Generatable typeAnnotation, GenerationContext ctx) {

        Class<?>[] candidates = ctx.implementations();

        if (candidates.length == 0) {
            candidates = typeAnnotation.implementations();
        }

        if (candidates.length == 0 && clazz.isSealed()) {
            candidates = Arrays.stream(clazz.getPermittedSubclasses())
                    .filter((c) -> c.isAnnotationPresent(Generatable.class))
                    .toArray(Class<?>[]::new);
        }

        if (candidates.length > 0) {
            Class<?> chosen = candidates[RANDOM.nextInt(candidates.length)];
            if (!clazz.isAssignableFrom(chosen)) {
                throw new IllegalArgumentException(chosen.getName() + " не является наследником " + clazz.getName());
            }
            if (!chosen.isAnnotationPresent(Generatable.class)) {
                throw new IllegalArgumentException("Реализация " + chosen.getName() + " должна быть помечена @Generatable");
            }
            return generateClass(chosen, new GenerationContext(ctx.depth, chosen.getAnnotation(Generatable.class)));
        }

        if (clazz.isInterface()) {
            return createInterfaceProxy(clazz, ctx);
        }

        throw new IllegalArgumentException(
                "Не удалось сгенерировать абстрактный класс " + clazz.getName() +
                        ": укажите наследников в @Generatable(implementations = ...)"
        );
    }

    /**
     * Создание прокси для интерфейса без указания наследников
     * При вызове метода возвращает случайное значение подходящего типа
     */
    private static Object createInterfaceProxy(Class<?> iface, GenerationContext ctx) {
        Map<Method, Object> cache = new HashMap<>();
        return Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                ((proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    switch (method.getName()) {
                        case "toString" -> {
                            return "Proxy[" + iface.getSimpleName() + "]";
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "equals" -> {
                            return proxy == args[0];
                        }
                    }
                    if (method.getReturnType() == void.class) return null;
                    return cache.computeIfAbsent(method, m ->
                            generateByType(m.getGenericReturnType(), ctx.nextDepth(null))
                    );
                })
        );
    }

    private static Object generateParametrized(Class<?> rawClass, Type[] typeArgs, GenerationContext ctx) {
        int size = ctx.size();
        if (Collection.class.isAssignableFrom(rawClass)) {
            Collection<Object> collection = Set.class.isAssignableFrom(rawClass) ? new HashSet<>() : new ArrayList<>();
            Type elementType = typeArgs.length > 0 ? typeArgs[0] : String.class;
            for (int i = 0; i < size; i++) {
                collection.add(generateByType(elementType, ctx.nextDepth(null)));
            }
            return collection;
        }

        if (Map.class.isAssignableFrom(rawClass)) {
            Map<Object, Object> map = new HashMap<>();
            Type keyType = typeArgs.length > 0 ? typeArgs[0] : String.class;
            Type valueType = typeArgs.length > 1 ? typeArgs[1] : String.class;
            for (int i = 0; i < size; i++) {
                map.put(
                        generateByType(keyType, ctx.nextDepth(null)),
                        generateByType(valueType, ctx.nextDepth(null))
                );
            }
            return map;
        }

        throw new IllegalArgumentException("Parametrized type " + rawClass + " currently is not supported");
    }

    private static Object instancePojo(Class<?> clazz, GenerationContext ctx) {
        try {
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();

            Constructor<?>[] targetConstructors = Arrays.stream(constructors)
                    .filter(constructor -> constructor.isAnnotationPresent(Generatable.class))
                    .toArray(Constructor<?>[]::new);
            Constructor<?>[] primalConstructors = Arrays.stream(targetConstructors)
                    .filter(constructor -> constructor.isAnnotationPresent(Generatable.class) && constructor.getAnnotation(Generatable.class).primalConstructor())
                    .toArray(Constructor<?>[]::new);
            Constructor<?> targetConstructor;
            if (primalConstructors.length > 1) {
                throw new IllegalArgumentException("Primal constructor может быть только один");
            } else if (primalConstructors.length == 1) {
                targetConstructor = primalConstructors[0];
            } else {
                targetConstructor = targetConstructors.length > 1 ? targetConstructors[RANDOM.nextInt(targetConstructors.length)] : targetConstructors[0];
            }

            Type[] paramTypes = targetConstructor.getGenericParameterTypes();
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = generateByType(paramTypes[i], ctx.nextDepth(null));
            }
            Object instance = targetConstructor.newInstance(args);

            if (!clazz.isRecord()) {
                fulfillFields(instance, clazz, ctx);
            }

            return instance;
        } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
            throw new RuntimeException("Ошибка при генерации класса " + clazz.getName(), e);
        }
    }

    private static void fulfillFields(Object instance, Class<?> clazz, GenerationContext ctx) throws IllegalAccessException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) continue;

                field.setAccessible(true);
                Generatable fieldMeta = field.getAnnotation(Generatable.class);
                if (fieldMeta != null && fieldMeta.ignore()) continue;
                if (fieldMeta == null && field.get(instance) != null) continue;

                Object value = generateByType(field.getType(), ctx.nextDepth(fieldMeta));
                field.set(instance, value);
            }
            current = current.getSuperclass();
        }
    }


}
