package demo.unjuanable.application.orgmng.empservice;

import demo.unjuanable.domain.orgmng.emp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Transactional
class EmpServiceIT {
    private static final long DEFAULT_USER_ID = 1L;
    private static final long DEFAULT_TENANT_ID = 1L;
    private static final long DEFAULT_ORG_ID = 1L;
    private static final String DEFAULT_EMP_STATUS_CODE = EmpStatus.REGULAR.code();
    private static final String DEFAULT_EMP_NAME = "Kline";
    private static final LocalDate DEFAULT_DOB = LocalDate.of(1980, 1, 1);
    private static final String DEFAULT_GENDER_CODE = Gender.MALE.code();
    private static final String DEFAULT_ID_NUM = "123456789012345678";

    private static final long JAVA_TYPE_ID = 1L;
    public static final String JAVA_LEVEL_CODE = "MED";
    public static final int JAVA_DURATION = 3;

    public static final long PYTHON_TYPE_ID = 2L;
    public static final String PYTHON_LEVEL_CODE = "ADV";
    public static final int PYTHON_DURATION = 10;

    public static final long CPP_TYPE_ID = 3L;
    public static final String CPP_LEVEL_CODE = "BEG";
    public static final int CPP_DURATION = 1;

    public static final long GOLANG_TYPE_ID = 4L;
    public static final String GOLANG_LEVEL_CODE = "MED";
    public static final int GOLANG_DURATION = 4;

    @Autowired
    private EmpService empService;

    @Autowired
    private EmpRepository empRepository;

    @Test
    void addEmp_shouldAddEmpWithSkills() {
        // given
        CreateEmpRequest request = buildCreateEmpRequest();

        // when
        EmpResponse empResponse = empService.addEmp(request, DEFAULT_USER_ID);

        // then
        Emp actual = empRepository.findById(DEFAULT_TENANT_ID, empResponse.getId())
                .orElseGet(() -> fail("找不到新增的员工！"));

        Emp expected = buildExpectedCreatedEmp(request, empResponse.getId());

        assertEmp(actual, expected);
    }

    @Test
    void updateEmp_shouldSuccess_WhenUpdateEmpName_AndRemovePythonSkill_AndAddGolangSkill_AndUpdateCppSkill() {
        // given
        Emp origionEmp = prepareEmpInDb();

        // when
        UpdateEmpRequest updateRequest = buildUpdateEmpRequest(origionEmp);
        empService.updateEmp(origionEmp.getId(), updateRequest, origionEmp.getTenantId());

        // then
        Emp actual = empRepository.findById(origionEmp.getTenantId(), origionEmp.getId())
                .orElseGet(() -> fail("找不到刚刚修改的员工！"));
        RebuiltEmp expected = buildExpectedUpdatedEmp(origionEmp, updateRequest);

        assertEmp(actual, expected);
    }

    private CreateEmpRequest buildCreateEmpRequest() {

        return new CreateEmpRequest()
                .setTenantId(DEFAULT_TENANT_ID)
                .setIdNum(DEFAULT_ID_NUM)
                .setName(DEFAULT_EMP_NAME)
                .setGenderCode(DEFAULT_GENDER_CODE)
                .setDob(DEFAULT_DOB)
                .setOrgId(DEFAULT_ORG_ID)
                .setStatusCode(DEFAULT_EMP_STATUS_CODE)
                .addSkill(JAVA_TYPE_ID, JAVA_LEVEL_CODE, JAVA_DURATION)
                .addSkill(PYTHON_TYPE_ID, PYTHON_LEVEL_CODE, PYTHON_DURATION)
                .addSkill(CPP_TYPE_ID, CPP_LEVEL_CODE, CPP_DURATION);
    }

    private UpdateEmpRequest buildUpdateEmpRequest(Emp origin) {

        return emp2UpdateRequest(origin)
                .setName("Dunne")
                .removeSkill(PYTHON_TYPE_ID)
                .addSkill(GOLANG_TYPE_ID, GOLANG_LEVEL_CODE, GOLANG_DURATION)
                .updateSkill(CPP_TYPE_ID, CPP_LEVEL_CODE, CPP_DURATION + 1);
    }

