export interface User {
  id: number;
  employee_code: string;
  first_name: string;
  last_name: string;
  email: string;
  roles: Role[];
  active: boolean;
  last_login: string;
  created_date: string;
  updated_date: string;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface AuthResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  user: User;
}

export interface ChangePasswordRequest {
  current_password: string;
  new_password: string;
  confirm_password: string;
}

export interface Role {
  id: number;
  role_name: string;
  description: string;
  active: boolean;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
  error?: string;
  errorCode?: number;
}

