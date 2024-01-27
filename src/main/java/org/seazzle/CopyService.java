package org.seazzle;

import jakarta.annotation.Nullable;
import org.hibernate.Hibernate;
import org.seazzle.base.BaseEntityWithGeneratedId;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CopyService {

    public static <T> T copy(T old) {
        return copy(old, null);
    }

    public static <T> T copy(T old, @Nullable CopyContext copyContext) {
        try {
            //returns the original object without copying
            // if the object does not implement CopySupport marker interface
            if (!(old instanceof CopySupport)) {
                return old;
            }

            @SuppressWarnings("unchecked")
            T copy = createNewInstanceOfSameType((T) Hibernate.unproxy(old));

            // resolves all fields that should be copied.
            List<Field> allFields = getAllFields(new ArrayList<>(), old.getClass()).stream()
                    .filter(filterFieldsFromClass(BaseEntityWithGeneratedId.class))
                    .toList();

            for (Field oldField : allFields) {
                Field newField = getAllFields(new ArrayList<>(), copy.getClass()).stream()
                        .filter(copyField -> copyField.getName().equals(oldField.getName()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Field " + oldField.getName() + " is not available in copy. Available Fields " + getAllFields(new ArrayList<>(), copy.getClass()).stream().map(Field::getName).collect(Collectors.joining(", "))));

                oldField.setAccessible(true);
                newField.setAccessible(true);

                // handle null values
                if (oldField.get(old) == null) {
                    newField.set(copy, null);
                    continue;
                }

                if (copyContext != null) {
                    try {
                        //restore a bidirectional relationship with referential integrity
                        if (copyContext.getOriginParentObject().equals(oldField.get(old))) {
                            newField.set(copy, copyContext.getCopyOfParentObject());
                            continue;
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Error restoring bidirectional relationship for field" + oldField.getName(), e);
                    }
                }

                if (isMap(oldField)) {
                    try {
                        Map<?, ?> newMapInstance = copyMap(old, copy, oldField);
                        newField.set(copy, newMapInstance);
                        continue;
                    } catch (Exception e) {
                        throw new RuntimeException("Error cloning Map Instance " + oldField.getName(), e);
                    }
                }

                if (isCollection(oldField)) {
                    try {
                        Collection<?> newCollectionInstance = copyCollection(old, copy, oldField);
                        newField.set(copy, newCollectionInstance);
                        continue;
                    } catch (Exception e) {
                        throw new RuntimeException("Error while cloning collection for field " + oldField.getName(), e);
                    }
                }

                if (isOtherCopyableEntity(oldField)) {
                    try {
                        newField.set(copy, CopyService.copy((CopySupport) Hibernate.unproxy(oldField.get(old)), new CopyContext(old, old.getClass(), copy)));
                        continue;

                    } catch (Exception e) {
                        throw new RuntimeException("Error while cloning other copyable Entity " + oldField.getName(), e);
                    }
                }

                // its any other non-copyable entity, this could be a primitive, an enum, or an object which does not implement Copyable
                try {
                    newField.set(copy, oldField.get(old));
                } catch (Exception e) {
                    throw new RuntimeException("Error while setting field " + oldField.getName() + " on copy " + copy, e);
                }

            }
            return copy;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @SuppressWarnings("rawtypes")
    private static <T> Map copyMap(T old, T copy, Field oldField) throws IllegalAccessException {
        Map newMapInstance = getNewMapInstance(oldField, old);
        Map oldMapInstance = (Map) oldField.get(old);
        oldMapInstance.keySet().forEach(oldKey -> {
            Object newKey = CopyService.copy(oldKey, new CopyContext(old, old.getClass(), copy));
            Object oldValue = oldMapInstance.get(oldKey);
            Object newValue = CopyService.copy(oldValue, new CopyContext(old, old.getClass(), copy));
            newMapInstance.put(newKey, newValue);
        });
        return newMapInstance;
    }

    @SuppressWarnings("rawtypes")
    private static <T> Collection copyCollection(T old, T copy, Field oldField) throws IllegalAccessException {
        Collection newCollectionInstance = getCollectionInstance(oldField, old);
        Collection oldCollectionInstance = (Collection<?>) oldField.get(old);
        for (Object o : oldCollectionInstance) {
            if (o instanceof CopySupport) {
                newCollectionInstance.add(CopyService.copy((CopySupport) o, new CopyContext(old, old.getClass(), copy)));
            } else {
                newCollectionInstance.add(o);
            }
        }
        return newCollectionInstance;
    }

    public static Collection<?> getCollectionInstance(Field field, Object o) throws IllegalAccessException {
        Object value = field.get(o);
        if (value == null) {
            return null;
        } else {
            if (List.class.isAssignableFrom(field.getType())) {
                return new ArrayList<>();
            }
            if (Set.class.isAssignableFrom(field.getType())) {
                return new HashSet<>();
            } else {
                throw new IllegalArgumentException("Unsupported Collection Type " + field.getType().getName() + " expected " + List.class.getName() + " or " + Set.class.getName());
            }
        }
    }

    public static Map<?, ?> getNewMapInstance(Field field, Object o) throws IllegalAccessException {
        Object value = field.get(o);
        if (value == null) {
            return null;
        } else if (Map.class.isAssignableFrom(field.getType())) {
            return new HashMap<>();
        } else {
            throw new IllegalAccessException("Unsupported type in " + field.getName() + " expected a type of " + Map.class.getName() + " but was " + field.getType().getName());
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T createNewInstanceOfSameType(T old) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Constructor<?> ctor = old.getClass().getDeclaredConstructor();
        ctor.setAccessible(true);
        return (T) ctor.newInstance();
    }

    public static List<Field> getAllFields(List<Field> fields, Class<?> type) {
        fields.addAll(Arrays.asList(type.getDeclaredFields()));

        if (type.getSuperclass() != null) {
            getAllFields(fields, type.getSuperclass());
        }

        return fields;
    }

    public static boolean isCollection(Field field) {
        return Collection.class.isAssignableFrom(field.getType());
    }

    public static boolean isMap(Field field) {
        return Map.class.isAssignableFrom(field.getType());
    }

    public static boolean isOtherCopyableEntity(Field field) {
        return CopySupport.class.isAssignableFrom(field.getType());
    }

    public static Predicate<Field> filterFieldsFromClass(Class<?> clazz) {
        return field -> {
            List<Field> allFields = getAllFields(new ArrayList<>(), clazz);
            return !allFields.contains(field);
        };
    }
}
