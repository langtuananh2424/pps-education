import React, { useState } from "react";
import { NavLink } from "react-router-dom";
import { ChevronDown, ChevronRight, LogOut, X } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { isNavItemAllowed, navSections } from "@/constants/navigation";
import Avatar from "@/components/ui/Avatar";
import { cn } from "@/lib/cn";

export default function Sidebar() {
  const { currentRoleLabel, currentUser, sidebarOpen, setSidebarOpen, hasPermission, logout } = useApp();
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>(
    Object.fromEntries(navSections.map((section) => [section.id, true]))
  );

  const toggleGroup = (group: string) => setExpandedGroups((prev) => ({ ...prev, [group]: !prev[group] }));

  const closeOnMobile = () => {
    if (window.innerWidth < 1024) setSidebarOpen(false);
  };

  return (
    <>
      {sidebarOpen && (
        <div className="fixed inset-0 bg-slate-900/40 z-30 lg:hidden backdrop-blur-xs" onClick={() => setSidebarOpen(false)} />
      )}

      <aside
        className={cn(
          "fixed top-0 bottom-0 left-0 lg:top-4 lg:bottom-4 lg:left-4 lg:h-[calc(100vh-2rem)] z-40 w-72 bg-white text-slate-700 border-r lg:border border-slate-200/90 lg:rounded-3xl lg:shadow-soft flex flex-col transition-transform duration-300 lg:translate-x-0",
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="h-16 px-6 border-b border-slate-200/90 flex items-center justify-between bg-white lg:rounded-t-3xl shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-full bg-white p-[2px] flex items-center justify-center shadow-glow relative shrink-0">
              <div className="w-full h-full rounded-full bg-gradient-to-tr from-[#EA580C] via-[#F68B1F] to-[#FB923C] p-[1px] flex items-center justify-center">
                <div className="w-full h-full rounded-full bg-white flex items-center justify-center text-[#EA580C] font-display font-black text-xs">
                  P
                </div>
              </div>
            </div>
            <div>
              <span className="font-display font-bold text-slate-900 text-sm tracking-tight block">
                PPS <span className="text-brand-orange">VIETNAM</span>
              </span>
              <span className="text-[8px] text-slate-500 font-extrabold tracking-wider block uppercase -mt-0.5">
                CARING INDIVIDUALS
              </span>
            </div>
          </div>
          <button
            onClick={() => setSidebarOpen(false)}
            className="p-1 rounded-md text-slate-400 hover:text-slate-700 hover:bg-slate-200 lg:hidden"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-4 py-6 space-y-4">
          {navSections.map((section) => {
            const visibleItems = section.items.filter((item) => isNavItemAllowed(item, currentUser?.roleCodes ?? [], hasPermission));
            if (visibleItems.length === 0) return null;

            const isExpanded = expandedGroups[section.id];
            return (
              <div key={section.id} className="space-y-1">
                <button
                  onClick={() => toggleGroup(section.id)}
                  className="w-full px-2 py-1.5 flex items-center justify-between text-[11px] font-bold font-display tracking-widest text-slate-400 hover:text-slate-600"
                >
                  <span>{section.title}</span>
                  {isExpanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                </button>

                {isExpanded && (
                  <div className="space-y-[2px] pl-1">
                    {visibleItems.map((item) => {
                      const Icon = item.icon;
                      return (
                        <NavLink
                          key={item.id}
                          to={item.path}
                          onClick={closeOnMobile}
                          className={({ isActive }) =>
                            cn(
                              "w-full px-3 py-2 flex items-center gap-2.5 rounded-md text-xs font-medium transition-all duration-150",
                              isActive
                                ? "bg-brand-gradient text-white shadow-soft font-semibold"
                                : "text-slate-600 hover:text-slate-900 hover:bg-slate-200/80"
                            )
                          }
                        >
                          {({ isActive }) => (
                            <>
                              <Icon className={cn("w-4 h-4 shrink-0", isActive ? "text-white" : "text-slate-400")} />
                              <span className="truncate">{item.label}</span>
                            </>
                          )}
                        </NavLink>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <div className="p-4 border-t border-slate-200/90 bg-white flex flex-col gap-3 lg:rounded-b-3xl shrink-0">
          <div className="flex items-center gap-3">
            <Avatar name={currentUser?.fullName || "U"} />
            <div className="flex-1 min-w-0">
              <span className="text-xs font-bold text-slate-900 block truncate leading-tight">
                {currentUser?.fullName || "Chưa đăng nhập"}
              </span>
              <span className="text-[10px] text-slate-500 block truncate mt-0.5 font-medium">{currentRoleLabel}</span>
            </div>
          </div>

          <button
            onClick={logout}
            className="w-full bg-white border border-slate-200 hover:bg-slate-50 hover:border-slate-300 text-rose-500 hover:text-rose-600 font-bold text-[11px] py-2 rounded-lg flex items-center justify-center gap-2 transition-all cursor-pointer shadow-xs"
          >
            <LogOut className="w-3.5 h-3.5 shrink-0" />
            <span>Đăng xuất hệ thống</span>
          </button>
        </div>
      </aside>
    </>
  );
}