    private Emp buildExpectedCreatedEmp(CreateEmpRequest request, Long id) {
        RebuiltEmp result = new RebuiltEmp(request.getTenantId()
                , id
                , LocalDateTime.now()
                , DEFAULT_USER_ID
        )
                .resetStatus(EmpStatus.ofCode(DEFAULT_EMP_STATUS_CODE))
                .resetOrgId(request.getOrgId())
                .resetDob(request.getDob())
                .resetGender(Gender.ofCode(request.getGenderCode()))
                .resetName(request.getName())
                .resetIdNum(request.getIdNum());

        request.getSkills().forEach(
                skill -> result.reAddSkill(
                        skill.getId()
                        , skill.getSkillTypeId()
                        , SkillLevel.ofCode(skill.getLevelCode())
                        , skill.getDuration()
                        , DEFAULT_USER_ID
                )
        );
        return result;
    }

    private RebuiltEmp buildExpectedUpdatedEmp(Emp origionEmp, UpdateEmpRequest updateRequest) {
        RebuiltEmp expected = cloneEmp(origionEmp);

        expected.resetDob(updateRequest.getDob())
                .resetEmpNum(updateRequest.getEmpNum())
                .resetGender(Gender.ofCode(updateRequest.getGenderCode()))
                .resetName(updateRequest.getName())
                .resetIdNum(updateRequest.getIdNum());

        expected.reAddSkill(null
                        , GOLANG_TYPE_ID
                        , SkillLevel.ofCode(GOLANG_LEVEL_CODE)
                        , GOLANG_DURATION
                        , DEFAULT_USER_ID)
                .reUpdateSkill(CPP_TYPE_ID
                        , SkillLevel.ofCode(CPP_LEVEL_CODE)
                        , CPP_DURATION + 1
                        , DEFAULT_USER_ID)
                .deleteSkillCompletely(PYTHON_TYPE_ID);
        return expected;
    }

    private void assertEmp(Emp actual, Emp expected) {
        assertThat(actual).usingRecursiveComparison()
                .ignoringFields("skills"
                        , "empNum"
                        , "createdAt"
                        , "createdBy"
                        , "updatedAt"
                        , "updatedBy"
                        , "version")
                .isEqualTo(expected);

        assertThat(actual.getSkills()).usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringExpectedNullFields() // this is because if of the new skill is null in request
                .comparingOnlyFields("id", "tenantId", "empId", "skillTypeId", "level", "duration")
                .isEqualTo(expected.getSkills());
    }

    private Emp prepareEmpInDb() {
        CreateEmpRequest createRequest = buildCreateEmpRequest();
        EmpResponse response = empService.addEmp(createRequest, DEFAULT_TENANT_ID);
        return empRepository.findById(response.getTenantId(), response.getId())
                .orElseGet(() -> fail("找不到新增的员工！"));
    }

    private RebuiltEmp cloneEmp(Emp origionEmp) {
        RebuiltEmp expected = new RebuiltEmp(origionEmp.getTenantId()
                , origionEmp.getId()
                , LocalDateTime.now()
                , origionEmp.getCreatedBy()
        )
                .resetStatus(origionEmp.getStatus())
                .resetOrgId(origionEmp.getOrgId())
                .resetDob(origionEmp.getDob())
                .resetEmpNum(origionEmp.getEmpNum())
                .resetGender(origionEmp.getGender())
                .resetName(origionEmp.getName())
                .resetIdNum(origionEmp.getIdNum());

        origionEmp.getSkills().forEach(
                skill -> expected.reAddSkill(
                        skill.getId()
                        , skill.getSkillTypeId()
                        , skill.getLevel()
                        , skill.getDuration()
                        , DEFAULT_USER_ID
                )
        );
        return expected;
    }

    private UpdateEmpRequest emp2UpdateRequest(Emp origin) {
        UpdateEmpRequest result = new UpdateEmpRequest();

        result.setTenantId(origin.getTenantId())
                .setIdNum(origin.getIdNum())
                .setName(origin.getName())
                .setGenderCode(origin.getGender().code())
                .setDob(origin.getDob())
                .setEmpNum(origin.getEmpNum());

        origin.getSkills().forEach(skill ->
                result.addSkill(skill.getId()
                        , skill.getSkillTypeId()
                        , skill.getLevel().code()
                        , skill.getDuration()
                )
        );
        return result;
    }
}