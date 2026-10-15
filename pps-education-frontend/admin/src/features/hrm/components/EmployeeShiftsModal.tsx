import React, { useEffect, useState } from "react";
import { Pencil, Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import { Badge, Button, TableContainer, Td, Th } from "@/components/ui";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import FloatingError from "@/components/ui/FloatingError";
import { useDialog } from "@/components/ui/DialogProvider";
import {
  deleteEmployeeShift,
  EmployeeResponse,
  EmployeeShiftResponse,
  listEmployeeShiftHistory,
  ShiftResponse,
  updateEmployeeShift
} from "../api";

interface EmployeeShiftsModalProps {
  employee: EmployeeResponse;
  shifts: ShiftResponse[];
  onClose: () => void;
  onChanged: () => void;
}

/**
 * UC-70 (bổ sung): xem toàn bộ các ca đã/đang gán của 1 nhân sự, sửa (đổi ca, ngày áp dụng,
 * ngày kết thúc) hoặc xoá từng bản ghi gán ca. Không động tới dữ liệu chấm công đã ghi.
 */
export default function EmployeeShiftsModal({ employee, shifts, onClose, onChanged }: EmployeeShiftsModalProps) {
  const { t } = useTranslation("hrm-shifts");
  const { confirmDialog } = useDialog();
  const [rows, setRows] = useState<EmployeeShiftResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [shiftId, setShiftId] = useState<number | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [saving, setSaving] = useState(false);

  const load = () => {
    setLoading(true);
    listEmployeeShiftHistory(employee.id)
      .then(setRows)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("employeeShifts.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [employee.id]);

  const startEdit = (r: EmployeeShiftResponse) => {
    setEditingId(r.id);
    setShiftId(r.shiftId);
    setFrom(r.effectiveFrom);
    setTo(r.effectiveTo ?? "");
  };

  const handleSave = async () => {
    if (editingId == null || shiftId === "" || !from) {
      setError(t("employeeShifts.required"));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateEmployeeShift(editingId, { shiftId, effectiveFrom: from, effectiveTo: to || null });
      setEditingId(null);
      load();
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("employeeShifts.saveError"));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (r: EmployeeShiftResponse) => {
    if (!(await confirmDialog(t("employeeShifts.deleteConfirm", { name: r.shiftName })))) return;
    try {
      await deleteEmployeeShift(r.id);
      load();
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("employeeShifts.deleteError"));
    }
  };

  return (
    <Modal open onClose={onClose} title={t("employeeShifts.title", { name: employee.fullName })} size="lg">
      <div className="space-y-3">
        <FloatingError message={error} onClose={() => setError(null)} />
        <TableContainer>
          <thead>
            <tr>
              <Th>{t("employeeShifts.columns.shift")}</Th>
              <Th>{t("employeeShifts.columns.from")}</Th>
              <Th>{t("employeeShifts.columns.to")}</Th>
              <Th>{""}</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <Td colSpan={4} className="text-center text-slate-400">
                  {t("employeeShifts.loading")}
                </Td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <Td colSpan={4} className="text-center text-slate-400">
                  {t("employeeShifts.empty")}
                </Td>
              </tr>
            ) : (
              rows.map((r) =>
                editingId === r.id ? (
                  <tr key={r.id} className="bg-slate-50/60">
                    <Td>
                      <Select
                        value={shiftId}
                        onChange={(e) => setShiftId(e.target.value === "" ? "" : Number(e.target.value))}
                        className="bg-white border border-slate-200 text-sm p-2 rounded-lg focus:outline-none"
                      >
                        {shifts
                          .filter((s) => s.active || s.id === r.shiftId)
                          .map((s) => (
                            <option key={s.id} value={s.id}>
                              {s.name}
                            </option>
                          ))}
                      </Select>
                    </Td>
                    <Td>
                      <div className="w-36">
                        <DatePicker value={from} onChange={setFrom} />
                      </div>
                    </Td>
                    <Td>
                      <div className="w-36">
                        <DatePicker value={to} onChange={setTo} min={from || undefined} placeholder={t("employeeShifts.ongoing")} />
                      </div>
                    </Td>
                    <Td>
                      <div className="flex gap-1.5 justify-end">
                        <Button size="sm" variant="primary" disabled={saving} onClick={handleSave}>
                          {t("employeeShifts.save")}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setEditingId(null)}>
                          {t("employeeShifts.cancel")}
                        </Button>
                      </div>
                    </Td>
                  </tr>
                ) : (
                  <tr key={r.id}>
                    <Td className="font-bold text-slate-800">{r.shiftName}</Td>
                    <Td>{r.effectiveFrom}</Td>
                    <Td>{r.effectiveTo ?? <Badge variant="success">{t("employeeShifts.ongoing")}</Badge>}</Td>
                    <Td>
                      <div className="flex gap-1.5 justify-end">
                        <Button size="sm" variant="ghost" onClick={() => startEdit(r)}>
                          <Pencil className="w-3.5 h-3.5" />
                          {t("employeeShifts.edit")}
                        </Button>
                        <Button size="sm" variant="danger" onClick={() => handleDelete(r)}>
                          <Trash2 className="w-3.5 h-3.5" />
                          {t("employeeShifts.delete")}
                        </Button>
                      </div>
                    </Td>
                  </tr>
                )
              )
            )}
          </tbody>
        </TableContainer>
      </div>
    </Modal>
  );
}
