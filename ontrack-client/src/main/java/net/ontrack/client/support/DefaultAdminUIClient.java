package net.ontrack.client.support;

import net.ontrack.client.AdminUIClient;
import net.ontrack.core.model.*;
import net.ontrack.core.security.GlobalFunction;

import java.util.List;

import static java.lang.String.format;

public class DefaultAdminUIClient extends AbstractClient implements AdminUIClient {

    public DefaultAdminUIClient(String url) {
        super(url);
    }

    @Override
    public List<GlobalFunction> getGlobalFunctions() {
        return list(
                getDefaultLocale(),
                format("/ui/admin/acl/global"),
                GlobalFunction.class
        );
    }

    @Override
    public List<Account> accounts() {
        return list(
                getDefaultLocale(),
                format("/ui/admin/accounts"),
                Account.class
        );
    }

    @Override
    public Account account(int id) {
        return get(
                getDefaultLocale(),
                format("/ui/admin/accounts/%d", id),
                Account.class
        );
    }

    @Override
    public ID createAccount(AccountCreationForm form) {
        return post(
                getDefaultLocale(),
                format("/ui/admin/accounts"),
                ID.class,
                form
        );
    }

    @Override
    public Ack deleteAccount(int id) {
        return delete(
                getDefaultLocale(),
                format("/ui/admin/accounts/%d", id),
                Ack.class
        );
    }

    @Override
    public Ack enableExtension(String name) {
        return put(
                getDefaultLocale(),
                format("/ui/admin/extensions/%s", name),
                Ack.class,
                null
        );
    }

    @Override
    public Ack disableExtension(String name) {
        return delete(
                getDefaultLocale(),
                format("/ui/admin/extensions/%s", name),
                Ack.class
        );
    }

    @Override
    public List<AccountSummary> accountLookup(String query) {
        return list(
                getDefaultLocale(),
                format("/ui/admin/account/lookup/%s", query),
                AccountSummary.class
        );
    }
}
