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
import {AuthenticationService} from '../service/authentication.service';
import {LoginComponent} from './login.component';

class MockAuthenticationService {

  public login(username: string, password: string, onError): void {
    if (username === 'success') {
      onError({status: 200});
    }

    if (username === 'failure') {
      onError({status: 401});
    }
  }
}

describe('LoginComponent', () => {

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      providers: [
        LoginComponent,
        {provide: AuthenticationService, useClass: MockAuthenticationService}
      ]
    })
      .compileComponents();

  }));

  it('can instantiate login component', inject([LoginComponent], (loginComponent: LoginComponent) => {
      expect(loginComponent instanceof LoginComponent).toBe(true);
  }));

  it('can instantiate login component', inject([LoginComponent], (loginComponent: LoginComponent) => {
    loginComponent.user = 'success';
    loginComponent.password = 'success';
    loginComponent.login();
    expect(loginComponent.loginFailure).toEqual('');

    loginComponent.user = 'failure';
    loginComponent.password = 'failure';
    loginComponent.login();
    expect(loginComponent.loginFailure).toEqual('Login failed for failure');

  }));

});
