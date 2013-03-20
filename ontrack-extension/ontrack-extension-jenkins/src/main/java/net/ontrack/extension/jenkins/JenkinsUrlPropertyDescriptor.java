package net.ontrack.extension.jenkins;

import net.ontrack.core.model.Entity;
import net.ontrack.extension.api.property.AbstractLinkPropertyExtensionDescriptor;

import java.util.EnumSet;

public class JenkinsUrlPropertyDescriptor extends AbstractLinkPropertyExtensionDescriptor {

    public JenkinsUrlPropertyDescriptor() {
        super("jenkins.url", "jenkins.png");
    }

    @Override
    public EnumSet<Entity> getScope() {
        return EnumSet.allOf(Entity.class);
    }

    @Override
    public String getExtension() {
        return JenkinsExtension.EXTENSION;
    }

    @Override
    public String getName() {
        return "url";
    }
}
