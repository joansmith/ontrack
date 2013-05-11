package net.ontrack.backend;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.ontrack.backend.dao.*;
import net.ontrack.backend.dao.model.*;
import net.ontrack.backend.db.SQL;
import net.ontrack.core.model.*;
import net.ontrack.core.security.SecurityRoles;
import net.ontrack.core.security.SecurityUtils;
import net.ontrack.core.support.MapBuilder;
import net.ontrack.core.support.TimeUtils;
import net.ontrack.core.validation.NameDescription;
import net.ontrack.extension.api.decorator.DecorationService;
import net.ontrack.extension.api.property.PropertiesService;
import net.ontrack.service.EventService;
import net.ontrack.service.ManagementService;
import net.ontrack.service.model.Event;
import net.sf.jstring.Strings;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class ManagementServiceImpl extends AbstractServiceImpl implements ManagementService {
    /**
     * Maximum number of events to store in a {@link BuildValidationStampRun}.
     *
     * @see #getValidationRuns(java.util.Locale, int, int)
     */
    public static final int MAX_EVENTS_IN_BUILD_VALIDATION_STAMP_RUN = 10;
    // TODO Split the service in different parts
    private final SecurityUtils securityUtils;
    private final Strings strings;
    private final AccountDao accountDao;
    private final ProjectDao projectDao;
    private final BranchDao branchDao;
    private final ValidationStampDao validationStampDao;
    private final PromotionLevelDao promotionLevelDao;
    private final BuildDao buildDao;
    private final PromotedRunDao promotedRunDao;
    private final ValidationRunDao validationRunDao;
    private final ValidationRunStatusDao validationRunStatusDao;
    private final ValidationRunEventDao validationRunEventDao;
    private final CommentDao commentDao;
    private final EntityDao entityDao;
    private final PropertiesService propertiesService;
    private final DecorationService decorationService;
    // Dao -> Summary converters
    private final Function<TProject, ProjectSummary> projectSummaryFunction = new Function<TProject, ProjectSummary>() {
        @Override
        public ProjectSummary apply(TProject t) {
            return new ProjectSummary(t.getId(), t.getName(), t.getDescription());
        }
    };
    private final Function<TBranch, BranchSummary> branchSummaryFunction = new Function<TBranch, BranchSummary>() {
        @Override
        public BranchSummary apply(TBranch t) {
            return new BranchSummary(
                    t.getId(),
                    t.getName(),
                    t.getDescription(),
                    getProject(t.getProject())
            );
        }
    };
    private final Function<TValidationStamp, ValidationStampSummary> validationStampSummaryFunction = new Function<TValidationStamp, ValidationStampSummary>() {
        @Override
        public ValidationStampSummary apply(TValidationStamp t) {
            return new ValidationStampSummary(
                    t.getId(),
                    t.getName(),
                    t.getDescription(),
                    getBranch(t.getBranch()),
                    t.getOrderNb(),
                    getAccountSummary(t.getOwnerId())
            );
        }
    };
    private final Function<TPromotionLevel, PromotionLevelSummary> promotionLevelSummaryFunction = new Function<TPromotionLevel, PromotionLevelSummary>() {
        @Override
        public PromotionLevelSummary apply(TPromotionLevel t) {
            return new PromotionLevelSummary(
                    t.getId(),
                    getBranch(t.getBranch()),
                    t.getLevelNb(),
                    t.getName(),
                    t.getDescription(),
                    t.isAutoPromote()
            );
        }
    };
    private final Function<TBuild, BuildSummary> buildSummaryFunction = new Function<TBuild, BuildSummary>() {

        @Override
        public BuildSummary apply(TBuild t) {
            return new BuildSummary(
                    t.getId(),
                    t.getName(),
                    t.getDescription(),
                    getBranch(t.getBranch())
            );
        }
    };

    @Autowired
    public ManagementServiceImpl(ValidatorService validatorService, EventService auditService, SecurityUtils securityUtils, Strings strings, AccountDao accountDao, ProjectDao projectDao, BranchDao branchDao, ValidationStampDao validationStampDao, PromotionLevelDao promotionLevelDao, BuildDao buildDao, PromotedRunDao promotedRunDao, ValidationRunDao validationRunDao, ValidationRunStatusDao validationRunStatusDao, ValidationRunEventDao validationRunEventDao, CommentDao commentDao, EntityDao entityDao, PropertiesService propertiesService, DecorationService decorationService) {
        super(validatorService, auditService);
        this.securityUtils = securityUtils;
        this.strings = strings;
        this.accountDao = accountDao;
        this.projectDao = projectDao;
        this.branchDao = branchDao;
        this.validationStampDao = validationStampDao;
        this.promotionLevelDao = promotionLevelDao;
        this.buildDao = buildDao;
        this.promotedRunDao = promotedRunDao;
        this.validationRunDao = validationRunDao;
        this.validationRunStatusDao = validationRunStatusDao;
        this.validationRunEventDao = validationRunEventDao;
        this.commentDao = commentDao;
        this.entityDao = entityDao;
        this.propertiesService = propertiesService;
        this.decorationService = decorationService;
    }

    private AccountSummary getAccountSummary(Integer id) {
        if (id == null) {
            return null;
        } else {
            TAccount account = accountDao.getByID(id);
            return new AccountSummary(
                    account.getId(),
                    account.getName(),
                    account.getFullName()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSummary> getProjectList() {
        return Lists.transform(
                projectDao.findAll(),
                projectSummaryFunction
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectSummary getProject(int id) {
        return projectSummaryFunction.apply(projectDao.getById(id));
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public ProjectSummary createProject(ProjectCreationForm form) {
        // Validation
        validate(form, NameDescription.class);
        // Query
        int id = projectDao.createProject(form.getName(), form.getDescription());
        // Audit
        event(Event.of(EventType.PROJECT_CREATED).withProject(id));
        // OK
        return new ProjectSummary(id, form.getName(), form.getDescription());
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public ProjectSummary updateProject(int id, ProjectUpdateForm form) {
        // Validation
        validate(form, NameDescription.class);
        // Query
        Ack ack = projectDao.updateProject(id, form.getName(), form.getDescription());
        // Audit
        if (ack.isSuccess()) {
            event(Event.of(EventType.PROJECT_UPDATED).withProject(id));
        }
        // OK
        return getProject(id);
    }

    // Validation stamps

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack deleteProject(int id) {
        String name = getEntityName(Entity.PROJECT, id);
        Ack ack = projectDao.deleteProject(id);
        if (ack.isSuccess()) {
            event(Event.of(EventType.PROJECT_DELETED).withValue("project", name));
        }
        return ack;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchSummary> getBranchList(int project) {
        return Lists.transform(
                branchDao.findByProject(project),
                branchSummaryFunction
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BranchSummary getBranch(int id) {
        return branchSummaryFunction.apply(branchDao.getById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public DecoratedBranch getDecoratedBranch(Locale locale, int branchId) {
        BranchSummary branch = getBranch(branchId);
        return new DecoratedBranch(
                branch,
                getLocalizedDecorations(locale, Entity.BRANCH, branchId)
        );
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public BranchSummary createBranch(int project, BranchCreationForm form) {
        // Validation
        validate(form, NameDescription.class);
        // Query
        int id = branchDao.createBranch(
                project,
                form.getName(),
                form.getDescription()
        );
        // Audit
        event(Event.of(EventType.BRANCH_CREATED).withProject(project).withBranch(id));
        // OK
        return new BranchSummary(id, form.getName(), form.getDescription(), getProject(project));
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack deleteBranch(int branchId) {
        BranchSummary branch = getBranch(branchId);
        Ack ack = branchDao.deleteBranch(branchId);
        if (ack.isSuccess()) {
            event(Event.of(EventType.BRANCH_DELETED)
                    .withValue("project", branch.getProject().getName())
                    .withValue("branch", branch.getName()));
        }
        return ack;
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public BranchSummary updateBranch(int branch, BranchUpdateForm form) {
        // Validation
        validate(form, NameDescription.class);
        // Loads existing branch
        BranchSummary existingBranch = getBranch(branch);
        // Query
        branchDao.updateBranch(
                branch,
                form.getName(),
                form.getDescription()
        );
        // Audit
        event(Event.of(EventType.BRANCH_UPDATED).withProject(existingBranch.getProject().getId()).withBranch(branch));
        // OK
        return getBranch(branch);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public BranchSummary cloneBranch(int branchId, BranchCloneForm form) {
        // Validation
        validate(form, NameDescription.class);
        // Gets the original branch
        BranchSummary originalBranch = getBranch(branchId);
        // Creates the (empty) branch
        BranchSummary newBranch = createBranch(
                originalBranch.getProject().getId(),
                new BranchCreationForm(
                        form.getName(),
                        form.getDescription()
                )
        );
        int newBranchId = newBranch.getId();
        // Promotion levels
        List<PromotionLevelSummary> promotionLevelList = new ArrayList<>(getPromotionLevelList(branchId));
        // Sort by increasing level number
        Collections.sort(
                promotionLevelList,
                new Comparator<PromotionLevelSummary>() {
                    @Override
                    public int compare(PromotionLevelSummary o1, PromotionLevelSummary o2) {
                        return o1.getLevelNb() - o2.getLevelNb();
                    }
                }
        );
        // Links between promotion levels & validation stamps
        for (PromotionLevelSummary promotionLevel : promotionLevelList) {
            // Creates the new promotion level
            PromotionLevelSummary newPromotionLevel = createPromotionLevel(
                    newBranchId,
                    new PromotionLevelCreationForm(
                            promotionLevel.getName(),
                            promotionLevel.getDescription()
                    )
            );
            // Copies any image
            byte[] promotionLevelImage = imagePromotionLevel(promotionLevel.getId());
            if (promotionLevelImage != null) {
                promotionLevelDao.updateImage(
                        newPromotionLevel.getId(),
                        promotionLevelImage);
            }
            // Gets all the linked stamps
            List<TValidationStamp> linkedStamps = validationStampDao.findByPromotionLevel(promotionLevel.getId());
            for (TValidationStamp linkedStamp : linkedStamps) {
                ValidationStampSummary newValidationStamp = cloneValidationStampSummary(newBranchId, linkedStamp);
                // Link to the promotion level
                linkValidationStampToPromotionLevel(newValidationStamp.getId(), newPromotionLevel.getId());
            }
        }
        // Gets all the unlinked validation stamps
        List<TValidationStamp> unlinkedStamp = validationStampDao.findByNoPromotionLevel(branchId);
        for (TValidationStamp stamp : unlinkedStamp) {
            cloneValidationStampSummary(newBranchId, stamp);
        }

        // Properties
        List<PropertyValue> propertyValues = propertiesService.getPropertyValues(Entity.BRANCH, branchId);
        List<PropertyCreationForm> propertyCreationForms = Lists.transform(
                propertyValues,
                new Function<PropertyValue, PropertyCreationForm>() {
                    @Override
                    public PropertyCreationForm apply(PropertyValue propertyValue) {
                        return new PropertyCreationForm(
                                propertyValue.getExtension(),
                                propertyValue.getName(),
                                propertyValue.getValue()
                        );
                    }
                }
        );
        propertiesService.createProperties(
                Entity.BRANCH,
                newBranchId,
                new PropertiesCreationForm(propertyCreationForms)
        );
        // OK
        return newBranch;
    }

    private ValidationStampSummary cloneValidationStampSummary(int newBranchId, TValidationStamp linkedStamp) {
        ValidationStampSummary newValidationStamp = createValidationStamp(
                newBranchId,
                new ValidationStampCreationForm(
                        linkedStamp.getName(),
                        linkedStamp.getDescription()
                )
        );
        // Copies any image
        byte[] image = imageValidationStamp(linkedStamp.getId());
        if (image != null) {
            validationStampDao.updateImage(
                    newValidationStamp.getId(),
                    image);
        }
        return newValidationStamp;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ValidationStampSummary> getValidationStampList(int branch) {
        return Lists.transform(
                validationStampDao.findByBranch(branch),
                validationStampSummaryFunction
        );
    }

    // Promotion levels

    @Override
    @Transactional(readOnly = true)
    public ValidationStampSummary getValidationStamp(int id) {
        return validationStampSummaryFunction.apply(
                validationStampDao.getById(id)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DecoratedValidationStamp getDecoratedValidationStamp(Locale locale, int validationStampId) {
        ValidationStampSummary validationStamp = getValidationStamp(validationStampId);
        return new DecoratedValidationStamp(
                validationStamp,
                getLocalizedDecorations(locale, Entity.VALIDATION_STAMP, validationStampId)
        );
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public ValidationStampSummary createValidationStamp(int branch, ValidationStampCreationForm form) {
        // Validation
        validate(form, NameDescription.class);
        // Query
        int id = validationStampDao.createValidationStamp(branch, form.getName(), form.getDescription());
        // Branch summary
        BranchSummary theBranch = getBranch(branch);
        // Audit
        event(Event.of(EventType.VALIDATION_STAMP_CREATED)
                .withProject(theBranch.getProject().getId())
                .withBranch(theBranch.getId())
                .withValidationStamp(id));
        // OK
        return getValidationStamp(id);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public ValidationStampSummary updateValidationStamp(int validationStampId, ValidationStampUpdateForm form) {
        // Validation
        validate(form, NameDescription.class);
        // Existing value
        ValidationStampSummary existing = getValidationStamp(validationStampId);
        // Query
        validationStampDao.updateValidationStamp(validationStampId, form.getName(), form.getDescription());
        // Audit
        event(Event.of(EventType.VALIDATION_STAMP_UPDATED)
                .withProject(existing.getBranch().getProject().getId())
                .withBranch(existing.getBranch().getId())
                .withValidationStamp(validationStampId));
        // OK
        return getValidationStamp(validationStampId);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack deleteValidationStamp(int validationStampId) {
        ValidationStampSummary validationStamp = getValidationStamp(validationStampId);
        Ack ack = validationStampDao.deleteValidationStamp(validationStampId);
        if (ack.isSuccess()) {
            event(Event.of(EventType.VALIDATION_STAMP_DELETED)
                    .withValue("project", validationStamp.getBranch().getProject().getName())
                    .withValue("branch", validationStamp.getBranch().getName())
                    .withValue("validationStamp", validationStamp.getName()));
        }
        return ack;
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack upValidationStamp(int validationStampId) {
        return validationStampDao.upValidationStamp(validationStampId);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack downValidationStamp(int validationStampId) {
        return validationStampDao.downValidationStamp(validationStampId);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack setValidationStampOwner(int validationStampId, int ownerId) {
        return validationStampDao.setValidationStampOwner(validationStampId, ownerId);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack unsetValidationStampOwner(int validationStampId) {
        return validationStampDao.setValidationStampOwner(validationStampId, null);
    }

    @Override
    @Transactional
    @Secured({SecurityRoles.USER, SecurityRoles.CONTROLLER, SecurityRoles.ADMINISTRATOR})
    public Ack addValidationStampComment(int validationStampId, ValidationStampCommentForm form) {
        // Comment
        CommentStub comment = createComment(Entity.VALIDATION_STAMP, validationStampId, form.getComment());
        // Registers an event for this comment
        event(
                collectEntityContext(
                        Event.of(EventType.VALIDATION_STAMP_COMMENT), Entity.VALIDATION_STAMP, validationStampId)
                        .withEntity(Entity.VALIDATION_STAMP, validationStampId)
                        .withComment(comment.getComment()));
        // OK
        return Ack.OK;
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Comment> getValidationStampComments(final Locale locale, final int validationStampId, int offset, int count) {
        return Lists.newArrayList(Collections2.transform(
                commentDao.findByEntity(Entity.VALIDATION_STAMP, validationStampId, offset, count),
                new Function<TComment, Comment>() {
                    @Override
                    public Comment apply(TComment t) {
                        return new Comment(
                                t.getId(),
                                t.getContent(),
                                new DatedSignature(
                                        new Signature(
                                                t.getAuthorId(),
                                                t.getAuthor()
                                        ),
                                        t.getTimestamp(),
                                        TimeUtils.elapsed(strings, locale, t.getTimestamp(), TimeUtils.now(), t.getAuthor()),
                                        TimeUtils.format(locale, t.getTimestamp())
                                )
                        );
                    }
                }
        ));
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack imageValidationStamp(final int validationStampId, MultipartFile image) {
        return setImage(
                image,
                SQL.VALIDATION_STAMP_IMAGE_MAXSIZE,
                new Function<byte[], Ack>() {
                    @Override
                    public Ack apply(byte[] image) {
                        return validationStampDao.updateImage(validationStampId, image);
                    }
                });

    }

    @Override
    public byte[] imageValidationStamp(int validationStampId) {
        return validationStampDao.getImage(validationStampId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionLevelSummary> getPromotionLevelList(int branch) {
        return Lists.transform(
                promotionLevelDao.findByBranch(branch),
                promotionLevelSummaryFunction
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionLevelSummary getPromotionLevel(int promotionLevelId) {
        return promotionLevelSummaryFunction.apply(
                promotionLevelDao.getById(promotionLevelId)
        );
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public PromotionLevelSummary createPromotionLevel(int branchId, PromotionLevelCreationForm form) {
        // Validation
        validate(form, NameDescription.class);
        // Query
        int id = promotionLevelDao.createPromotionLevel(
                branchId,
                form.getName(),
                form.getDescription()
        );
        // Branch summary
        BranchSummary theBranch = getBranch(branchId);
        // Audit
        event(Event.of(EventType.PROMOTION_LEVEL_CREATED)
                .withProject(theBranch.getProject().getId())
                .withBranch(theBranch.getId())
                .withPromotionLevel(id));
        // OK
        return getPromotionLevel(id);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public PromotionLevelSummary updatePromotionLevel(int promotionLevelId, PromotionLevelUpdateForm form) {
        // Validation
        validate(form, NameDescription.class);
        // Existing value
        PromotionLevelSummary existing = getPromotionLevel(promotionLevelId);
        // Query
        promotionLevelDao.updatePromotionLevel(promotionLevelId, form.getName(), form.getDescription());
        // Audit
        event(Event.of(EventType.PROMOTION_LEVEL_UPDATED)
                .withProject(existing.getBranch().getProject().getId())
                .withBranch(existing.getBranch().getId())
                .withPromotionLevel(promotionLevelId));
        // OK
        return getPromotionLevel(promotionLevelId);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack deletePromotionLevel(int promotionLevelId) {
        PromotionLevelSummary promotionLevel = getPromotionLevel(promotionLevelId);
        Ack ack = promotionLevelDao.deletePromotionLevel(promotionLevelId);
        if (ack.isSuccess()) {
            event(Event.of(EventType.PROMOTION_LEVEL_DELETED)
                    .withValue("project", promotionLevel.getBranch().getProject().getName())
                    .withValue("branch", promotionLevel.getBranch().getName())
                    .withValue("promotionLevel", promotionLevel.getName()));
        }
        return ack;
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack linkValidationStampToPromotionLevel(int validationStampId, int promotionLevelId) {
        Ack ack = validationStampDao.linkValidationStampToPromotionLevel(validationStampId, promotionLevelId);
        if (ack.isSuccess()) {
            Event event = Event.of(EventType.VALIDATION_STAMP_LINKED);
            event = collectEntityContext(event, Entity.VALIDATION_STAMP, validationStampId);
            event = collectEntityContext(event, Entity.PROMOTION_LEVEL, promotionLevelId);
            event(event);
            return Ack.OK;
        } else {
            return Ack.NOK;
        }
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack unlinkValidationStampToPromotionLevel(int validationStampId) {
        Ack ack = validationStampDao.unlinkValidationStampToPromotionLevel(validationStampId);
        if (ack.isSuccess()) {
            Event event = Event.of(EventType.VALIDATION_STAMP_UNLINKED);
            event = collectEntityContext(event, Entity.VALIDATION_STAMP, validationStampId);
            event(event);
            return Ack.OK;
        } else {
            return Ack.NOK;
        }
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack upPromotionLevel(int promotionLevelId) {
        return promotionLevelDao.upPromotionLevel(promotionLevelId);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack downPromotionLevel(int promotionLevelId) {
        return promotionLevelDao.downPromotionLevel(promotionLevelId);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Ack imagePromotionLevel(final int promotionLevelId, MultipartFile image) {
        return setImage(
                image,
                SQL.PROMOTION_LEVEL_IMAGE_MAXSIZE,
                new Function<byte[], Ack>() {
                    @Override
                    public Ack apply(byte[] image) {
                        return promotionLevelDao.updateImage(promotionLevelId, image);
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] imagePromotionLevel(int promotionLevelId) {
        return promotionLevelDao.getImage(promotionLevelId);
    }

    @Override
    @Transactional(readOnly = true)
    @Secured(SecurityRoles.ADMINISTRATOR)
    public PromotionLevelManagementData getPromotionLevelManagementData(int branchId) {
        // Gets the branch
        BranchSummary branch = getBranch(branchId);
        // List of validation stamps for this branch, without any promotion level
        List<ValidationStampSummary> freeValidationStampList = getValidationStampWithoutPromotionLevel(branchId);
        // List of promotion levels for this branch
        List<PromotionLevelSummary> promotionLevelList = getPromotionLevelList(branchId);
        // List of promotion levels with stamps
        List<PromotionLevelAndStamps> promotionLevelAndStampsList = Lists.transform(promotionLevelList, new Function<PromotionLevelSummary, PromotionLevelAndStamps>() {
            @Override
            public PromotionLevelAndStamps apply(PromotionLevelSummary promotionLevelSummary) {
                // Gets the list of stamps for this promotion level
                List<ValidationStampSummary> stamps = getValidationStampForPromotionLevel(promotionLevelSummary.getId());
                // OK
                return new PromotionLevelAndStamps(promotionLevelSummary).withStamps(stamps);
            }
        });
        // OK
        return new PromotionLevelManagementData(branch, freeValidationStampList, promotionLevelAndStampsList);
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Flag setPromotionLevelAutoPromote(int promotionLevelId) {
        // Auto promotion can be enabled only if the promotion level is associated to at least one validation stamp
        List<TValidationStamp> stamps = validationStampDao.findByPromotionLevel(promotionLevelId);
        if (stamps.isEmpty()) {
            return Flag.UNSET;
        } else {
            promotionLevelDao.setAutoPromote(promotionLevelId, true);
            return Flag.SET;
        }
    }

    @Override
    @Transactional
    @Secured(SecurityRoles.ADMINISTRATOR)
    public Flag unsetPromotionLevelAutoPromote(int promotionLevelId) {
        promotionLevelDao.setAutoPromote(promotionLevelId, false);
        return Flag.UNSET;
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionLevelSummary getPromotionLevelForValidationStamp(int validationStamp) {
        TValidationStamp t = validationStampDao.getById(validationStamp);
        Integer promotionLevelId = t.getPromotionLevel();
        if (promotionLevelId != null) {
            return getPromotionLevel(promotionLevelId);
        } else {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPromotionLevelComplete(final int build, int promotionLevelId) {
        // Gets the promotion level
        PromotionLevelSummary promotionLevel = getPromotionLevel(promotionLevelId);
        if (!promotionLevel.isAutoPromote()) {
            return false;
        } else {
            // Gets the list of validation stamps for this promotion level
            List<ValidationStampSummary> stamps = getValidationStampForPromotionLevel(promotionLevelId);
            return Iterables.all(
                    stamps,
                    new Predicate<ValidationStampSummary>() {
                        @Override
                        public boolean apply(ValidationStampSummary stamp) {
                            TValidationRun r = validationRunDao.findLastByBuildAndValidationStamp(build, stamp.getId());
                            if (r != null) {
                                TValidationRunStatus rs = validationRunStatusDao.findLastForValidationRun(r.getId());
                                return rs != null && rs.getStatus() == Status.PASSED;
                            } else {
                                return false;
                            }
                        }
                    }
            );
        }
    }

    protected List<ValidationStampSummary> getValidationStampForPromotionLevel(int promotionLevelId) {
        return Lists.transform(
                validationStampDao.findByPromotionLevel(promotionLevelId),
                validationStampSummaryFunction
        );
    }

    protected List<ValidationStampSummary> getValidationStampWithoutPromotionLevel(int branchId) {
        return Lists.transform(
                validationStampDao.findByNoPromotionLevel(branchId),
                validationStampSummaryFunction
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BranchBuilds getBuildList(final Locale locale, int branch, int offset, int count) {
        return getBranchBuilds(locale, branch, buildDao.findByBranch(branch, offset, count));
    }

    @Override
    @Transactional(readOnly = true)
    public BranchBuilds queryBuilds(final Locale locale, int branch, BuildFilter filter) {
        return getBranchBuilds(locale, branch, buildDao.query(branch, filter));
    }

    @Override
    public BuildSummary findLastBuildWithValidationStamp(int validationStamp, Set<Status> statuses) {
        TBuild tBuild = buildDao.findLastBuildWithValidationStamp(validationStamp, statuses);
        if (tBuild != null) {
            return buildSummaryFunction.apply(tBuild);
        } else {
            return null;
        }
    }

    @Override
    public BuildSummary findLastBuildWithPromotionLevel(final int promotionLevel) {
        TBuild tBuild = buildDao.findLastBuildWithPromotionLevel(promotionLevel);
        if (tBuild != null) {
            return buildSummaryFunction.apply(tBuild);
        } else {
            return null;
        }
    }

    private BranchBuilds getBranchBuilds(final Locale locale, int branch, List<TBuild> tlist) {
        return new BranchBuilds(
                // Validation stamps for the branch
                Lists.transform(
                        getValidationStampList(branch),
                        new Function<ValidationStampSummary, DecoratedValidationStamp>() {
                            @Override
                            public DecoratedValidationStamp apply(ValidationStampSummary summary) {
                                return new DecoratedValidationStamp(
                                        summary,
                                        getLocalizedDecorations(locale, Entity.VALIDATION_STAMP, summary.getId())
                                );
                            }
                        }
                ),
                // Promotion levels for the branch
                getPromotionLevelList(branch),
                // Status list
                Arrays.asList(Status.values()),
                // Builds for the branch and their complete status
                Lists.transform(
                        tlist,
                        getBuildCompleteStatusFn(locale)
                )
        );
    }

    private Function<TBuild, BuildCompleteStatus> getBuildCompleteStatusFn(final Locale locale) {
        return new Function<TBuild, BuildCompleteStatus>() {
            @Override
            public BuildCompleteStatus apply(TBuild t) {
                int buildId = t.getId();
                List<LocalizedDecoration> decorations = getLocalizedDecorations(locale, Entity.BUILD, buildId);
                List<BuildValidationStamp> stamps = getBuildValidationStamps(locale, buildId);
                List<BuildPromotionLevel> promotionLevels = getBuildPromotionLevels(locale, buildId);
                DatedSignature signature = getDatedSignature(locale, EventType.BUILD_CREATED, Entity.BUILD, buildId);
                return new BuildCompleteStatus(
                        buildId,
                        t.getName(),
                        t.getDescription(),
                        signature,
                        decorations,
                        stamps,
                        promotionLevels);
            }
        };
    }

    private List<LocalizedDecoration> getLocalizedDecorations(final Locale locale, Entity entity, int entityId) {
        return Lists.transform(
                decorationService.getDecorations(entity, entityId),
                new Function<Decoration, LocalizedDecoration>() {
                    @Override
                    public LocalizedDecoration apply(Decoration decoration) {
                        return new LocalizedDecoration(
                                decoration.getTitle().getLocalizedMessage(strings, locale),
                                decoration.getCls(),
                                decoration.getIconPath()
                        );
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    public BuildSummary getLastBuild(int branch) {
        TBuild t = buildDao.findLastByBranch(branch);
        if (t != null) {
            return buildSummaryFunction.apply(t);
        } else {
            throw new BranchNoBuildFoundException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findBuildByName(int branchId, String buildName) {
        return buildDao.findByBrandAndName(branchId, buildName);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findBuildAfterUsingNumericForm(int branchId, String buildName) {
        return buildDao.findBuildAfterUsingNumericForm(branchId, buildName);
    }

    @Override
    @Transactional(readOnly = true)
    public BuildSummary getBuild(int id) {
        TBuild t = buildDao.getById(id);
        return buildSummaryFunction.apply(t);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BuildValidationStamp> getBuildValidationStamps(final Locale locale, final int buildId) {
        // Gets the build details
        BuildSummary build = getBuild(buildId);
        // Gets all the stamps for the branch
        List<ValidationStampSummary> stamps = getValidationStampList(build.getBranch().getId());
        // Collects information for all stamps
        return Lists.transform(
                stamps,
                new Function<ValidationStampSummary, BuildValidationStamp>() {
                    @Override
                    public BuildValidationStamp apply(ValidationStampSummary stamp) {
                        return getBuildValidationStamp(stamp, locale, buildId);
                    }
                });
    }

    protected BuildValidationStamp getBuildValidationStamp(ValidationStampSummary stamp, Locale locale, int buildId) {
        BuildValidationStamp buildStamp = BuildValidationStamp.of(stamp);
        // Gets the latest runs with their status for this build and this stamp
        List<BuildValidationStampRun> runStatuses = getValidationRuns(locale, buildId, stamp.getId());
        // OK
        return buildStamp.withRuns(runStatuses);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BuildPromotionLevel> getBuildPromotionLevels(final Locale locale, final int buildId) {
        // Gets all the promotion levels that were run for this build
        List<TPromotionLevel> tPromotionLevels = promotionLevelDao.findByBuild(buildId);
        // Conversion
        return Lists.transform(
                tPromotionLevels,
                new Function<TPromotionLevel, BuildPromotionLevel>() {
                    @Override
                    public BuildPromotionLevel apply(TPromotionLevel level) {
                        return new BuildPromotionLevel(
                                getDatedSignature(locale, EventType.PROMOTED_RUN_CREATED,
                                        MapBuilder.of(Entity.BUILD, buildId).with(Entity.PROMOTION_LEVEL, level.getId()).get()),
                                level.getName(),
                                level.getDescription(),
                                level.getLevelNb()
                        );
                    }
                }
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationRunSummary getValidationRun(int id) {
        TValidationRun t = validationRunDao.getById(id);
        int runId = t.getId();
        return new ValidationRunSummary(
                runId,
                t.getRunOrder(),
                t.getDescription(),
                getBuild(t.getBuild()),
                getValidationStamp(t.getValidationStamp()),
                getLastValidationRunStatus(runId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ValidationRunEvent> getValidationRunHistory(final Locale locale, int validationRunId, int offset, int count) {
        ValidationRunSummary validationRun = getValidationRun(validationRunId);
        int branchId = validationRun.getBuild().getBranch().getId();
        int validationStampId = validationRun.getValidationStamp().getId();
        return getValidationRunEvents(locale, validationStampId, offset, count, branchId, validationRunId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ValidationRunEvent> getValidationRunsForValidationStamp(final Locale locale, int validationStampId, int offset, int count) {
        // Gets the validation stamp
        ValidationStampSummary validationStamp = getValidationStamp(validationStampId);
        // Gets the branch id
        int branchId = validationStamp.getBranch().getId();
        // All validation runs
        int validationRunId = Integer.MAX_VALUE;
        // OK
        return getValidationRunEvents(locale, validationStampId, offset, count, branchId, validationRunId);
    }

    private List<ValidationRunEvent> getValidationRunEvents(final Locale locale, int validationStampId, int offset, int count, int branchId, int validationRunId) {
        return Lists.transform(
                validationRunEventDao.findByBranchAndValidationStamp(
                        validationRunId,
                        branchId,
                        validationStampId,
                        offset, count),
                new Function<TValidationRunEvent, ValidationRunEvent>() {
                    @Override
                    public ValidationRunEvent apply(TValidationRunEvent t) {
                        return new ValidationRunEvent(
                                getValidationRun(t.getValidationRunId()),
                                new DatedSignature(
                                        new Signature(
                                                t.getAuthorId(),
                                                t.getAuthor()
                                        ),
                                        t.getTimestamp(),
                                        TimeUtils.elapsed(strings, locale, t.getTimestamp(), TimeUtils.now(), t.getAuthor()),
                                        TimeUtils.format(locale, t.getTimestamp())
                                ),
                                t.getStatus(),
                                t.getContent()
                        );
                    }
                }
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ValidationRunStatusStub> getStatusesForLastBuilds(int validationStampId, int count) {
        // Gets the last validation runs
        List<TValidationRun> runs = validationRunDao.findLastRunsOfBuildByValidationStamp(validationStampId, count);
        // Gets the last status
        return Lists.transform(
                runs,
                new Function<TValidationRun, ValidationRunStatusStub>() {
                    @Override
                    public ValidationRunStatusStub apply(TValidationRun run) {
                        return getLastValidationRunStatus(run.getId());
                    }
                }
        );
    }

    // Validation run status

    @Override
    @Transactional(readOnly = true)
    public List<BuildValidationStampRun> getValidationRuns(final Locale locale, final int buildId, final int validationStampId) {
        // Lists of runs for the build and validation stamp
        List<TValidationRun> runs = validationRunDao.findByBuildAndValidationStamp(buildId, validationStampId);
        return Lists.transform(
                runs,
                new Function<TValidationRun, BuildValidationStampRun>() {
                    @Override
                    public BuildValidationStampRun apply(TValidationRun t) {
                        int runId = t.getId();
                        ValidationRunStatusStub runStatus = getLastValidationRunStatus(runId);
                        ValidationRunSummary run = getValidationRun(runId);
                        DatedSignature signature = getDatedSignature(locale, EventType.VALIDATION_RUN_CREATED, MapBuilder.of(Entity.BUILD, buildId).with(Entity.VALIDATION_STAMP, validationStampId).get());
                        return new BuildValidationStampRun(runId, run.getRunOrder(), signature, runStatus.getStatus(), runStatus.getDescription(),
                                getValidationRunHistory(locale, runId, 0, MAX_EVENTS_IN_BUILD_VALIDATION_STAMP_RUN));
                    }
                }
        );
    }

    @Override
    @Transactional
    @Secured({SecurityRoles.USER, SecurityRoles.CONTROLLER, SecurityRoles.ADMINISTRATOR})
    public Ack addValidationRunComment(int runId, ValidationRunCommentCreationForm form) {
        // Properties
        List<PropertyCreationForm> properties = form.getProperties();
        if (properties != null) {
            for (PropertyCreationForm propertyForm : properties) {
                String propertyValue = propertyForm.getValue();
                if (StringUtils.isNotBlank(propertyValue)) {
                    propertiesService.saveProperty(
                            Entity.VALIDATION_RUN,
                            runId,
                            propertyForm.getExtension(),
                            propertyForm.getName(),
                            propertyValue
                    );
                }
            }
        }
        // Checks the status
        if (StringUtils.isBlank(form.getStatus())) {
            // Does not do anything if empty description
            if (StringUtils.isBlank(form.getDescription())) {
                return Ack.NOK;
            }
            // No status - it means that the user creates a comment
            CommentStub comment = createComment(Entity.VALIDATION_RUN, runId, form.getDescription());
            // Registers an event for this comment
            event(
                    collectEntityContext(Event.of(EventType.VALIDATION_RUN_COMMENT), Entity.VALIDATION_RUN, runId)
                            .withComment(comment.getComment()));
            // OK
            return Ack.OK;
        } else {
            // Tries to get a valid status
            Status s = Status.valueOf(form.getStatus());
            // Creates the new status
            createValidationRunStatus(runId, new ValidationRunStatusCreationForm(s, form.getDescription()), false);
            // OK
            return Ack.OK;
        }
    }

    @Override
    @Transactional
    @Secured({SecurityRoles.USER, SecurityRoles.CONTROLLER, SecurityRoles.ADMINISTRATOR})
    public ValidationRunStatusSummary createValidationRunStatus(int validationRun, ValidationRunStatusCreationForm validationRunStatus, boolean initialStatus) {
        // TODO Validation of the status
        // Author
        Signature signature = securityUtils.getCurrentSignature();
        // Creation
        int id = validationRunStatusDao.createValidationRunStatus(
                validationRun,
                validationRunStatus.getStatus(),
                validationRunStatus.getDescription(),
                signature.getName(),
                signature.getId()
        );
        // Generates an event for the status
        // Only when additional run
        if (!initialStatus) {
            // Validation run
            ValidationRunSummary run = getValidationRun(validationRun);
            // Generates an event
            event(Event.of(EventType.VALIDATION_RUN_STATUS)
                    .withProject(run.getBuild().getBranch().getProject().getId())
                    .withBranch(run.getBuild().getBranch().getId())
                    .withBuild(run.getBuild().getId())
                    .withValidationStamp(run.getValidationStamp().getId())
                    .withValidationRun(run.getId())
                    .withValue("status", validationRunStatus.getStatus().name()));
        }
        // OK
        return new ValidationRunStatusSummary(id, signature.getName(), validationRunStatus.getStatus(), validationRunStatus.getDescription());
    }

    public ValidationRunStatusStub getLastValidationRunStatus(int validationRunId) {
        TValidationRunStatus t = validationRunStatusDao.findLastForValidationRun(validationRunId);
        return new ValidationRunStatusStub(
                t.getId(),
                t.getStatus(),
                t.getDescription()
        );
    }

    // Promoted runs

    @Override
    @Transactional(readOnly = true)
    public PromotedRunSummary getPromotedRun(int buildId, int promotionLevel) {
        TPromotedRun t = promotedRunDao.findByBuildAndPromotionLevel(buildId, promotionLevel);
        if (t != null) {
            return new PromotedRunSummary(
                    t.getId(),
                    t.getDescription(),
                    getBuild(t.getBuild()),
                    getPromotionLevel(t.getPromotionLevel())
            );
        } else {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Promotion> getPromotions(final Locale locale, int promotionLevelId, int offset, int count) {
        // List of `promoted_run` for this promotion level
        List<TPromotedRun> runs = promotedRunDao.findByPromotionLevel(promotionLevelId, offset, count);
        // Gets the promotion level summary
        final PromotionLevelSummary promotionLevel = getPromotionLevel(promotionLevelId);
        // Now
        final DateTime now = TimeUtils.now();
        // Converts them into Promotion objects
        return Lists.transform(
                runs,
                new Function<TPromotedRun, Promotion>() {
                    @Override
                    public Promotion apply(TPromotedRun t) {
                        return new Promotion(
                                promotionLevel,
                                getBuild(t.getBuild()),
                                getPromotedRunDatedSignature(t, locale, now)
                        );
                    }
                }
        );
    }

    private DatedSignature getPromotedRunDatedSignature(TPromotedRun t, Locale locale, DateTime now) {
        return getDatedSignature(
                locale,
                t.getAuthorId(), t.getAuthor(), t.getCreation(),
                now);
    }

    private DatedSignature getDatedSignature(Locale locale, Integer authorId, String author, DateTime time, DateTime now) {
        return new DatedSignature(
                new Signature(
                        authorId,
                        author
                ),
                time,
                TimeUtils.elapsed(strings, locale, time, now, author),
                TimeUtils.format(locale, time)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Promotion getEarliestPromotionForBuild(Locale locale, int buildId, int promotionLevelId) {
        // Branch for the build
        int branchId = getBuild(buildId).getBranch().getId();
        // Branch for the promotion
        PromotionLevelSummary promotionLevel = getPromotionLevel(promotionLevelId);
        int promotionBranchId = promotionLevel.getBranch().getId();
        // ... they must be same
        if (branchId != promotionBranchId) {
            throw new IllegalStateException("Branches for the build and the promotion level must be identical.");
        }
        // Looking for the earliest promoted run
        Integer earliestBuildId = promotedRunDao.findBuildByEarliestPromotion(buildId, promotionLevelId);
        // Not found
        if (earliestBuildId == null) {
            return new Promotion(
                    promotionLevel,
                    null,
                    null
            );
        } else {
            // Gets the promoted run data
            TPromotedRun t = promotedRunDao.findByBuildAndPromotionLevel(earliestBuildId, promotionLevelId);
            return new Promotion(
                    promotionLevel,
                    getBuild(earliestBuildId),
                    getPromotedRunDatedSignature(t, locale, TimeUtils.now())
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Promotion findLastPromotion(Locale locale, int promotionLevelId) {
        BuildSummary build = findLastBuildWithPromotionLevel(promotionLevelId);
        PromotionLevelSummary promotionLevel = getPromotionLevel(promotionLevelId);
        if (build != null) {
            TPromotedRun t = promotedRunDao.findByBuildAndPromotionLevel(build.getId(), promotionLevelId);
            return new Promotion(
                    promotionLevel,
                    build,
                    getPromotedRunDatedSignature(t, locale, TimeUtils.now())
            );
        } else {
            return new Promotion(
                    promotionLevel,
                    null,
                    null
            );
        }
    }


    // Comments

    @Override
    @Transactional
    @Secured({SecurityRoles.USER, SecurityRoles.CONTROLLER, SecurityRoles.ADMINISTRATOR})
    public CommentStub createComment(Entity entity, int id, String content) {
        // Does not do anything if empty content
        if (StringUtils.isBlank(content)) {
            return null;
        }
        // Author
        Signature signature = securityUtils.getCurrentSignature();
        // Insertion
        int commentId = commentDao.createComment(
                entity,
                id,
                content,
                signature.getName(),
                signature.getId()
        );
        // OK
        return new CommentStub(commentId, content);
    }

    // Common

    @Override
    @Transactional(readOnly = true)
    public int getEntityId(Entity entity, String name, final Map<Entity, Integer> parentIds) {
        return entityDao.getEntityId(entity, name, parentIds);
    }

    protected Event collectEntityContext(Event event, Entity entity, int id) {
        Event e = event.withEntity(entity, id);
        // Gets the entities in the content
        List<Entity> parentEntities = entity.getParents();
        for (Entity parentEntity : parentEntities) {
            Integer parentEntityId = entityDao.getParentEntityId(parentEntity, entity, id);
            if (parentEntityId != null) {
                e = collectEntityContext(e, parentEntity, parentEntityId);
            }
        }
        // OK
        return e;
    }

    protected Ack setImage(MultipartFile image, long maxSize, Function<byte[], Ack> imageUpdateFn) {
        // Checks the image type
        String contentType = image.getContentType();
        if (!"image/png".equals(contentType)) {
            throw new ImageIncorrectMIMETypeException(contentType, "image/png");
        }
        // Checks the size
        long imageSize = image.getSize();
        if (imageSize > maxSize) {
            throw new ImageTooBigException(imageSize, maxSize);
        }
        // Gets the bytes
        byte[] content;
        try {
            content = image.getBytes();
        } catch (IOException e) {
            throw new ImageCannotReadException(e);
        }
        // Updates the content
        return imageUpdateFn.apply(content);
    }
}
