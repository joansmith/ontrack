package net.ontrack.backend;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.ontrack.core.model.*;
import net.ontrack.service.DashboardService;
import net.ontrack.service.ManagementService;
import net.sf.jstring.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class DefaultDashboardService implements DashboardService {

    private final ManagementService managementService;
    private final Strings strings;

    @Autowired
    public DefaultDashboardService(ManagementService managementService, Strings strings) {
        this.managementService = managementService;
        this.strings = strings;
    }

    @Override
    @Transactional(readOnly = true)
    public Dashboard getGeneralDashboard(Locale locale) {
        // Gets the list of branches
        List<BranchSummary> branchList = new ArrayList<>();
        for (ProjectSummary projectSummary : managementService.getProjectList()) {
            for (BranchSummary branchSummary : managementService.getBranchList(projectSummary.getId())) {
                branchList.add(branchSummary);
            }
        }
        // OK
        return new Dashboard(
                strings.get(locale, "dashboard.general"),
                branchList
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Dashboard getProjectDashboard(Locale locale, int projectId) {
        ProjectSummary project = managementService.getProject(projectId);
        // Gets the list of branches for this project
        List<BranchSummary> branchList = managementService.getBranchList(projectId);
        // OK
        return new Dashboard(
                strings.get(locale, "dashboard.project", project.getName()),
                branchList
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Dashboard getBranchDashboard(Locale locale, int branchId) {
        BranchSummary branch = managementService.getBranch(branchId);
        return new Dashboard(
                strings.get(locale, "dashboard.branch", branch.getProject().getName(), branch.getName()),
                Collections.singletonList(branch)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatus getBranchStatus(Locale locale, int branchId) {
        BranchSummary branch = managementService.getBranch(branchId);
        // TODO Dashboard status providers
        // OK
        return new DashboardStatus(
                getBranchTitle(branch)
        );
    }

    private String getBranchTitle(BranchSummary branch) {
        return branch.getProject().getName() + "/" + branch.getName();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardPage getBranchPage(Locale locale, int branchId) {
        BranchSummary branch = managementService.getBranch(branchId);
        // Empty page
        DashboardPage page = DashboardPage.create(getBranchTitle(branch));
        // TODO Dashboard section providers
        // TODO Last build
        // All validation stamps
        page = page.withSection(getBranchValidationStampsSection(branchId));
        // All promotion levels
        page = page.withSection(getBranchPromotionsSection(locale, branchId));
        // OK
        return page;
    }

    private DashboardSection getBranchValidationStampsSection(int branchId) {
        List<ValidationStampStatus> fullList = Lists.transform(
                // Gets the list of validation stamps
                managementService.getValidationStampList(branchId),
                // Gets the last validation run for each stamp
                new Function<ValidationStampSummary, ValidationStampStatus>() {
                    @Override
                    public ValidationStampStatus apply(ValidationStampSummary stamp) {
                        List<ValidationRunStatusStub> statusesForLastBuilds = managementService.getStatusesForLastBuilds(stamp.getId(), 1);
                        if (statusesForLastBuilds.isEmpty()) {
                            return new ValidationStampStatus(stamp, null);
                        } else {
                            return new ValidationStampStatus(stamp, statusesForLastBuilds.get(0));
                        }
                    }
                }
        );
        return new DashboardSection(
                "dashboard-branch-validationStamps",
                new DashboardBranchValidationStamps(
                        Lists.newArrayList(Iterables.filter(
                                fullList,
                                new Predicate<ValidationStampStatus>() {
                                    @Override
                                    public boolean apply(ValidationStampStatus v) {
                                        return v.getStatus() != null && v.getStatus().getStatus() == Status.PASSED;
                                    }
                                }
                        )),
                        Lists.newArrayList(Iterables.filter(
                                fullList,
                                new Predicate<ValidationStampStatus>() {
                                    @Override
                                    public boolean apply(ValidationStampStatus v) {
                                        return v.getStatus() != null && v.getStatus().getStatus() != Status.PASSED;
                                    }
                                }
                        )),
                        Lists.newArrayList(Iterables.filter(
                                fullList,
                                new Predicate<ValidationStampStatus>() {
                                    @Override
                                    public boolean apply(ValidationStampStatus v) {
                                        return v.getStatus() == null;
                                    }
                                }
                        ))

                )
        );
    }

    private DashboardSection getBranchPromotionsSection(final Locale locale, int branchId) {
        return new DashboardSection(
                "dashboard-branch-promotions",
                Collections.singletonMap("promotions", Lists.transform(
                        // Gets the list of promotions
                        managementService.getPromotionLevelList(branchId),
                        // Converts to the list of last promotions
                        new Function<PromotionLevelSummary, Promotion>() {
                            @Override
                            public Promotion apply(PromotionLevelSummary promotionLevel) {
                                return managementService.findLastPromotion(locale, promotionLevel.getId());
                            }
                        }
                ))
        );
    }
}
