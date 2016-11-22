package redis_migration.migration.tasks;

import java.util.Map;

public class DeletionMigrationTask implements MigrationTask {

    @Override
    public Map migrationAction(Map map, String variableToDelete, String newVariableName) {
        map.remove(variableToDelete);
        return map;
    }
}