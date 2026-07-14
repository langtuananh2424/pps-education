import React, { useEffect, useState } from "react";
import { Employee, UserRole } from "@/types";
import { readStoredEmployees, writeStoredEmployees } from "../storage";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import EmployeeListPanel from "../components/EmployeeListPanel";
import EmployeeDetailPanel from "../components/EmployeeDetailPanel";
import EmployeeFormModal, { EmployeeFormValues } from "../components/EmployeeFormModal";

const emptyFormValues: EmployeeFormValues = {
  fullName: "",
  email: "",
  phone: "",
  role: UserRole.TEACHER,
  campusIds: ["CAMP-01"],
  degrees: "",
  certificates: "",
  contractType: "LABOR",
  baseSalary: 15000000,
  status: "ACTIVE"
};

function toFormValues(emp: Employee): EmployeeFormValues {
  return {
    fullName: emp.fullName,
    email: emp.email,
    phone: emp.phone,
    role: emp.role,
    campusIds: emp.campusIds,
    degrees: emp.degrees.join(", "),
    certificates: emp.certificates.join(", "),
    contractType: emp.contracts[0]?.type || "LABOR",
    baseSalary: emp.contracts[0]?.baseSalary || 12000000,
    status: emp.status
  };
}

export default function ProfilesPage() {
  const [employees, setEmployees] = useState<Employee[]>(readStoredEmployees);
  const [selectedEmpId, setSelectedEmpId] = useState(employees[0]?.id || "");
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<Employee | null>(null);
  const { message, showToast } = useToast();

  useEffect(() => {
    writeStoredEmployees(employees);
  }, [employees]);

  const selectedEmp = employees.find((e) => e.id === selectedEmpId) || employees[0];

  const handleAddEmployee = (values: EmployeeFormValues) => {
    const newId = `EMP-0${employees.length + 1 < 10 ? "0" : ""}${employees.length + 1}`;
    const employeeRecord: Employee = {
      id: newId,
      fullName: values.fullName,
      email: values.email,
      phone: values.phone,
      role: values.role,
      campusIds: values.campusIds,
      degrees: values.degrees ? values.degrees.split(",").map((s) => s.trim()).filter(Boolean) : [],
      certificates: values.certificates ? values.certificates.split(",").map((s) => s.trim()).filter(Boolean) : [],
      contracts: [{ id: `CTR-0${employees.length + 1 < 10 ? "0" : ""}${employees.length + 1}`, type: values.contractType, startDate: new Date().toISOString().substring(0, 10), baseSalary: values.baseSalary }],
      rewards: [],
      disciplines: [],
      joinedDate: new Date().toISOString().substring(0, 10),
      status: "ACTIVE"
    };

    setEmployees((prev) => [...prev, employeeRecord]);
    setSelectedEmpId(newId);
    setShowCreateModal(false);
    showToast("Đã thêm mới cán bộ thành công!");
  };

  const handleUpdateEmployee = (values: EmployeeFormValues) => {
    if (!editingEmployee) return;
    setEmployees((prev) =>
      prev.map((emp) => {
        if (emp.id !== editingEmployee.id) return emp;
        const updatedContracts = [...emp.contracts];
        if (updatedContracts.length > 0) {
          updatedContracts[0] = { ...updatedContracts[0], type: values.contractType, baseSalary: values.baseSalary };
        } else {
          updatedContracts.push({ id: `CTR-${Date.now().toString().substring(8)}`, type: values.contractType, startDate: emp.joinedDate, baseSalary: values.baseSalary });
        }
        return {
          ...emp,
          fullName: values.fullName,
          email: values.email,
          phone: values.phone,
          role: values.role,
          campusIds: values.campusIds,
          degrees: values.degrees ? values.degrees.split(",").map((s) => s.trim()).filter(Boolean) : [],
          certificates: values.certificates ? values.certificates.split(",").map((s) => s.trim()).filter(Boolean) : [],
          contracts: updatedContracts,
          status: values.status
        };
      })
    );
    setEditingEmployee(null);
    showToast("Đã cập nhật thông tin cán bộ thành công!");
  };

  const handleToggleStatus = (empId: string) => {
    const emp = employees.find((e) => e.id === empId);
    if (!emp) return;
    const nextStatus = emp.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    setEmployees((prev) => prev.map((e) => (e.id === empId ? { ...e, status: nextStatus } : e)));
    showToast(`Đã ${nextStatus === "ACTIVE" ? "kích hoạt lại" : "tắt hoạt động"} cán bộ ${emp.fullName}!`);
  };

  const handleAddReward = (empId: string, text: string) => {
    setEmployees((prev) => prev.map((emp) => (emp.id === empId ? { ...emp, rewards: [...emp.rewards, text] } : emp)));
    showToast("Đã thêm quyết định khen thưởng!");
  };

  const handleAddDiscipline = (empId: string, text: string) => {
    setEmployees((prev) => prev.map((emp) => (emp.id === empId ? { ...emp, disciplines: [...emp.disciplines, text] } : emp)));
    showToast("Đã ghi nhận quyết định kỷ luật!");
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Phân hệ Quản lý Nhân sự & Tiền lương (HRM)</h1>
        <p className="text-xs text-slate-500 mt-1">
          Lưu hồ sơ cán bộ đa cơ sở, kiểm soát thiết bị chấm công tự động, tính lương tự động dựa trên ngày công (UC-08/09/10/11/12).
        </p>
      </div>

      <Toast message={message} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <EmployeeListPanel
          employees={employees}
          selectedEmpId={selectedEmp?.id || ""}
          onSelect={setSelectedEmpId}
          onCreate={() => setShowCreateModal(true)}
          onEdit={setEditingEmployee}
          onToggleStatus={handleToggleStatus}
        />

        {selectedEmp && (
          <EmployeeDetailPanel
            employee={selectedEmp}
            onToggleStatus={handleToggleStatus}
            onEdit={setEditingEmployee}
            onAddReward={handleAddReward}
            onAddDiscipline={handleAddDiscipline}
          />
        )}
      </div>

      {showCreateModal && (
        <EmployeeFormModal mode="create" initialValues={emptyFormValues} onClose={() => setShowCreateModal(false)} onSubmit={handleAddEmployee} />
      )}

      {editingEmployee && (
        <EmployeeFormModal mode="edit" initialValues={toFormValues(editingEmployee)} onClose={() => setEditingEmployee(null)} onSubmit={handleUpdateEmployee} />
      )}
    </div>
  );
}
