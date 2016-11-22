package redis_migration.main;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.YamlWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import redis.clients.jedis.Jedis;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class RedisMigrationTest {

    private Jedis jedis;
    private RedisMigration.RedisInterface redisInterface;
    private String redisYmlFile;

    @Before
    public void setup() throws JsonProcessingException {
        // For Populating redis db
        jedis = new Jedis("localhost", 6379);
        ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        byte[] bytes = objectMapper.writeValueAsBytes(new Family());
        byte[] bytesFamilies = objectMapper.writeValueAsBytes(new Families());
        byte[] bytesPerson = objectMapper.writeValueAsBytes(new Person("B", 23, null));
        jedis.select(1);
        jedis.set("family".getBytes(), bytes);
        jedis.set("person".getBytes(), bytesPerson);
        jedis.set("families".getBytes(), bytesFamilies);
    }

    @After
    public void tearDown() throws Exception {
        // decrement yaml file
        YamlReader reader = new YamlReader(new FileReader(redisYmlFile));
        Map map = (Map) reader.read();

        map.put("lastMigrationRun", 0);

        YamlWriter var3 = new YamlWriter(new FileWriter(redisYmlFile));
        var3.write(map);
        var3.close();
        // flushing db
        jedis.flushAll();
    }

    @Test
    public void shouldRenameAddAndDeletePropertyIfPresentInRootObject() throws Exception {
        // Syntax:
        // renamed:
        //  path: ~
        //  from: oldPropertyName
        //  to: newPropertyName

        redisInterface = mock(RedisMigration.RedisInterface.class);
        byte[] keyBytes = "person".getBytes();
        when(redisInterface.keys(keyBytes)).thenReturn(jedis.keys(keyBytes));
        when(redisInterface.get(keyBytes)).thenReturn(jedis.get(keyBytes));

        redisYmlFile = "./testData/renamePropertyIfPresentInRootObject.yml";
        new RedisMigration().runMigration(redisInterface, redisYmlFile);

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> personCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisInterface, times(1)).setKey(keyCaptor.capture(), personCaptor.capture());

        Map map = new ObjectMapper(new MessagePackFactory()).readValue(personCaptor.getAllValues().get(0), Map.class);
        assertNotNull(map.get("personName"));
        assertTrue(map.containsKey("name1"));
        assertFalse(map.containsKey("name2"));
    }

    @Test
    public void shouldRenamePropertyIfPresentAtAnyLevelObject() throws Exception {
        // Syntax:
        // renamed:
        //  path: person$father
        //  from: oldPropertyName
        //  to: newPropertyName

        redisInterface = mock(RedisMigration.RedisInterface.class);
        byte[] keyBytes = "family".getBytes();
        when(redisInterface.keys(keyBytes)).thenReturn(jedis.keys(keyBytes));
        when(redisInterface.get(keyBytes)).thenReturn(jedis.get(keyBytes));

        redisYmlFile = "./testData/renamePropertyIfPresentAtAnyLevelObject.yml";
        new RedisMigration().runMigration(redisInterface, redisYmlFile);

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> personCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisInterface, times(1)).setKey(keyCaptor.capture(), personCaptor.capture());

        Map map = new ObjectMapper(new MessagePackFactory()).readValue(personCaptor.getAllValues().get(0), Map.class);
        assertNotNull(((Map) map.get("head")).get("personName"));
    }

    @Test
    public void shouldRenamePropertyIfPresentInsideArrayAtAnyLevelObject() throws Exception {
        // Syntax:
        // renamed:
        //  path: person$vehicle$[~]
        //  from: oldPropertyName
        //  to: newPropertyName
        redisInterface = mock(RedisMigration.RedisInterface.class);
        byte[] keyBytes = "family".getBytes();
        when(redisInterface.keys(keyBytes)).thenReturn(jedis.keys(keyBytes));
        when(redisInterface.get(keyBytes)).thenReturn(jedis.get(keyBytes));

        redisYmlFile = "./testData/renamePropertyIfPresentInsideArrayAtAnyLevelObject.yml";
        new RedisMigration().runMigration(redisInterface, redisYmlFile);

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> personCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisInterface, times(1)).setKey(keyCaptor.capture(), personCaptor.capture());

        Map map = new ObjectMapper(new MessagePackFactory()).readValue(personCaptor.getAllValues().get(0), Map.class);
        assertNotNull(((Map) ((List) ((Map) map.get("head")).get("vehicles")).get(0)).get("wheelType"));
    }

    @Test
    public void shouldRenamePropertyIfPresentInRootObjectGivenRootObjectIsArrayOfObjects() throws Exception {
        // Syntax:
        // renamed:
        //  path: [~]
        //  from: oldPropertyName
        //  to: newPropertyName
        redisInterface = mock(RedisMigration.RedisInterface.class);
        byte[] keyBytes = "families".getBytes();
        when(redisInterface.keys(keyBytes)).thenReturn(jedis.keys(keyBytes));
        when(redisInterface.get(keyBytes)).thenReturn(jedis.get(keyBytes));

        redisYmlFile = "./testData/renamePropertyIfPresentInRootObjectGivenRootObjectIsArrayOfObjects.yml";
        new RedisMigration().runMigration(redisInterface, redisYmlFile);

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> personCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisInterface, times(1)).setKey(keyCaptor.capture(), personCaptor.capture());

        Object map = new ObjectMapper(new MessagePackFactory()).readValue(personCaptor.getAllValues().get(0), Object.class);
        assertNotNull(((Map) ((List) map).get(0)).get("familyHead"));
    }

    @Test
    public void shouldRenamePropertyIfPresentAtAnyLevelObjectGivenRootObjectIsArrayOfObjects() throws Exception {
        // Syntax:
        // renamed:
        //  path: [person$father]
        //  from: oldPropertyName
        //  to: newPropertyName
        redisInterface = mock(RedisMigration.RedisInterface.class);
        byte[] keyBytes = "families".getBytes();
        when(redisInterface.keys(keyBytes)).thenReturn(jedis.keys(keyBytes));
        when(redisInterface.get(keyBytes)).thenReturn(jedis.get(keyBytes));

        redisYmlFile = "./testData/renamePropertyIfPresentAtAnyLevelObjectGivenRootObjectIsArrayOfObjects.yml";
        new RedisMigration().runMigration(redisInterface, redisYmlFile);

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> personCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisInterface, times(1)).setKey(keyCaptor.capture(), personCaptor.capture());

        Object map = new ObjectMapper(new MessagePackFactory()).readValue(personCaptor.getAllValues().get(0), Object.class);
        assertNotNull(((Map) ((Map) ((List) map).get(0)).get("head")).get("personName"));
    }

    @Test
    public void shouldRenamePropertyIfPresentInsideArrayAtAnyLevelObjectGivenRootObjectIsArrayOfObjects() throws Exception {
        // Syntax:
        // renamed:
        //  path: [person$vehicle$[~]]
        //  from: oldPropertyName
        //  to: newPropertyName
        redisInterface = mock(RedisMigration.RedisInterface.class);
        byte[] keyBytes = "families".getBytes();
        when(redisInterface.keys(keyBytes)).thenReturn(jedis.keys(keyBytes));
        when(redisInterface.get(keyBytes)).thenReturn(jedis.get(keyBytes));

        redisYmlFile = "./testData/renamePropertyIfPresentInsideArrayAtAnyLevelObjectGivenRootObjectIsArrayOfObjects.yml";
        new RedisMigration().runMigration(redisInterface, redisYmlFile);

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> personCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisInterface, times(1)).setKey(keyCaptor.capture(), personCaptor.capture());

        Object map = new ObjectMapper(new MessagePackFactory()).readValue(personCaptor.getAllValues().get(0), Object.class);
        assertNotNull(((Map) ((List) ((Map) (((Map) ((List) map).get(0))).get("head")).get("vehicles")).get(0)).get("wheelType"));
    }

    @Test
    public void shouldRenamePropertyIfPresentInsideMapAtAnyLevelObjectGivenRootObjectIsArrayOfObjects() throws Exception {
        // Syntax:
        // renamed:
        //  path: [person$vehicle$[car]]
        //  from: oldPropertyName
        //  to: newPropertyName
        redisInterface = mock(RedisMigration.RedisInterface.class);
        byte[] keyBytes = "families".getBytes();
        when(redisInterface.keys(keyBytes)).thenReturn(jedis.keys(keyBytes));
        when(redisInterface.get(keyBytes)).thenReturn(jedis.get(keyBytes));

        redisYmlFile = "./testData/renamePropertyIfPresentInsideMapAtAnyLevelObjectGivenRootObjectIsArrayOfObjects.yml";
        new RedisMigration().runMigration(redisInterface, redisYmlFile);

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> personCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisInterface, times(1)).setKey(keyCaptor.capture(), personCaptor.capture());

        Object map = new ObjectMapper(new MessagePackFactory()).readValue(personCaptor.getAllValues().get(0), Object.class);
        assertNotNull(((Map) ((List) ((Map) ((Map) ((Map) ((List) map).get(0)).get("personsMap")).get("A")).get("vehicles")).get(0)).get("wheelType"));
        assertNotNull(((Map) ((List) ((Map) ((Map) ((Map) ((List) map).get(1)).get("personsMap")).get("A")).get("vehicles")).get(0)).get("wheelType"));
    }
}

