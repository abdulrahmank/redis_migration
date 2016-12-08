package redis_migration.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class YamlFileReaders extends ArrayList<YamlFileReader> {
    YamlFileReaders(String modelVersion) {
        File file = new File("model.changes");
        String[] modelChangeFiles = file.list();
        String lastModelChangeFile = modelChangeFiles[modelChangeFiles.length - 1];
        Integer modelVersionInt = Integer.valueOf(modelVersion);
        Integer lastModelChangeFileInt = Integer.valueOf(lastModelChangeFile
                .substring(0, lastModelChangeFile.indexOf("-")));
        if (modelVersionInt < lastModelChangeFileInt) {
            int diffInModels = lastModelChangeFileInt - modelVersionInt;
            for (int i = 0; i < diffInModels; i++) {
                this.add(new YamlFileReader("model.changes" + "/" + modelChangeFiles[modelChangeFiles.length - 1 - i]));
            }
        }
        Collections.reverse(this);
    }
}
