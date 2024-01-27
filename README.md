# Entity Copy Support

This small utility allows to deep-copy a nested data structure of persisted Hibernate Entities.

## The Problem

Copying a nested data structure of persisted hibernate entities could be a harder task than it sounds.
Basically the problem is that you must not copy attributes which are managed by hibernate (like the ID for example), and you want to keep referential integrity for bidirectional relationships.

This does not allow you to just serialize and deserialize your nested data structure in order to get your copy.

So this limits you to the possibility of:
* instantiate each entity manually, and map all attributes manually except the Hiberante managed ones, or
* Detach the clone from the EntityManager and reset the id (and maybe other fields managed by Hibernate, createdTimestamp etc.)

```

MyEntity clone = entityManager.find(MyEntity.class, ID);
entityManager.detach(clone);
clone.setId(null);
entityManager.persist(clone);

```

However both solutions does not really fit for large nested data structures. 

## The Solution

```
var clone = CopyService.copy(oldEntity);
```

The CopyService creates a deep-copy of all entities which implement `org.seazzle.CopySupport` by reflection.
Entities which do not implement this interface are *NOT* copied, and a relation to the original entity is built.

In order to prevent the CopyService to copy also Hibernate-managed attributes it makes sense to pack them into a common class like `org.seazzle.base.BaseEntityWithGeneratedId`
```
            // resolves all fields that should be copied.
            List<Field> allFields = getAllFields(new ArrayList<>(), old.getClass()).stream()
                    .filter(filterFieldsFromClass(BaseEntityWithGeneratedId.class))
                    .toList();
```


