package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Commendation;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeHistory;
import vn.com.pps.education.domain.EmploymentContract;
import vn.com.pps.education.domain.EmploymentContractHistory;
import vn.com.pps.education.domain.Position;
import vn.com.pps.education.domain.Qualification;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CommendationResponse;
import vn.com.pps.education.dto.CreateCommendationRequest;
import vn.com.pps.education.dto.CreateEmployeeRequest;
import vn.com.pps.education.dto.CreateEmploymentContractRequest;
import vn.com.pps.education.dto.CreateQualificationRequest;
import vn.com.pps.education.dto.EmployeeResponse;
import vn.com.pps.education.dto.EmploymentContractResponse;
import vn.com.pps.education.dto.ExpiringContractResponse;
import vn.com.pps.education.dto.QualificationResponse;
import vn.com.pps.education.dto.UpdateEmployeeRequest;
import vn.com.pps.education.dto.UpdateEmploymentContractRequest;
import vn.com.pps.education.exception.ActiveContractAlreadyExistsException;
import vn.com.pps.education.exception.DuplicateContractNumberException;
import vn.com.pps.education.exception.DuplicateEmployeeCodeException;
import vn.com.pps.education.exception.EmployeeAlreadyExistsException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.CommendationRepository;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeHistoryRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmploymentContractHistoryRepository;
import vn.com.pps.education.repository.EmploymentContractRepository;
import vn.com.pps.education.repository.PositionRepository;
import vn.com.pps.education.repository.QualificationRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-08: Quản lý hồ sơ nhân sự (FR-HRM-01).
 * Xem docs/uc/phan-he-04-nhan-su.md — Main Flow bước 1-5, A2 (cảnh báo hợp
 * đồng sắp/đã hết hạn). A1 (nhân sự làm nhiều điểm trường) không cần code ở
 * đây — phân công điểm trường thuộc Phân hệ Cơ sở vật chất/Học thuật.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmploymentContractRepository employmentContractRepository;
    private final QualificationRepository qualificationRepository;
    private final CommendationRepository commendationRepository;
    private final EmployeeHistoryRepository employeeHistoryRepository;
    private final EmploymentContractHistoryRepository employmentContractHistoryRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final UserAccountService userAccountService;
    private final PositionRoleSyncService positionRoleSyncService;

    public EmployeeService(EmployeeRepository employeeRepository,
                            EmploymentContractRepository employmentContractRepository,
                            QualificationRepository qualificationRepository,
                            CommendationRepository commendationRepository,
                            EmployeeHistoryRepository employeeHistoryRepository,
                            EmploymentContractHistoryRepository employmentContractHistoryRepository,
                            UserRepository userRepository,
                            DepartmentRepository departmentRepository,
                            PositionRepository positionRepository,
                            UserAccountService userAccountService,
                            PositionRoleSyncService positionRoleSyncService) {
        this.employeeRepository = employeeRepository;
        this.employmentContractRepository = employmentContractRepository;
        this.qualificationRepository = qualificationRepository;
        this.commendationRepository = commendationRepository;
        this.employeeHistoryRepository = employeeHistoryRepository;
        this.employmentContractHistoryRepository = employmentContractHistoryRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.userAccountService = userAccountService;
        this.positionRoleSyncService = positionRoleSyncService;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> search(String query, Long departmentId) {
        List<Employee> employees = query == null || query.isBlank()
                ? employeeRepository.findAllActive(departmentId)
                : employeeRepository.searchByQuery(query.trim(), departmentId);
        return employees.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        return toResponse(getEmployeeOrThrow(id));
    }

    /**
     * Main Flow bước 1-2: khởi tạo hồ sơ nhân sự mới. Nhận ĐÚNG 1 trong 2:
     * userId (tài khoản có sẵn) hoặc newAccount (tạo tài khoản kèm hồ sơ
     * trong cùng transaction — cơ chế UC-43/FR-USR-01, xem ghi chú UC-08).
     */
    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request, Long actorUserId) {
        if ((request.userId() == null) == (request.newAccount() == null)) {
            throw new IllegalArgumentException("Cung cấp đúng 1 trong 2: userId (tài khoản có sẵn) hoặc newAccount (tạo tài khoản mới).");
        }
        User user = request.userId() != null
                ? userRepository.findById(request.userId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + request.userId()))
                : userAccountService.createAccount(request.newAccount());
        if (employeeRepository.findByUserId(user.getId()).isPresent()) {
            throw new EmployeeAlreadyExistsException("Tài khoản id=" + user.getId() + " đã có hồ sơ nhân sự.");
        }
        if (employeeRepository.findByEmployeeCode(request.employeeCode()).isPresent()) {
            throw new DuplicateEmployeeCodeException("Mã nhân sự đã tồn tại: " + request.employeeCode());
        }

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode(request.employeeCode());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setIdCardNumber(request.idCardNumber());
        employee.setIdCardIssuedDate(request.idCardIssuedDate());
        employee.setIdCardIssuedPlace(request.idCardIssuedPlace());
        employee.setPermanentAddress(request.permanentAddress());
        employee.setCurrentAddress(request.currentAddress());
        employee.setBankAccountNumber(request.bankAccountNumber());
        employee.setBankName(request.bankName());
        employee.setTaxCode(request.taxCode());
        employee.setSocialInsuranceNumber(request.socialInsuranceNumber());
        employee.setEmployeeType(Employee.EmployeeType.valueOf(request.employeeType()));
        Position position = resolvePosition(request.positionId());
        employee.setPosition(position);
        if (request.departmentId() != null) {
            Department department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy phòng ban id=" + request.departmentId()));
            employee.setDepartment(department);
        }
        employee.setManagement(Boolean.TRUE.equals(request.isManagement()));
        employee.setDefaultShiftRequired(request.isDefaultShiftRequired() == null || request.isDefaultShiftRequired());
        employee.setHireDate(request.hireDate());
        employee = employeeRepository.save(employee);

        // UC-08 A5 (FR-HRM-06/UC-52) -- hồ sơ vừa tạo nên chưa từng có chức vụ (oldPositionId = null).
        positionRoleSyncService.sync(user, null, position, actorUserId);

        writeEmployeeHistory(employee, actorUserId, EmployeeHistory.Action.CREATED);
        return toResponse(employee);
    }

    /** Main Flow bước 2: cập nhật thông tin cá nhân của hồ sơ đã có, giữ lịch sử phiên bản (bước 5). */
    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest request, Long actorUserId) {
        Employee employee = getEmployeeOrThrow(id);
        Long oldPositionId = employee.getPosition() == null ? null : employee.getPosition().getId();

        employee.setDateOfBirth(request.dateOfBirth());
        employee.setIdCardNumber(request.idCardNumber());
        employee.setIdCardIssuedDate(request.idCardIssuedDate());
        employee.setIdCardIssuedPlace(request.idCardIssuedPlace());
        employee.setPermanentAddress(request.permanentAddress());
        employee.setCurrentAddress(request.currentAddress());
        employee.setBankAccountNumber(request.bankAccountNumber());
        employee.setBankName(request.bankName());
        employee.setTaxCode(request.taxCode());
        employee.setSocialInsuranceNumber(request.socialInsuranceNumber());
        employee.setEmployeeType(Employee.EmployeeType.valueOf(request.employeeType()));
        Position newPosition = resolvePosition(request.positionId());
        employee.setPosition(newPosition);
        if (request.departmentId() != null) {
            Department department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy phòng ban id=" + request.departmentId()));
            employee.setDepartment(department);
        } else {
            employee.setDepartment(null); // A1 -- bỏ trống phòng ban.
        }
        employee.setManagement(Boolean.TRUE.equals(request.isManagement()));
        employee.setDefaultShiftRequired(request.isDefaultShiftRequired() == null || request.isDefaultShiftRequired());
        employee.setStatus(Employee.Status.valueOf(request.status()));
        employee.setTerminationDate(request.terminationDate());
        employee = employeeRepository.save(employee);

        // UC-08 A5 (FR-HRM-06/UC-52) -- đồng bộ role theo chức vụ mới, thu hồi role tự gán theo chức vụ cũ nếu không còn phù hợp.
        positionRoleSyncService.sync(employee.getUser(), oldPositionId, newPosition, actorUserId);

        writeEmployeeHistory(employee, actorUserId, EmployeeHistory.Action.UPDATED);
        return toResponse(employee);
    }

    /** Main Flow bước 2: thêm 1 bằng cấp/chứng chỉ. Không có lịch sử (bảng phụ — theo SDD). */
    @Transactional
    public QualificationResponse addQualification(Long employeeId, CreateQualificationRequest request) {
        Employee employee = getEmployeeOrThrow(employeeId);

        Qualification qualification = new Qualification();
        qualification.setEmployee(employee);
        qualification.setQualificationType(Qualification.QualificationType.valueOf(request.qualificationType()));
        qualification.setTitle(request.title());
        qualification.setIssuer(request.issuer());
        qualification.setIssuedDate(request.issuedDate());
        qualification.setExpiryDate(request.expiryDate());
        qualification.setFileUrl(request.fileUrl());
        return toResponse(qualificationRepository.save(qualification));
    }

    @Transactional(readOnly = true)
    public List<QualificationResponse> listQualifications(Long employeeId) {
        getEmployeeOrThrow(employeeId);
        return qualificationRepository.findByEmployeeId(employeeId).stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 4: ghi nhận 1 sự kiện khen thưởng/kỷ luật. Mỗi record độc lập, không sửa (theo SDD). */
    @Transactional
    public CommendationResponse addCommendation(Long employeeId, CreateCommendationRequest request, Long actorUserId) {
        Employee employee = getEmployeeOrThrow(employeeId);
        User decidedBy = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        Commendation commendation = new Commendation();
        commendation.setEmployee(employee);
        commendation.setRecordType(Commendation.RecordType.valueOf(request.recordType()));
        commendation.setRecordDate(request.recordDate());
        commendation.setTitle(request.title());
        commendation.setAmount(request.amount());
        commendation.setDecidedBy(decidedBy);
        return toResponse(commendationRepository.save(commendation));
    }

    @Transactional(readOnly = true)
    public List<CommendationResponse> listCommendations(Long employeeId) {
        getEmployeeOrThrow(employeeId);
        return commendationRepository.findByEmployeeId(employeeId).stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 3: thêm hợp đồng lao động. Enforce ràng buộc 1 hợp đồng ACTIVE/nhân sự (SDD). */
    @Transactional
    public EmploymentContractResponse addContract(Long employeeId, CreateEmploymentContractRequest request,
                                                    Long actorUserId) {
        Employee employee = getEmployeeOrThrow(employeeId);
        if (employmentContractRepository.findByContractNumber(request.contractNumber()).isPresent()) {
            throw new DuplicateContractNumberException("Số hợp đồng đã tồn tại: " + request.contractNumber());
        }
        EmploymentContract.Status status = EmploymentContract.Status.valueOf(request.status());
        if (status == EmploymentContract.Status.ACTIVE) {
            assertNoActiveContract(employeeId);
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        EmploymentContract contract = new EmploymentContract();
        contract.setEmployee(employee);
        contract.setContractNumber(request.contractNumber());
        contract.setContractType(EmploymentContract.ContractType.valueOf(request.contractType()));
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setBaseSalary(request.baseSalary());
        contract.setSalaryType(EmploymentContract.SalaryType.valueOf(request.salaryType()));
        contract.setStatus(status);
        contract.setFileUrl(request.fileUrl());
        contract.setCreatedBy(actor);
        contract = employmentContractRepository.save(contract);

        writeContractHistory(contract, actor, EmploymentContractHistory.Action.CREATED);
        return toResponse(contract);
    }

    /** Main Flow bước 3: chỉnh sửa hợp đồng đã có, giữ lịch sử phiên bản (bước 5). */
    @Transactional
    public EmploymentContractResponse updateContract(Long employeeId, Long contractId,
                                                       UpdateEmploymentContractRequest request, Long actorUserId) {
        EmploymentContract contract = employmentContractRepository.findByIdAndDeletedAtIsNull(contractId)
                .filter(c -> c.getEmployee().getId().equals(employeeId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng id=" + contractId));

        EmploymentContract.Status newStatus = EmploymentContract.Status.valueOf(request.status());
        if (newStatus == EmploymentContract.Status.ACTIVE && contract.getStatus() != EmploymentContract.Status.ACTIVE) {
            assertNoActiveContract(employeeId);
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        contract.setContractType(EmploymentContract.ContractType.valueOf(request.contractType()));
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setBaseSalary(request.baseSalary());
        contract.setSalaryType(EmploymentContract.SalaryType.valueOf(request.salaryType()));
        contract.setStatus(newStatus);
        contract.setFileUrl(request.fileUrl());
        contract = employmentContractRepository.save(contract);

        writeContractHistory(contract, actor, EmploymentContractHistory.Action.UPDATED);
        return toResponse(contract);
    }

    @Transactional(readOnly = true)
    public List<EmploymentContractResponse> listContracts(Long employeeId) {
        getEmployeeOrThrow(employeeId);
        return employmentContractRepository.findByEmployeeIdAndDeletedAtIsNullOrderByStartDateDesc(employeeId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** A2 — hợp đồng ACTIVE sắp/đã hết hạn trong vòng withinDays ngày kể từ hôm nay. */
    @Transactional(readOnly = true)
    public List<ExpiringContractResponse> listExpiringContracts(int withinDays) {
        LocalDate threshold = LocalDate.now().plusDays(withinDays);
        return employmentContractRepository
                .findByDeletedAtIsNullAndStatusAndEndDateIsNotNullAndEndDateLessThanEqualOrderByEndDateAsc(
                        EmploymentContract.Status.ACTIVE, threshold)
                .stream()
                .map(c -> new ExpiringContractResponse(
                        c.getId(),
                        c.getEmployee().getId(),
                        c.getEmployee().getEmployeeCode(),
                        c.getEmployee().getUser().getFullName(),
                        c.getContractNumber(),
                        c.getEndDate()))
                .toList();
    }

    private void assertNoActiveContract(Long employeeId) {
        if (employmentContractRepository
                .findByEmployeeIdAndStatusAndDeletedAtIsNull(employeeId, EmploymentContract.Status.ACTIVE)
                .isPresent()) {
            throw new ActiveContractAlreadyExistsException(
                    "Nhân sự id=" + employeeId + " đã có hợp đồng ACTIVE — chấm dứt/kết thúc hợp đồng cũ trước.");
        }
    }

    private void writeEmployeeHistory(Employee employee, Long actorUserId, EmployeeHistory.Action action) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        EmployeeHistory history = new EmployeeHistory();
        history.setEmployee(employee);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(employeeSnapshot(employee));
        employeeHistoryRepository.save(history);
    }

    private void writeContractHistory(EmploymentContract contract, User actor, EmploymentContractHistory.Action action) {
        EmploymentContractHistory history = new EmploymentContractHistory();
        history.setContract(contract);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(contractSnapshot(contract));
        employmentContractHistoryRepository.save(history);
    }

    private Map<String, Object> employeeSnapshot(Employee e) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeCode", e.getEmployeeCode());
        snapshot.put("dateOfBirth", String.valueOf(e.getDateOfBirth()));
        snapshot.put("idCardNumber", e.getIdCardNumber());
        snapshot.put("employeeType", e.getEmployeeType().name());
        snapshot.put("positionCode", e.getPosition() == null ? null : e.getPosition().getCode());
        snapshot.put("hireDate", String.valueOf(e.getHireDate()));
        snapshot.put("terminationDate", e.getTerminationDate() == null ? null : e.getTerminationDate().toString());
        snapshot.put("status", e.getStatus().name());
        return snapshot;
    }

    private Map<String, Object> contractSnapshot(EmploymentContract c) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contractNumber", c.getContractNumber());
        snapshot.put("contractType", c.getContractType().name());
        snapshot.put("startDate", String.valueOf(c.getStartDate()));
        snapshot.put("endDate", c.getEndDate() == null ? null : c.getEndDate().toString());
        snapshot.put("baseSalary", c.getBaseSalary());
        snapshot.put("salaryType", c.getSalaryType().name());
        snapshot.put("status", c.getStatus().name());
        return snapshot;
    }

    private Employee getEmployeeOrThrow(Long id) {
        return employeeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự id=" + id));
    }

    private Position resolvePosition(Long positionId) {
        if (positionId == null) {
            return null;
        }
        return positionRepository.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chức vụ id=" + positionId));
    }

    private EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(
                e.getId(),
                e.getUser().getId(),
                e.getUser().getFullName(),
                e.getEmployeeCode(),
                e.getDateOfBirth(),
                e.getIdCardNumber(),
                e.getIdCardIssuedDate(),
                e.getIdCardIssuedPlace(),
                e.getPermanentAddress(),
                e.getCurrentAddress(),
                e.getBankAccountNumber(),
                e.getBankName(),
                e.getTaxCode(),
                e.getSocialInsuranceNumber(),
                e.getEmployeeType().name(),
                e.getPosition() == null ? null : e.getPosition().getId(),
                e.getPosition() == null ? null : e.getPosition().getName(),
                e.getDepartment() == null ? null : e.getDepartment().getId(),
                e.isManagement(),
                e.isDefaultShiftRequired(),
                e.getHireDate(),
                e.getTerminationDate(),
                e.getStatus().name());
    }

    private EmploymentContractResponse toResponse(EmploymentContract c) {
        return new EmploymentContractResponse(
                c.getId(), c.getEmployee().getId(), c.getContractNumber(), c.getContractType().name(),
                c.getStartDate(), c.getEndDate(), c.getBaseSalary(), c.getSalaryType().name(),
                c.getStatus().name(), c.getFileUrl());
    }

    private QualificationResponse toResponse(Qualification q) {
        return new QualificationResponse(
                q.getId(), q.getEmployee().getId(), q.getQualificationType().name(), q.getTitle(), q.getIssuer(),
                q.getIssuedDate(), q.getExpiryDate(), q.getFileUrl());
    }

    private CommendationResponse toResponse(Commendation c) {
        return new CommendationResponse(
                c.getId(), c.getEmployee().getId(), c.getRecordType().name(), c.getRecordDate(), c.getTitle(),
                c.getAmount(), c.getDecidedBy() == null ? null : c.getDecidedBy().getId());
    }
}
