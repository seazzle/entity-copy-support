package org.seazzle;

import org.junit.jupiter.api.Test;
import org.seazzle.base.BaseEntity;
import org.seazzle.base.BaseEntityWithGeneratedId;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CopyServiceTest {


    @Test
    void shouldCopyASingleEntity() {
        ParentEntity parentEntity = new ParentEntity();
        parentEntity.setId(UUID.randomUUID());
        parentEntity.setIntegerField(Integer.valueOf(2));
        parentEntity.setIntField(3);
        parentEntity.setStringField("parent");

        ParentEntity copy = CopyService.copy(parentEntity);

        assertNotEquals(copy, parentEntity);
        assertNull(copy.getId());
        assertEquals(3, copy.getIntField());
        assertEquals(2, copy.getIntegerField());
        assertEquals("parent", copy.getStringField());
        assertEquals(0, copy.getChildren().size());
        assertEquals(0, parentEntity.getChildren().size());

        parentEntity.getChildren().add(new ChildEntity());
        assertEquals(0, copy.getChildren().size());
        assertEquals(1, parentEntity.getChildren().size());
    }

    @Test
    void shouldCopyABidirectionalOneToManyRelationShip() {
        ParentEntity parentEntity = new ParentEntity();
        ChildEntity firstChild = new ChildEntity();
        firstChild.setStringField("firstChild");
        firstChild.setParentEntity(parentEntity);
        ChildEntity secondChild = new ChildEntity();
        secondChild.setStringField("secondChild");
        secondChild.setParentEntity(parentEntity);
        parentEntity.setChildren(List.of(firstChild, secondChild));

        ParentEntity copy = CopyService.copy(parentEntity);

        assertNotEquals(copy, parentEntity);
        assertNotEquals(copy.getChildren(), parentEntity.getChildren());
        ChildEntity copyOfFirstChild = copy.getChildren().stream().filter(c -> c.getStringField().equals("firstChild")).findAny().orElseThrow();
        assertEquals(copyOfFirstChild.getParentEntity(), copy);
        assertEquals(firstChild.getParentEntity(), parentEntity);
        ChildEntity copyOfSecondChild = copy.getChildren().stream().filter(c -> c.getStringField().equals("secondChild")).findAny().orElseThrow();
        assertEquals(copyOfSecondChild.getParentEntity(), copy);
        assertEquals(secondChild.getParentEntity(), parentEntity);
    }

    @Test
    void shouldCopyAOneToManyRelationShip() {
        ParentEntity parentEntity = new ParentEntity();
        ChildEntity firstChild = new ChildEntity();
        firstChild.setId(UUID.randomUUID());
        firstChild.setBooleanField(Boolean.FALSE);
        firstChild.setBoolField(true);
        firstChild.setStringField("firstChild");
        firstChild.setParentEntity(parentEntity);
        ChildEntity secondChild = new ChildEntity();
        secondChild.setId(UUID.randomUUID());
        secondChild.setBoolField(Boolean.TRUE);
        secondChild.setBoolField(true);
        secondChild.setStringField("secondChild");
        secondChild.setParentEntity(parentEntity);
        parentEntity.setChildren(List.of(firstChild, secondChild));

        ParentEntity copy = CopyService.copy(parentEntity);

        ChildEntity copyOfFirstChild = copy.getChildren().stream().filter(c -> c.getStringField().equals("firstChild")).findAny().orElseThrow();
        assertNull(copyOfFirstChild.getId());
        assertEquals(Boolean.FALSE, firstChild.getBooleanField());
        ChildEntity copyOfSecondChild = copy.getChildren().stream().filter(c -> c.getStringField().equals("secondChild")).findAny().orElseThrow();
        assertEquals(copyOfSecondChild.getParentEntity(), copy);
        assertNull(copyOfSecondChild.getId());
    }

    @Test
    void shouldCopyMap() {
        ParentEntity parentEntity = new ParentEntity();
        parentEntity.setId(UUID.randomUUID());
        MapValue testOneValue = new MapValue();
        testOneValue.setStringValue("testOne");
        testOneValue.setId(UUID.randomUUID());
        MapValue testTwoValue = new MapValue();
        testTwoValue.setId(UUID.randomUUID());
        testTwoValue.setStringValue("testTwo");
        parentEntity.getKeyValues().put(TestEnum.TEST_1, testOneValue);
        parentEntity.getKeyValues().put(TestEnum.TEST_2, testTwoValue);

        ParentEntity copy = CopyService.copy(parentEntity);

        assertNotEquals(parentEntity, copy);
        assertNull(copy.getKeyValues().get(TestEnum.TEST_1).getId());
        assertNull(copy.getKeyValues().get(TestEnum.TEST_2).getId());
    }

    @Test
    void shouldCopyAOneToOneRelationship() {
        ParentEntity parentEntity = new ParentEntity();
        parentEntity.setId(UUID.randomUUID());
        parentEntity.setStringField("parent");
        ChildEntity child = new ChildEntity();
        child.setId(UUID.randomUUID());
        child.setStringField("child");
        child.setBoolField(true);
        parentEntity.setChild(child);

        ParentEntity copy = CopyService.copy(parentEntity);

        assertNull(copy.getId());
        assertNull(copy.getChild().getId());
        assertEquals("parent", copy.getStringField());
        assertEquals("child", copy.getChild().getStringField());
        assertTrue(copy.getChild().boolField);
        assertNotEquals(child, copy.getChild());
    }

    @Test
    void shouldReturnOriginalInstanceIfCopySupportIsNotImplemented() {
        ParentEntity old = new ParentEntity();
        CopyNotSupported copyNotSupported = new CopyNotSupported();
        old.setCopyNotSupported(copyNotSupported);

        ParentEntity copy = CopyService.copy(old);

        assertNotEquals(old, copy);
        assertEquals(old.getCopyNotSupported(), copy.getCopyNotSupported());
    }

    @Test
    void isCollection() throws Exception {
        ParentEntity parentEntity = new ParentEntity();
        Field children = parentEntity.getClass().getDeclaredField("children");
        Field integerField = parentEntity.getClass().getDeclaredField("integerField");

        assertTrue(CopyService.isCollection(children));
        assertFalse(CopyService.isCollection(integerField));
    }

    @Test
    void isOtherCopyableEntity() throws Exception {
        ParentEntity parentEntity = new ParentEntity();
        Field copyAbleEntity = parentEntity.getClass().getDeclaredField("child");
        Field nonCopyAbleEntity = parentEntity.getClass().getDeclaredField("copyNotSupported");

        assertTrue(CopyService.isOtherCopyableEntity(copyAbleEntity));
        assertFalse(CopyService.isOtherCopyableEntity(nonCopyAbleEntity));
    }

    @Test
    void filterFieldFromClass() {
        var fields = CopyService.getAllFields(new ArrayList<>(), ParentEntity.class)
                .stream()
                .filter(CopyService.filterFieldsFromClass(BaseEntity.class))
                .map(Field::getName)
                .toList();

        assertFalse(fields.contains("optLock"));
        assertTrue(fields.contains("id"));
    }

    @Test
    void getNewMapInstanceWhenValueIsNull() throws Exception {
        ParentEntity parentEntity = new ParentEntity();
        parentEntity.setKeyValues(null);
        Field field = ParentEntity.class.getDeclaredField("keyValues");
        field.setAccessible(true);

        Map<?, ?> newMapInstance = CopyService.getNewMapInstance(field, parentEntity);

        assertNull(newMapInstance);
    }

    @Test
    void getCollectionInstanceWhenValueIsNull() throws Exception {
        ParentEntity parentEntity = new ParentEntity();
        parentEntity.setChildren(null);
        Field field = ParentEntity.class.getDeclaredField("children");
        field.setAccessible(true);

        Collection<?> collectionInstance = CopyService.getCollectionInstance(field, parentEntity);

        assertNull(collectionInstance);
    }

    @Test
    void getCollectionInstanceForList() throws Exception {
        ParentEntity parentEntity = new ParentEntity();
        List<ChildEntity> expectedChildren = new ArrayList<>();
        parentEntity.setChildren(expectedChildren);
        Field field = ParentEntity.class.getDeclaredField("children");
        field.setAccessible(true);

        Collection<?> collectionInstance = CopyService.getCollectionInstance(field, parentEntity);

        expectedChildren.add(new ChildEntity());
        assertNotEquals(expectedChildren, collectionInstance);
    }

    @Test
    void getCollectionInstanceForSet() throws Exception {
        ParentEntity parentEntity = new ParentEntity();
        Set<ChildEntity> expectedChildren = new HashSet<>();
        parentEntity.setChildrenAsSet(expectedChildren);
        Field field = ParentEntity.class.getDeclaredField("childrenAsSet");
        field.setAccessible(true);

        Collection<?> collectionInstance = CopyService.getCollectionInstance(field, parentEntity);

        expectedChildren.add(new ChildEntity());
        assertNotEquals(expectedChildren, collectionInstance);
    }

    @Test
    void getCollectionInstanceThrowsException() throws Exception {
        ParentEntity parentEntity = new ParentEntity();
        Field field = ParentEntity.class.getDeclaredField("keyValues");
        field.setAccessible(true);

        Exception exception = assertThrows(Exception.class, () -> CopyService.getCollectionInstance(field, parentEntity));

        assertEquals("Unsupported Collection Type java.util.Map expected java.util.List or java.util.Set", exception.getMessage());
    }

    @Test
    void getNewMapInstance() throws Exception {
        ParentEntity parentEntity = new ParentEntity();
        LinkedHashMap<TestEnum, MapValue> originalMap = new LinkedHashMap<>();
        parentEntity.setKeyValues(originalMap);
        Field field = ParentEntity.class.getDeclaredField("keyValues");
        field.setAccessible(true);

        Map<?, ?> newMapInstance = CopyService.getNewMapInstance(field, parentEntity);

        originalMap.put(TestEnum.TEST_1, null);
        assertNotEquals(newMapInstance, originalMap);
    }

    @Test
    void getNewMapInstanceThrowsException() throws Exception {
        ParentEntity parentEntity = new ParentEntity();
        Field field = ParentEntity.class.getDeclaredField("intField");
        field.setAccessible(true);

        IllegalAccessException exception = assertThrows(IllegalAccessException.class, () -> CopyService.getNewMapInstance(field, parentEntity));

        assertEquals("Unsupported type in intField expected a type of java.util.Map but was int", exception.getMessage());
    }


    private static class ParentEntity extends BaseEntityWithGeneratedId implements CopySupport {
        private String stringField;
        private Integer integerField;
        private int intField;
        private List<ChildEntity> children = new ArrayList<>();
        private Set<ChildEntity> childrenAsSet = new HashSet<>();
        private ChildEntity child;
        private CopyNotSupported copyNotSupported;
        private Map<TestEnum, MapValue> keyValues = new HashMap<>();

        public String getStringField() {
            return stringField;
        }

        public void setStringField(String stringField) {
            this.stringField = stringField;
        }

        public Integer getIntegerField() {
            return integerField;
        }

        public void setIntegerField(Integer integerField) {
            this.integerField = integerField;
        }

        public int getIntField() {
            return intField;
        }

        public void setIntField(int intField) {
            this.intField = intField;
        }

        public List<ChildEntity> getChildren() {
            return children;
        }

        public void setChildren(List<ChildEntity> children) {
            this.children = children;
        }

        public ChildEntity getChild() {
            return child;
        }

        public void setChild(ChildEntity child) {
            this.child = child;
        }

        public Map<TestEnum, MapValue> getKeyValues() {
            return keyValues;
        }

        public void setKeyValues(Map<TestEnum, MapValue> keyValues) {
            this.keyValues = keyValues;
        }

        public CopyNotSupported getCopyNotSupported() {
            return copyNotSupported;
        }

        public void setCopyNotSupported(CopyNotSupported copyNotSupported) {
            this.copyNotSupported = copyNotSupported;
        }

        public Set<ChildEntity> getChildrenAsSet() {
            return childrenAsSet;
        }

        public void setChildrenAsSet(Set<ChildEntity> childrenAsSet) {
            this.childrenAsSet = childrenAsSet;
        }
    }

    private static class ChildEntity extends BaseEntityWithGeneratedId implements CopySupport {
        private ParentEntity parentEntity;
        private Boolean booleanField;
        private boolean boolField;
        private String stringField;

        public ParentEntity getParentEntity() {
            return parentEntity;
        }

        public void setParentEntity(ParentEntity parentEntity) {
            this.parentEntity = parentEntity;
        }

        public Boolean getBooleanField() {
            return booleanField;
        }

        public void setBooleanField(Boolean booleanField) {
            this.booleanField = booleanField;
        }

        public boolean isBoolField() {
            return boolField;
        }

        public void setBoolField(boolean boolField) {
            this.boolField = boolField;
        }

        public String getStringField() {
            return stringField;
        }

        public void setStringField(String stringField) {
            this.stringField = stringField;
        }
    }

    private enum TestEnum {
        TEST_1, TEST_2
    }

    private static class MapValue extends BaseEntityWithGeneratedId implements CopySupport {
        private String stringValue;

        public String getStringValue() {
            return stringValue;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }
    }

    private static class CopyNotSupported extends BaseEntityWithGeneratedId {
        private String stringValue;

        public String getStringValue() {
            return stringValue;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }
    }

}