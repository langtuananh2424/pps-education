export interface PermissionGroup {
  id: string;
  code: string;
  name: string;
  module: string;
  description: string;
  permissions: string[];
  status: "active" | "inactive";
  createdBy?: string;
  updatedAt?: string;
}

export interface BusinessModule {
  id: string;
  name: string;
  permissions: string[];
}
