package redis_migration;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.YamlWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedisMigration {

    public interface RedisInterface {
        Set<byte[]> keys(byte[] keys);

        void setKey(byte[] key, byte[] modifiedValue);

        byte[] get(byte[] key);

        void close();
    }

    public interface MigrationTask {
        Map migrationAction(Map map, String oldVariableName, String newVariableName);
    }

    class RenameMigrationTask implements MigrationTask {

        @Override
        public Map migrationAction(Map map, String oldVariableName, String newVariableName) {
            map.put(newVariableName, map.get(oldVariableName));
            map.remove(oldVariableName);
            return map;
        }
    }

    class AdditionMigrationTask implements MigrationTask {

        @Override
        public Map migrationAction(Map map, String variableName, String variableType) {
            map.put(variableName, null);
            return map;
        }
    }

    class DeletionMigrationTask implements MigrationTask {

        @Override
        public Map migrationAction(Map map, String variableToDelete, String newVariableName) {
            map.remove(variableToDelete);
            return map;
        }
    }

    public void runMigration(RedisInterface redisInterface, String redisYmlFile) throws IOException,
            ClassNotFoundException, IllegalAccessException, InstantiationException {

        YamlFileReader yamlFileReader = new YamlFileReader(redisYmlFile).readYmlFile();
        int lastMigrationRun = yamlFileReader.getLastMigrationRun();
        int modelVersion = yamlFileReader.getModelVersion();
        Map yamlMap = yamlFileReader.getMap();
        ObjectMapper objectMapper = yamlFileReader.getObjectMapper();

        if (lastMigrationRun < modelVersion) {
            Map migrations = yamlFileReader.getMigrations();
            for (int migrationNumber = lastMigrationRun + 1; migrationNumber <= modelVersion; migrationNumber++) {
                Map migrationDescription = (Map) (migrations.get(String.valueOf(migrationNumber)));
                List migrationTasks = (List) migrationDescription.get("tasks");
                String keyPattern = migrationDescription.get("keyPattern").toString();
                String mainModelClass = migrationDescription.get("className").toString();

                Object mainObject;
                Set<byte[]> keys = redisInterface.keys(keyPattern.getBytes());
                for (byte[] key : keys) {
                    Class<?> outerClassType = Class.forName(mainModelClass);

                    Object o = objectMapper.convertValue(outerClassType.newInstance(), Object.class);
                    boolean value = o instanceof List;
                    if (!value) {
                        mainObject = objectMapper.readValue(redisInterface.get(key), Map.class);
                        runMigrationTasks(migrationTasks, (Map) mainObject);
                    } else {
                        mainObject = objectMapper.readValue(redisInterface.get(key), Map[].class);
                        for (Map mapObject : (Map[]) mainObject)
                            runMigrationTasks(migrationTasks, mapObject);
                    }

                    byte[] classNames = objectMapper.writeValueAsBytes(objectMapper.convertValue(mainObject,
                            outerClassType));
                    redisInterface.setKey(key, classNames);

                }
                redisInterface.close();
            }
            incrementLastMigrationRunCounterAndUpdateYml(redisYmlFile, yamlMap);
        }
    }

    private void runMigrationTasks(List migrationTasks, Map mapObject) {
        for (Object migrationTask : migrationTasks) {
            MigrationTask migrationTaskObject = null;
            String param1 = null;
            String param2 = null;
            String path = null;
            Map renamed = (Map) ((Map) migrationTask).get("renamed");
            Map added = (Map) ((Map) migrationTask).get("added");
            Map deleted = (Map) ((Map) migrationTask).get("deleted");

            if (renamed != null && !renamed.isEmpty()) {
                migrationTaskObject = new RenameMigrationTask();
                param1 = renamed.get("from").toString();
                param2 = renamed.get("to").toString();
                path = renamed.get("path").toString();
            } else if (added != null && !added.isEmpty()) {
                migrationTaskObject = new AdditionMigrationTask();
                param1 = added.get("name").toString();
                param2 = added.get("type").toString();
                path = added.get("path").toString();
            } else if (deleted != null && !deleted.isEmpty()) {
                migrationTaskObject = new DeletionMigrationTask();
                param1 = deleted.get("name").toString();
                path = deleted.get("path").toString();
            }

            doMigrate(mapObject, path, param1, param2, "", migrationTaskObject);
        }
    }

    private void doMigrate(Map map, String oldVariableNameWithPath, String param1, String param2,
                           String defaultInnerPath, MigrationTask migrationTask) {
        String oldVariableNameWithPathCopy = oldVariableNameWithPath;
        String innerPath = defaultInnerPath;

        if (oldVariableNameWithPath.contains("[")) {
            int startIndexOfInnerPath = oldVariableNameWithPath.indexOf("[");
            int lastIndexOfInnerPath = oldVariableNameWithPath.lastIndexOf("]");
            innerPath = oldVariableNameWithPath.substring(startIndexOfInnerPath + 1, lastIndexOfInnerPath);
            oldVariableNameWithPathCopy = oldVariableNameWithPathCopy.substring(0, startIndexOfInnerPath);
        }

        String[] pathToRenamedVariable = oldVariableNameWithPathCopy.split("\\$");

        if (!pathToRenamedVariable[0].isEmpty()) {
            List<Object> objectList = new ArrayList<>();
            if (!pathToRenamedVariable[0].contains("<")) {
                if (pathToRenamedVariable[0].equals("~")) {
                    addMapObjectToObjectList(objectList, map);
                } else {
                    Object value = map.get(pathToRenamedVariable[0]);
                    addMapObjectToObjectList(objectList, value);
                }
            } else {
                for (Object mapKeySet : map.entrySet()) {
                    Map.Entry entry = (Map.Entry) mapKeySet;
                    if (entry.getKey().toString().matches(pathToRenamedVariable[0].replace("<", "").replace(">", ""))) {
                        Object value = entry.getValue();
                        addMapObjectToObjectList(objectList, value);
                    }
                }
            }

            for (Object object : objectList) {
                Map objectToBeModified = (Map) object;
                if (pathToRenamedVariable.length > 1) {
                    doMigrate(objectToBeModified,
                            oldVariableNameWithPathCopy.replace(pathToRenamedVariable[0] + "$", ""),
                            param1,
                            param2,
                            innerPath, new RenameMigrationTask());
                } else if (!innerPath.isEmpty()) {
                    doMigrate(objectToBeModified,
                            innerPath,
                            param1,
                            param2,
                            "", new RenameMigrationTask());
                } else {
                    migrationTask.migrationAction(objectToBeModified, param1, param2);
                }
            }
        } else {
            doMigrate(map,
                    innerPath,
                    param1,
                    param2,
                    "", new RenameMigrationTask());
        }


    }

    private void addMapObjectToObjectList(List<Object> objectList, Object value) {
        if (value instanceof Map) {
            objectList.add(value);
        } else if (value instanceof List) {
            objectList.addAll((List) value);
        }
    }

    private void incrementLastMigrationRunCounterAndUpdateYml(String redisYmlFile, Map map)
            throws IOException {
        map.put("lastMigrationRun", map.get("modelVersion"));
        YamlWriter var3 = new YamlWriter(new FileWriter(redisYmlFile));
        var3.write(map);
        var3.close();
    }

    private class YamlFileReader {
        private String redisYmlFile;
        private ObjectMapper objectMapper;
        private Map map;
        private int modelVersion;
        private int lastMigrationRun;
        private Map migrations;

        public YamlFileReader(String redisYmlFile) {
            this.redisYmlFile = redisYmlFile;
        }

        public ObjectMapper getObjectMapper() {
            return objectMapper;
        }

        public Map getMap() {
            return map;
        }

        public int getModelVersion() {
            return modelVersion;
        }

        public int getLastMigrationRun() {
            return lastMigrationRun;
        }

        public Map getMigrations() {
            return migrations;
        }

        public YamlFileReader readYmlFile() throws FileNotFoundException, YamlException {
            objectMapper = new ObjectMapper(new MessagePackFactory());
            YamlReader reader = new YamlReader(new FileReader(redisYmlFile));

            map = (Map) reader.read();

            modelVersion = Integer.valueOf(map.get("modelVersion").toString());
            lastMigrationRun = Integer.valueOf(map.get("lastMigrationRun").toString());

            migrations = (Map) map.get("migrations");
            return this;
        }

    }
}