package redis_migration.migration.tasks;

import java.util.Map;

/**
 * Created by kabdul on 11/22/16.
 */
public interface MigrationTask {
    Map migrationAction(Map map, String oldVariableName, String newVariableName);
}
