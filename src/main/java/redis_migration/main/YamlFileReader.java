package redis_migration.main;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Map;

class YamlFileReader {
    private String redisYmlFile;
    private ObjectMapper objectMapper;
    private Map map;
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

    public Map getMigrations() {
        return migrations;
    }

    public YamlFileReader readYmlFile() throws FileNotFoundException, YamlException {
        objectMapper = new ObjectMapper(new MessagePackFactory());
        YamlReader reader = new YamlReader(new FileReader(redisYmlFile));

        map = (Map) reader.read();

        migrations = (Map) map.get("migrations");
        return this;
    }

}