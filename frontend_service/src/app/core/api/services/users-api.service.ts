import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, buildApiUrl } from '../api.config';
import {
  ChangePasswordRequest,
  MessageResponse,
  ProfileUpdateRequest,
  StaffStatusRequest,
  UserCreationRequest,
  UserResponse,
  EnterpriseProfile, EnterpriseUpdateRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class UsersApiService {
  private readonly http = inject(HttpClient);
  private readonly url = buildApiUrl(inject(API_BASE_URL), '/api/v1/users');

  getMyProfile(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.url}/me`);
  }

  updateMyProfile(request: ProfileUpdateRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${this.url}/me`, request);
  }

  changeMyPassword(request: ChangePasswordRequest): Observable<MessageResponse> {
    return this.http.put<MessageResponse>(`${this.url}/me/password`, request);
  }
  getEnterprise():Observable<EnterpriseProfile>{return this.http.get<EnterpriseProfile>(`${this.url}/enterprise`);}
  updateEnterprise(request:EnterpriseUpdateRequest):Observable<EnterpriseProfile>{return this.http.put<EnterpriseProfile>(`${this.url}/enterprise`,request);}

  createStaffMember(request: UserCreationRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${this.url}/staff`, request);
  }

  getEnterpriseStaff(): Observable<readonly UserResponse[]> {
    return this.http.get<readonly UserResponse[]>(`${this.url}/staff`);
  }

  getActiveCouriers(): Observable<readonly UserResponse[]> {
    return this.http.get<readonly UserResponse[]>(`${this.url}/staff/couriers`);
  }

  getStaffMember(id: number): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.url}/staff/${id}`);
  }

  setStaffStatus(id: number, active: boolean): Observable<UserResponse> {
    const request: StaffStatusRequest = { active };
    return this.http.patch<UserResponse>(`${this.url}/staff/${id}/status`, request);
  }

  deleteStaffMember(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/staff/${id}`);
  }

  getUserById(id: number): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.url}/${id}`);
  }
}
