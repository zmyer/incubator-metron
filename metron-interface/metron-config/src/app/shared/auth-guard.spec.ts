/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {async, inject, TestBed} from '@angular/core/testing';
import {EventEmitter}     from '@angular/core';
import {AuthGuard} from './auth-guard';
import {AuthenticationService} from '../service/authentication.service';
import {Router} from '@angular/router';

class MockAuthenticationService {
  _isAuthenticationChecked: boolean;
  _isAuthenticated: boolean;
  onLoginEvent: EventEmitter<boolean> = new EventEmitter<boolean>();

  public isAuthenticationChecked(): boolean {
    return this._isAuthenticationChecked;
  }

  public isAuthenticated(): boolean {
    return this._isAuthenticated;
  }
}

class MockRouter {
  navigateByUrl(): any {}
}

describe('AuthGuard', () => {

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthGuard,
        {provide: AuthenticationService, useClass: MockAuthenticationService},
        {provide: Router, useClass: MockRouter}
      ]
    })
      .compileComponents();

  }));

  it('can instantiate auth guard',
    inject([AuthGuard], (authGuard: AuthGuard) => {
      expect(authGuard instanceof AuthGuard).toBe(true);
    }));

  it('test when authentication is checked',
    inject([AuthGuard, AuthenticationService], (authGuard: AuthGuard, authenticationService: MockAuthenticationService) => {
      authenticationService._isAuthenticationChecked = true;
      authenticationService._isAuthenticated = true;
      expect(authGuard.canActivate(null, null)).toBe(true);

      authenticationService._isAuthenticationChecked = true;
      authenticationService._isAuthenticated = false;
      expect(authGuard.canActivate(null, null)).toBe(false);
    }));

  it('test when authentication is not checked',
    inject([AuthGuard, AuthenticationService, Router], (authGuard: AuthGuard,
                                                        authenticationService: MockAuthenticationService,
                                                        router: MockRouter) => {
      authenticationService._isAuthenticationChecked = false;
      authGuard.canActivate(null, null).subscribe(isUserValid => {
        expect(isUserValid).toBe(true);
      });
      authenticationService.onLoginEvent.emit(true);


      spyOn(router, 'navigateByUrl');
      authenticationService._isAuthenticationChecked = false;
      authGuard.canActivate(null, null).subscribe(isUserValid => {
        expect(isUserValid).toBe(false);
      });
      authenticationService.onLoginEvent.emit(false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/login');

    }));
});
