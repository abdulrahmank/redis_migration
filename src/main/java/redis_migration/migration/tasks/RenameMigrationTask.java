package redis_migration.migration.tasks;

import java.util.Map;

/**
 * Created by kabdul on 11/22/16.
 */
public class RenameMigrationTask implements MigrationTask {

    @Override
    public Map migrationAction(Map map, String oldVariableName, String newVariableName) {
        map.put(newVariableName, map.get(oldVariableName));
        map.remove(oldVariableName);
        return map;
    }
}
