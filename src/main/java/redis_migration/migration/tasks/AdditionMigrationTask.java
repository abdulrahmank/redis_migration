package redis_migration.migration.tasks;

import java.util.Map;

public class AdditionMigrationTask implements MigrationTask {

    @Override
    public Map migrationAction(Map map, String variableName, String variableType) {
        map.put(variableName, null);
        return map;
    }
}