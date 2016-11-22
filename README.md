# Redis Migration

This is an library to update the redis db, if any refactoring has been done to the models, like
    1. Renaming of a property
    2. Addition of new property
    3. Deletion of a property

The migration tasks are expected to be declared in Yaml file and provided to the libraries api,
for running those tasks.

Once the tasks are run the Yaml file provided, to the api, will have an updated `lastMigrationRun` field.

Installing:

    Installing can be done using jitpack repo, which fetches dependent jars from github.
    ref: https://jitpack.io/

    1. Add the repository in build.gradle, at the end of repositories:

        allprojects {
            repositories {
                ...
                maven { url "https://jitpack.io" }
            }
        }

    2. Add Dependency:

        dependencies {
                compile 'com.github.abdulrahmank:redis_migration:1.0'
        }

Usage:

    1. Call the method `runMigration()` of `RedisMigration` class with required parameters.

        ex: `new RedisMigration().runMigration(redisInterface, "migartion.yml");`

        The first parameter is an instance of `RedisMigration.RedisInterface` interface, this interface is for
        interacting with the redis, make sure the interface methods are implemented with the RedisFactory after
        setting the required db and similar settings.

        ex: `RedisMigration.RedisInterface redisInterface = new RedisMigration.RedisInterface() {
                        @Override
                        public Set<byte[]> keys(byte[] keys) {
                            return jedis.keys(keys);
                        }

                        @Override
                        public void setKey(byte[] key, byte[] modifiedValue) {
                            jedis.set(key, modifiedValue);
                        }

                        @Override
                        public byte[] get(byte[] key) {
                            return jedis.get(key);
                        }

                        @Override
                        public void close() {
                            try {
                                jedis.disconnect();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                   };`
