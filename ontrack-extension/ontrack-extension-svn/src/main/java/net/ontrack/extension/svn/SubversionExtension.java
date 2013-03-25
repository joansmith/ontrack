package net.ontrack.extension.svn;

import net.ontrack.extension.api.support.ExtensionAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class SubversionExtension extends ExtensionAdapter {

    public static final String EXTENSION = "svn";

    @Autowired
    public SubversionExtension(SubversionPathPropertyExtension propertyExtension, SubversionConfigurationExtension configurationExtension) {
        super(
                EXTENSION,
                Collections.singletonList(propertyExtension),
                Collections.singletonList(configurationExtension));
    }
}