class Families extends ArrayList<Family> {
    Families() {
        add(new Family());
        add(new Family());
    }
}

class Family {

    public Person getHead() {
        return head;
    }

    public void setHead(Person head) {
        this.head = head;
    }

    public List<Person> getPersons() {
        return persons;
    }

    public void setPersons(List<Person> persons) {
        this.persons = persons;
    }

    private Person head = new Person("A", 22, new ArrayList() {{
        add(new Vehicle("bike", "threaded"));
    }});
    List<Person> persons = new ArrayList<>();

    public Map<String, Person> getPersonsMap() {
        return personsMap;
    }

    public void setPersonsMap(Map<String, Person> personsMap) {
        this.personsMap = personsMap;
    }

    Map<String, Person> personsMap = new HashMap<>();

    Family() {
        persons.add(head);
        personsMap.put(persons.get(0).getName(), persons.get(0));
        persons.add(new Person("C", 22, new ArrayList() {{
            add(new Vehicle("bike", "unthreaded"));
        }}));
        personsMap.put(persons.get(1).getName(), persons.get(1));
        persons.add(new Person("D", 24, new ArrayList() {{
            add(new Vehicle("car", "threaded"));
        }}));
        personsMap.put(persons.get(2).getName(), persons.get(2));
    }
}

class Person {
    public Person() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<Vehicle> vehicles) {
        this.vehicles = vehicles;
    }

    private String name;
    private int age;
    List<Vehicle> vehicles;

    Person(String personName, int personAge, List<Vehicle> vehicles) {
        this.name = personName;
        this.age = personAge;
        this.vehicles = vehicles;
    }
}

class Vehicle {
    public Vehicle() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWheels() {
        return wheels;
    }

    public void setWheels(String wheels) {
        this.wheels = wheels;
    }

    private String name;
    private String wheels;

    Vehicle(String name, String wheels) {
        this.name = name;
        this.wheels = wheels;
    }
}