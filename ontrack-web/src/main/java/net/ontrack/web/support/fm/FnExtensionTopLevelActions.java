package net.ontrack.web.support.fm;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import freemarker.template.TemplateMethodModel;
import freemarker.template.TemplateModelException;
import net.ontrack.core.security.SecurityUtils;
import net.ontrack.extension.api.ExtensionManager;
import net.ontrack.extension.api.action.ActionExtension;
import net.ontrack.web.gui.model.GUIAction;
import net.sf.jstring.Strings;
import org.apache.commons.lang3.Validate;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class FnExtensionTopLevelActions implements TemplateMethodModel {

    protected final SecurityUtils securityUtils;
    private final Strings strings;
    private final ExtensionManager extensionManager;

    public FnExtensionTopLevelActions(Strings strings, ExtensionManager extensionManager, SecurityUtils securityUtils) {
        this.strings = strings;
        this.extensionManager = extensionManager;
        this.securityUtils = securityUtils;
    }

    @Override
    public Object exec(List list) throws TemplateModelException {
        // Checks
        Validate.notNull(list, "List of arguments is required");
        Validate.isTrue(list.size() == 0, "No argument is needed");
        // Gets the list of top level actions
        Collection<? extends ActionExtension> actions = extensionManager.getTopLevelActions();
        // Filter on access rights
        actions = Collections2.filter(
                actions,
                new Predicate<ActionExtension>() {
                    @Override
                    public boolean apply(ActionExtension action) {
                        return securityUtils.hasRole(action.getRole());
                    }
                }
        );
        // Gets the locale from the context
        final Locale locale = LocaleContextHolder.getLocale();
        // Converts to GUI actions
        return Collections2.transform(
                actions,
                new Function<ActionExtension, GUIAction>() {
                    @Override
                    public GUIAction apply(ActionExtension action) {
                        return new GUIAction(
                                action.getPath(),
                                strings.get(locale, action.getTitleKey())
                        );
                    }
                }
        );
    }
}