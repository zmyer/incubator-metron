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
import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {Inject} from '@angular/core';
import {Observable} from 'rxjs/Observable';
import {Router, ActivatedRoute, Params} from '@angular/router';
import {Http, RequestOptions, Response, ResponseOptions} from '@angular/http';
import {SensorParserConfigComponent, Pane, KafkaStatus} from './sensor-parser-config.component';
import {StellarService} from '../../service/stellar.service';
import {SensorParserConfigService} from '../../service/sensor-parser-config.service';
import {KafkaService} from '../../service/kafka.service';
import {KafkaTopic} from '../../model/kafka-topic';
import {GrokValidationService} from '../../service/grok-validation.service';
import {MetronAlerts} from '../../shared/metron-alerts';
import {SensorParserConfig} from '../../model/sensor-parser-config';
import {ParseMessageRequest} from '../../model/parse-message-request';
import {SensorParserContext} from '../../model/sensor-parser-context';
import {AuthenticationService} from '../../service/authentication.service';
import {FieldTransformer} from '../../model/field-transformer';
import {SensorParserConfigModule} from './sensor-parser-config.module';
import {SensorEnrichmentConfigService} from '../../service/sensor-enrichment-config.service';
import {SensorEnrichmentConfig} from '../../model/sensor-enrichment-config';
import {APP_CONFIG, METRON_REST_CONFIG} from '../../app.config';
import {IAppConfig} from '../../app.config.interface';
import {SensorIndexingConfigService} from '../../service/sensor-indexing-config.service';
import {IndexingConfigurations} from '../../model/sensor-indexing-config';
import '../../rxjs-operators';
import 'rxjs/add/observable/of';
import {HdfsService} from '../../service/hdfs.service';
import {RestError} from '../../model/rest-error';
import {RiskLevelRule} from '../../model/risk-level-rule';


class MockRouter {
  navigateByUrl(url: string) {}
}

class MockActivatedRoute {
  private name: string;
  params: Observable<Params>;

  setNameForTest(name: string) {
    this.name = name;
    this.params = Observable.create(observer => {
      observer.next({id: this.name});
      observer.complete();
    });
  }
}

class MockSensorParserConfigService extends SensorParserConfigService {
  private sensorParserConfig: SensorParserConfig;
  private parsedMessage: any;
  private postedSensorParserConfig: SensorParserConfig;
  private throwError: boolean;

  constructor(private http2: Http, @Inject(APP_CONFIG) private config2: IAppConfig) {
    super(http2, config2);
  }

  public post(sensorParserConfig: SensorParserConfig): Observable<SensorParserConfig> {
    if (this.throwError) {
      let error = new RestError();
      error.message = 'SensorParserConfig post error';
      return Observable.throw(error);
    }
    this.postedSensorParserConfig = sensorParserConfig;
    return Observable.create(observer => {
      observer.next(sensorParserConfig);
      observer.complete();
    });
  }

  public get(name: string): Observable<SensorParserConfig> {
    return Observable.create(observer => {
      observer.next(this.sensorParserConfig);
      observer.complete();
    });
  }

  public getAvailableParsers(): Observable<{}> {
    return Observable.create(observer => {
      observer.next({
        'Bro': 'org.apache.metron.parsers.bro.BasicBroParser',
        'Grok': 'org.apache.metron.parsers.GrokParser'
      });
      observer.complete();
    });
  }

  public parseMessage(parseMessageRequest: ParseMessageRequest): Observable<{}> {
    return Observable.create(observer => {
      observer.next(this.parsedMessage);
      observer.complete();
    });
  }

  public setSensorParserConfig(result: any) {
    this.sensorParserConfig = result;
  }

  public setParsedMessage(parsedMessage: any) {
    this.parsedMessage = parsedMessage;
  }

  public setThrowError(throwError: boolean) {
    this.throwError = throwError;
  }

  public getPostedSensorParserConfig() {
    return this.postedSensorParserConfig;
  }
}

class MockSensorIndexingConfigService extends SensorIndexingConfigService {
  private name: string;
  private postedIndexingConfigurations: IndexingConfigurations;
  private sensorIndexingConfig: IndexingConfigurations;
  private throwError: boolean;

  constructor(private http2: Http, @Inject(APP_CONFIG) private config2: IAppConfig) {
    super(http2, config2);
  }

  public post(name: string, sensorIndexingConfig: IndexingConfigurations): Observable<IndexingConfigurations> {
    if (this.throwError) {
      let error = new RestError();
      error.message = 'IndexingConfigurations post error';
      return Observable.throw(error);
    }
    this.postedIndexingConfigurations = sensorIndexingConfig;
    return Observable.create(observer => {
      observer.next(sensorIndexingConfig);
      observer.complete();
    });
  }

  public get(name: string): Observable<IndexingConfigurations> {
    if (this.sensorIndexingConfig === null) {
      let error = new RestError();
      error.message = 'IndexingConfigurations get error';
      return Observable.throw(error);
    }
    return Observable.create(observer => {
      if (name === this.name) {
        observer.next(this.sensorIndexingConfig);
      }
      observer.complete();
    });
  }

  public setSensorIndexingConfig(name: string, result: IndexingConfigurations) {
    this.name = name;
    this.sensorIndexingConfig = result;
  }

  public setThrowError(throwError: boolean) {
    this.throwError = throwError;
  }

  public getPostedIndexingConfigurations(): IndexingConfigurations {
    return this.postedIndexingConfigurations;
  }
}

class MockKafkaService extends KafkaService {
  private name: string;
  private kafkaTopic: KafkaTopic;
  private kafkaTopicForPost: KafkaTopic;
  private sampleData = {'key1': 'value1', 'key2': 'value2'};

  constructor(private http2: Http, @Inject(APP_CONFIG) private config2: IAppConfig) {
    super(http2, config2);
  }

  public setKafkaTopic(name: string, kafkaTopic: KafkaTopic) {
    this.name = name;
    this.kafkaTopic = kafkaTopic;
  }

  public setSampleData(name: string, sampleData?: any) {
    this.name = name;
    this.sampleData = sampleData;
  }

  public sample(name: string): Observable<string> {
    if (this.sampleData === null) {
      return Observable.throw(new RestError());
    }
    return Observable.create(observer => {
      if (name === this.name) {
        observer.next(JSON.stringify(this.sampleData));
      }
      observer.complete();
    });
  }

  public get(name: string): Observable<KafkaTopic> {
    if (this.kafkaTopic === null) {
      return Observable.throw(new RestError());
    }
    return Observable.create(observer => {
      if (name === this.name) {
        observer.next(this.kafkaTopic);
      }
      observer.complete();
    });
  }

  public post(k: KafkaTopic): Observable<KafkaTopic> {
    this.kafkaTopicForPost = k;
    console.log('called post MockKafkaService: ' + this.kafkaTopicForPost);
    return Observable.create(observer => {
      observer.next({});
      observer.complete();
    });
  }

  public getKafkaTopicForPost(): KafkaTopic {
    console.log('Called get MockKafkaService: ' + this.kafkaTopicForPost);
    return this.kafkaTopicForPost;
  }
}

class MockGrokValidationService extends GrokValidationService {

  private path: string;
  private contents: string;

  constructor(private http2: Http, @Inject(APP_CONFIG) private config2: IAppConfig) {
    super(http2, config2);
  }

  public setContents(path: string, contents: string) {
    this.path = path;
    this.contents = contents;
  }

  public list(): Observable<string[]> {
    return Observable.create(observer => {
      observer.next({
        'BASE10NUM': '(?<![0-9.+-])(?>[+-]?(?:(?:[0-9]+(?:\\.[0-9]+)?)|(?:\\.[0-9]+)))',
        'BASE16FLOAT': '\\b(?<![0-9A-Fa-f.])(?:[+-]?(?:0x)?(?:(?:[0-9A-Fa-f]+(?:\\.[0-9A-Fa-f]*)?)|(?:\\.[0-9A-Fa-f]+)))\\b',
        'BASE16NUM': '(?<![0-9A-Fa-f])(?:[+-]?(?:0x)?(?:[0-9A-Fa-f]+))',
        'CISCOMAC': '(?:(?:[A-Fa-f0-9]{4}\\.){2}[A-Fa-f0-9]{4})',
        'COMMONMAC': '(?:(?:[A-Fa-f0-9]{2}:){5}[A-Fa-f0-9]{2})',
        'DATA': '.*?'
      });
      observer.complete();
    });
  }


  public getStatement(path: string): Observable<string> {
    if (this.contents === null) {
      return Observable.throw('Error');
    }
    return Observable.create(observer => {
      if (path === this.path) {
        observer.next(this.contents);
      }
      observer.complete();
    });
  }
}

class MockHdfsService extends HdfsService {
  private fileList: string[];
  private path: string;
  private contents: string;
  private postedContents: string;

  constructor(private http2: Http, @Inject(APP_CONFIG) private config2: IAppConfig) {
    super(http2, config2);
  }

  public setFileList(path: string, fileList: string[]) {
    this.path = path;
    this.fileList = fileList;
  }

  public setContents(path: string, contents: string) {
    this.path = path;
    this.contents = contents;
  }

  public list(path: string): Observable<string[]> {
    if (this.fileList === null) {
      return Observable.throw('Error');
    }
    return Observable.create(observer => {
      if (path === this.path) {
        observer.next(this.fileList);
      }
      observer.complete();
    });
  }

  public read(path: string): Observable<string> {
    if (this.contents === null) {
      return Observable.throw('Error');
    }
    return Observable.create(observer => {
      if (path === this.path) {
        observer.next(this.contents);
      }
      observer.complete();
    });
  }

  public post(path: string, contents: string): Observable<{}> {
    if (this.contents === null) {
      let error = new RestError();
      error.message = 'HDFS post Error';
      return Observable.throw(error);
    }
    this.postedContents = contents;
    return Observable.create(observer => {
      observer.next(contents);
      observer.complete();
    });
  }

  public deleteFile(path: string): Observable<Response> {
    return Observable.create(observer => {
      observer.next({});
      observer.complete();
    });
  }

  public getPostedContents() {
    return this.postedContents;
  }
}

class MockAuthenticationService extends AuthenticationService {

  constructor(private http2: Http, private router2: Router, @Inject(APP_CONFIG) private config2: IAppConfig) {
    super(http2, router2, config2);
  }

  public getCurrentUser(options: RequestOptions): Observable<Response> {
    let responseOptions: ResponseOptions = new ResponseOptions();
    responseOptions.body = 'user';
    let response: Response = new Response(responseOptions);
    return Observable.of(response);
  };
}

class MockTransformationValidationService extends StellarService {

  private transformationValidationResult: any;
  private transformationValidationForValidate: SensorParserContext;

  constructor(private http2: Http, @Inject(APP_CONFIG) private config2: IAppConfig) {
    super(http2, config2);
  }

  public setTransformationValidationResultForTest(transformationValidationResult: any): void {
    this.transformationValidationResult = transformationValidationResult;
  }

  public getTransformationValidationForValidate(): SensorParserContext {
    return this.transformationValidationForValidate;
  }

  public validate(t: SensorParserContext): Observable<{}> {
    this.transformationValidationForValidate = t;
    return Observable.create(observer => {
      observer.next(this.transformationValidationResult);
      observer.complete();
    });
  }
}

export class MockSensorEnrichmentConfigService {
  private name: string;
  private sensorEnrichmentConfig: SensorEnrichmentConfig;
  private postedSensorEnrichmentConfig: SensorEnrichmentConfig;
  private throwError: boolean;

  public setSensorEnrichmentConfig(name: string, sensorEnrichmentConfig: SensorEnrichmentConfig) {
    this.name = name;
    this.sensorEnrichmentConfig = sensorEnrichmentConfig;
  }

  public get(name: string): Observable<SensorEnrichmentConfig> {
    if (this.sensorEnrichmentConfig === null) {
      let error = new RestError();
      error.message = 'SensorEnrichmentConfig get error';
      return Observable.throw(error);
    }
    return Observable.create(observer => {
      if (name === this.name) {
        observer.next(this.sensorEnrichmentConfig);
      }
      observer.complete();
    });
  }

  public post(name: string, sensorEnrichmentConfig: SensorEnrichmentConfig): Observable<SensorEnrichmentConfig> {
    if (this.throwError) {
      let error = new RestError();
      error.message = 'SensorEnrichmentConfig post error';
      return Observable.throw(error);
    }
    this.postedSensorEnrichmentConfig = sensorEnrichmentConfig;
    return  Observable.create(observer => {
      observer.next(sensorEnrichmentConfig);
      observer.complete();
    });
  }

  public setThrowError(throwError: boolean) {
    this.throwError = throwError;
  }

  public getPostedSensorEnrichmentConfig() {
    return this.postedSensorEnrichmentConfig;
  }
}

describe('Component: SensorParserConfig', () => {

  let component: SensorParserConfigComponent;
  let fixture: ComponentFixture<SensorParserConfigComponent>;
  let sensorParserConfigService: MockSensorParserConfigService;
  let sensorEnrichmentConfigService: MockSensorEnrichmentConfigService;
  let sensorIndexingConfigService: MockSensorIndexingConfigService;
  let transformationValidationService: MockTransformationValidationService;
  let kafkaService: MockKafkaService;
  let hdfsService: MockHdfsService;
  let grokValidationService: MockGrokValidationService;
  let activatedRoute: MockActivatedRoute;
  let metronAlerts: MetronAlerts;
  let router: MockRouter;

  let squidSensorParserConfig: any = {
    'parserClassName': 'org.apache.metron.parsers.GrokParser',
    'sensorTopic': 'squid',
    'parserConfig': {
      'grokPath': '/apps/metron/patterns/squid',
      'patternLabel': 'SQUID_DELIMITED',
      'timestampField': 'timestamp'
    },
    'fieldTransformations': [
      {
        'input': [],
        'output': ['full_hostname', 'domain_without_subdomains', 'hostname'],
        'transformation': 'STELLAR',
        'config': {
          'full_hostname': 'URL_TO_HOST(url)',
          'domain_without_subdomains': 'DOMAIN_REMOVE_SUBDOMAINS(full_hostname)'
        }
      }
    ],
  };

  let squidSensorEnrichmentConfig = {
    'enrichment': {
      'fieldMap': {
        'geo': ['ip_dst_addr'],
        'host': ['ip_dst_addr'],
        'whois': [],
        'stellar': { 'config': { 'group1': {} }}
      },
      'fieldToTypeMap': {}, 'config': {}
    },
    'threatIntel': {
      'threatIntel': {
        'fieldMap': { 'hbaseThreatIntel': ['ip_dst_addr'] },
        'fieldToTypeMap': { 'ip_dst_addr': ['malicious_ip'] }
      }
    }
  };

  let squidIndexingConfigurations = {
    'hdfs': {
      'index': 'squid',
      'batchSize': 5,
      'enabled': true
    },
    'elasticsearch': {
      'index': 'squid',
      'batchSize': 10,
      'enabled': true
    },
    'solr': {
      'index': 'squid',
      'batchSize': 1,
      'enabled': false
    },
  };

  beforeEach(async(() => {

    TestBed.configureTestingModule({
      imports: [SensorParserConfigModule],
      providers: [
        MetronAlerts,
        {provide: Http},
        {provide: SensorParserConfigService, useClass: MockSensorParserConfigService},
        {provide: SensorIndexingConfigService, useClass: MockSensorIndexingConfigService},
        {provide: KafkaService, useClass: MockKafkaService},
        {provide: HdfsService, useClass: MockHdfsService},
        {provide: GrokValidationService, useClass: MockGrokValidationService},
        {provide: StellarService, useClass: MockTransformationValidationService},
        {provide: ActivatedRoute, useClass: MockActivatedRoute},
        {provide: Router, useClass: MockRouter},
        {provide: AuthenticationService, useClass: MockAuthenticationService},
        {provide: SensorEnrichmentConfigService, useClass: MockSensorEnrichmentConfigService},
        {provide: APP_CONFIG, useValue: METRON_REST_CONFIG}
      ]
    }).compileComponents()
      .then(() => {
        fixture = TestBed.createComponent(SensorParserConfigComponent);
        component = fixture.componentInstance;
        sensorParserConfigService = fixture.debugElement.injector.get(SensorParserConfigService);
        sensorIndexingConfigService = fixture.debugElement.injector.get(SensorIndexingConfigService);
        transformationValidationService = fixture.debugElement.injector.get(StellarService);
        kafkaService = fixture.debugElement.injector.get(KafkaService);
        hdfsService = fixture.debugElement.injector.get(HdfsService);
        grokValidationService = fixture.debugElement.injector.get(GrokValidationService);
        sensorEnrichmentConfigService = fixture.debugElement.injector.get(SensorEnrichmentConfigService);
        activatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
        metronAlerts = fixture.debugElement.injector.get(MetronAlerts);
        router = fixture.debugElement.injector.get(Router);
      });

  }));

  it('should create an instance of SensorParserConfigComponent', async(() => {
    expect(component).toBeDefined();

    fixture.destroy();
  }));

  it('should handle ngOnInit', async(() => {
    spyOn(component, 'init');
    spyOn(component, 'createForms');
    spyOn(component, 'getAvailableParsers');

    activatedRoute.setNameForTest('squid');

    component.ngOnInit();

    expect(component.init).toHaveBeenCalledWith('squid');
    expect(component.createForms).toHaveBeenCalled();
    expect(component.getAvailableParsers).toHaveBeenCalled();

    fixture.destroy();
  }));

  it('should createForms', async(() => {
    component.sensorParserConfig = Object.assign(new SensorParserConfig(), squidSensorParserConfig);
    component.createForms();

    expect(Object.keys(component.sensorConfigForm.controls).length).toEqual(15);
    expect(Object.keys(component.transformsValidationForm.controls).length).toEqual(2);
    expect(component.showAdvancedParserConfiguration).toEqual(true);

    component.sensorParserConfig.parserConfig = {};
    component.showAdvancedParserConfiguration = false;
    component.createForms();
    expect(component.showAdvancedParserConfiguration).toEqual(false);

    fixture.destroy();
  }));

  it('should getAvailableParsers', async(() => {
    component.getAvailableParsers();
    expect(component.availableParsers).toEqual({
      'Bro': 'org.apache.metron.parsers.bro.BasicBroParser',
      'Grok': 'org.apache.metron.parsers.GrokParser'
    });
    expect(component.availableParserNames).toEqual(['Bro', 'Grok']);

    fixture.destroy();
  }));

  it('should init', async(() => {
    component.init('new');

    let expectedSensorParserConfig = new SensorParserConfig();
    expectedSensorParserConfig.parserClassName = 'org.apache.metron.parsers.GrokParser';
    expect(component.sensorParserConfig).toEqual(expectedSensorParserConfig);
    expect(component.sensorEnrichmentConfig).toEqual(new SensorEnrichmentConfig());
    expect(component.indexingConfigurations).toEqual(new IndexingConfigurations());
    expect(component.editMode).toEqual(false);

    spyOn(component, 'getKafkaStatus');
    let sensorParserConfig = Object.assign(new SensorParserConfig(), squidSensorParserConfig);
    sensorParserConfigService.setSensorParserConfig(sensorParserConfig);
    sensorEnrichmentConfigService.setSensorEnrichmentConfig('squid',
        Object.assign(new SensorEnrichmentConfig(), squidSensorEnrichmentConfig));
    sensorIndexingConfigService.setSensorIndexingConfig('squid',
        Object.assign(new IndexingConfigurations(), squidIndexingConfigurations));
    hdfsService.setContents('/apps/metron/patterns/squid', 'SQUID_DELIMITED grok statement');

    component.init('squid');
    expect(component.sensorParserConfig).toEqual(Object.assign(new SensorParserConfig(), squidSensorParserConfig));
    expect(component.sensorNameValid).toEqual(true);
    expect(component.getKafkaStatus).toHaveBeenCalled();
    expect(component.showAdvancedParserConfiguration).toEqual(true);
    expect(component.grokStatement).toEqual('SQUID_DELIMITED grok statement');
    expect(component.patternLabel).toEqual('SQUID_DELIMITED');
    expect(component.sensorEnrichmentConfig).toEqual(Object.assign(new SensorEnrichmentConfig(), squidSensorEnrichmentConfig));
    expect(component.indexingConfigurations).toEqual(Object.assign(new IndexingConfigurations(), squidIndexingConfigurations));

    component.sensorParserConfig.parserConfig['grokPath'] = '/patterns/squid';
    hdfsService.setContents('/patterns/squid', null);
    grokValidationService.setContents('/patterns/squid', 'SQUID grok statement from classpath');

    component.init('squid');
    expect(component.grokStatement).toEqual('SQUID grok statement from classpath');

    spyOn(metronAlerts, 'showErrorMessage');

    sensorEnrichmentConfigService.setSensorEnrichmentConfig('squid', null);
    component.init('squid');
    expect(metronAlerts.showErrorMessage).toHaveBeenCalledWith('SensorEnrichmentConfig get error');

    sensorIndexingConfigService.setSensorIndexingConfig('squid', null);
    component.init('squid');
    expect(metronAlerts.showErrorMessage).toHaveBeenCalledWith('IndexingConfigurations get error');

    grokValidationService.setContents('/patterns/squid', null);

    component.init('squid');
    expect(metronAlerts.showErrorMessage).toHaveBeenCalledWith('Could not find grok statement in HDFS or classpath at /patterns/squid');

    sensorParserConfig = new SensorParserConfig();
    sensorParserConfig.sensorTopic = 'bro';
    sensorParserConfigService.setSensorParserConfig(sensorParserConfig);
    component.showAdvancedParserConfiguration = false;

    component.init('bro');
    expect(component.showAdvancedParserConfiguration).toEqual(false);

    fixture.destroy();
  }));

  it('should getMessagePrefix', async(() => {
    component.getAvailableParsers();
    expect(component.getMessagePrefix()).toEqual('Created');
    component.editMode = true;
    expect(component.getMessagePrefix()).toEqual('Modified');

    fixture.destroy();
  }));

  it('should handle onSetSensorName', async(() => {
    spyOn(component, 'getKafkaStatus');
    spyOn(component, 'isConfigValid');

    component.onSetSensorName();
    expect(component.getKafkaStatus).not.toHaveBeenCalled();
    expect(component.isConfigValid).toHaveBeenCalled();

    component.sensorParserConfig.sensorTopic = 'bro';
    component.onSetSensorName();
    expect(component.sensorNameValid).toEqual(true);
    expect(component.getKafkaStatus).toHaveBeenCalled();

    fixture.destroy();
  }));

  it('should handle onParserTypeChange', async(() => {
    spyOn(component, 'hidePane');
    spyOn(component, 'isConfigValid');

    component.onParserTypeChange();
    expect(component.hidePane).not.toHaveBeenCalled();
    expect(component.isConfigValid).toHaveBeenCalled();

    component.sensorParserConfig.parserClassName = 'org.apache.metron.parsers.GrokParser';
    component.onParserTypeChange();
    expect(component.parserClassValid).toEqual(true);
    expect(component.hidePane).not.toHaveBeenCalled();

    component.sensorParserConfig.parserClassName = 'org.apache.metron.parsers.bro.BasicBroParser';
    component.onParserTypeChange();
    expect(component.hidePane).toHaveBeenCalledWith(Pane.GROK);

    fixture.destroy();
  }));

  it('should handle onGrokStatementChange', async(() => {
    spyOn(component, 'isConfigValid');

    component.onGrokStatementChange();
    expect(component.grokStatementValid).toEqual(false);
    expect(component.isConfigValid).toHaveBeenCalled();

    component.grokStatement = 'grok statement';
    component.onGrokStatementChange();
    expect(component.grokStatementValid).toEqual(true);

    fixture.destroy();
  }));

  it('should handle isConfigValid', async(() => {
    component.isConfigValid();
    expect(component.configValid).toEqual(false);

    component.sensorNameValid = true;
    component.parserClassValid = true;

    component.isConfigValid();
    expect(component.configValid).toEqual(true);

    component.sensorParserConfig.parserClassName = 'org.apache.metron.parsers.GrokParser';
    component.isConfigValid();
    expect(component.configValid).toEqual(false);

    component.grokStatementValid = true;
    component.isConfigValid();
    expect(component.configValid).toEqual(true);

    fixture.destroy();
  }));

  it('should getKafkaStatus', async(() => {
    component.getKafkaStatus();
    expect(component.currentKafkaStatus).toEqual(null);

    component.sensorParserConfig.sensorTopic = 'squid';
    kafkaService.setKafkaTopic('squid', null);

    component.getKafkaStatus();
    expect(component.currentKafkaStatus).toEqual(KafkaStatus.NO_TOPIC);

    kafkaService.setKafkaTopic('squid', new KafkaTopic());
    kafkaService.setSampleData('squid', null);

    component.getKafkaStatus();
    expect(component.currentKafkaStatus).toEqual(KafkaStatus.NOT_EMITTING);

    kafkaService.setSampleData('squid', 'message');
    component.getKafkaStatus();
    expect(component.currentKafkaStatus).toEqual(KafkaStatus.EMITTING);

    fixture.destroy();
  }));

  it('should getTransforms', async(() => {
    expect(component.getTransforms()).toEqual('0 Transformations Applied');

    component.sensorParserConfig.fieldTransformations.push(Object.assign(new FieldTransformer(), {'output': ['field1', 'field2']}));
    component.sensorParserConfig.fieldTransformations.push(Object.assign(new FieldTransformer(), {'output': ['field3']}));

    expect(component.getTransforms()).toEqual('3 Transformations Applied');

    fixture.destroy();
  }));

  it('should handle onSaveGrokStatement', async(() => {
    component.sensorParserConfig.sensorTopic = 'squid';

    component.onSaveGrokStatement('grok statement');
    expect(component.grokStatement).toEqual('grok statement');
    expect(component.sensorParserConfig.parserConfig['grokPath']).toEqual('/apps/metron/patterns/squid');

    component.sensorParserConfig.parserConfig['grokPath'] = '/patterns/squid';
    component.onSaveGrokStatement('grok statement');
    expect(component.sensorParserConfig.parserConfig['grokPath']).toEqual('/apps/metron/patterns/squid');

    component.sensorParserConfig.parserConfig['grokPath'] = '/custom/grok/path';
    component.onSaveGrokStatement('grok statement');
    expect(component.sensorParserConfig.parserConfig['grokPath']).toEqual('/custom/grok/path');

    fixture.destroy();
  }));

  it('should onSavePatternLabel', async(() => {
    component.onSavePatternLabel('PATTERN_LABEL');
    expect(component.patternLabel).toEqual('PATTERN_LABEL');
    expect(component.sensorParserConfig.parserConfig['patternLabel']).toEqual('PATTERN_LABEL');

    fixture.destroy();
  }));

  it('should goBack', async(() => {
    activatedRoute.setNameForTest('new');

    router.navigateByUrl = jasmine.createSpy('navigateByUrl');
    component.goBack();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/sensors');

    fixture.destroy();
  }));

  it('should save sensor configuration', async(() => {
    let fieldTransformer = Object.assign(new FieldTransformer(), {
      'input': [],
      'output': ['url_host'],
      'transformation': 'MTL',
      'config': {'url_host': 'TO_LOWER(URL_TO_HOST(url))'}
    });
    let sensorParserConfigSave: SensorParserConfig = new SensorParserConfig();
    sensorParserConfigSave.sensorTopic = 'squid';
    sensorParserConfigSave.parserClassName = 'org.apache.metron.parsers.GrokParser';
    sensorParserConfigSave.parserConfig['grokPath'] = '/apps/metron/patterns/squid';
    sensorParserConfigSave.fieldTransformations = [fieldTransformer];
    activatedRoute.setNameForTest('new');
    sensorParserConfigService.setThrowError(true);

    spyOn(metronAlerts, 'showSuccessMessage');
    spyOn(metronAlerts, 'showErrorMessage');

    component.sensorParserConfig.sensorTopic = 'squid';
    component.sensorParserConfig.parserClassName = 'org.apache.metron.parsers.GrokParser';
    component.sensorParserConfig.parserConfig['grokPath'] = '/apps/metron/patterns/squid';
    component.sensorParserConfig.fieldTransformations = [fieldTransformer];

    component.onSave();
    expect(metronAlerts.showErrorMessage['calls'].mostRecent().args[0])
        .toEqual('Unable to save sensor config: SensorParserConfig post error');

    component.sensorEnrichmentConfig = Object.assign(new SensorEnrichmentConfig(), squidSensorEnrichmentConfig);
    component.indexingConfigurations = Object.assign(new IndexingConfigurations(), squidIndexingConfigurations);
    sensorParserConfigService.setThrowError(false);
    hdfsService.setContents('/apps/metron/patterns/squid', 'SQUID grok statement');
    component.grokStatement = 'SQUID grok statement';

    component.onSave();
    expect(sensorParserConfigService.getPostedSensorParserConfig()).toEqual(sensorParserConfigSave);
    expect(sensorEnrichmentConfigService.getPostedSensorEnrichmentConfig())
        .toEqual(Object.assign(new SensorEnrichmentConfig(), squidSensorEnrichmentConfig));
    expect(sensorIndexingConfigService.getPostedIndexingConfigurations())
        .toEqual(Object.assign(new IndexingConfigurations(), squidIndexingConfigurations));
    expect(hdfsService.getPostedContents()).toEqual('SQUID grok statement');

    hdfsService.setContents('/apps/metron/patterns/squid', null);

    component.onSave();
    expect(metronAlerts.showErrorMessage['calls'].mostRecent().args[0]).toEqual('HDFS post Error');

    sensorEnrichmentConfigService.setThrowError(true);

    component.onSave();
    expect(metronAlerts.showErrorMessage['calls'].mostRecent().args[0])
        .toEqual('Created Sensor parser config but unable to save enrichment configuration: SensorEnrichmentConfig post error');

    sensorIndexingConfigService.setThrowError(true);

    component.onSave();
    expect(metronAlerts.showErrorMessage['calls'].mostRecent().args[0])
        .toEqual('Created Sensor parser config but unable to save indexing configuration: IndexingConfigurations post error');

    fixture.destroy();
  }));

  it('should getTransformationCount', async(() => {
    let transforms =
    [
      Object.assign(new FieldTransformer(), {
        'input': [
          'method'
        ],
        'output': null,
        'transformation': 'REMOVE',
        'config': {
          'condition': 'exists(method) and method == "foo"'
        }
      }),
      Object.assign(new FieldTransformer(), {
        'input': [],
        'output': [
          'method',
          'status_code',
          'url'
        ],
        'transformation': 'STELLAR',
        'config': {
          'method': 'TO_UPPER(method)',
          'status_code': 'TO_LOWER(code)',
          'url': 'TO_STRING(TRIM(url))'
        }
      })
    ];



    expect(component.getTransformationCount()).toEqual(0);

    fixture.componentInstance.sensorParserConfig.fieldTransformations = transforms;
    expect(component.getTransformationCount()).toEqual(3);

    fixture.componentInstance.sensorParserConfig.fieldTransformations = [transforms[0]];
    expect(component.getTransformationCount()).toEqual(0);
    fixture.destroy();
  }));

  it('should getEnrichmentCount', async(() => {

    component.sensorEnrichmentConfig.enrichment.fieldMap['geo'] = ['ip_src_addr', 'ip_dst_addr'];
    component.sensorEnrichmentConfig.enrichment.fieldToTypeMap['hbaseenrichment'] = ['ip_src_addr', 'ip_dst_addr'];

    expect(component.getEnrichmentCount()).toEqual(4);

    fixture.destroy();
  }));

  it('should getThreatIntelCount', async(() => {

    component.sensorEnrichmentConfig.threatIntel.fieldToTypeMap['hbaseenrichment'] = ['ip_src_addr', 'ip_dst_addr'];

    expect(component.getThreatIntelCount()).toEqual(2);

    fixture.destroy();
  }));

  it('should getRuleCount', async(() => {
    let rule1 = Object.assign(new RiskLevelRule(), {'name': 'rule1', 'rule': 'some rule', 'score': 50});
    let rule2 = Object.assign(new RiskLevelRule(), {'name': 'rule2', 'rule': 'some other rule', 'score': 80});
    component.sensorEnrichmentConfig.threatIntel.triageConfig.riskLevelRules.push(rule1);
    component.sensorEnrichmentConfig.threatIntel.triageConfig.riskLevelRules.push(rule2);

    expect(component.getRuleCount()).toEqual(2);

    fixture.destroy();
  }));

  it('should showPane', async(() => {

    component.showPane(Pane.GROK);
    expect(component.showGrokValidator).toEqual(true);
    expect(component.showFieldSchema).toEqual(false);
    expect(component.showRawJson).toEqual(false);

    component.showPane(Pane.FIELDSCHEMA);
    expect(component.showGrokValidator).toEqual(false);
    expect(component.showFieldSchema).toEqual(true);
    expect(component.showRawJson).toEqual(false);

    component.showPane(Pane.RAWJSON);
    expect(component.showGrokValidator).toEqual(false);
    expect(component.showFieldSchema).toEqual(false);
    expect(component.showRawJson).toEqual(true);

    fixture.destroy();
  }));

  it('should hidePane', async(() => {

    component.hidePane(Pane.GROK);
    expect(component.showGrokValidator).toEqual(false);
    expect(component.showFieldSchema).toEqual(false);
    expect(component.showRawJson).toEqual(false);

    component.hidePane(Pane.FIELDSCHEMA);
    expect(component.showGrokValidator).toEqual(false);
    expect(component.showFieldSchema).toEqual(false);
    expect(component.showRawJson).toEqual(false);

    component.hidePane(Pane.RAWJSON);
    expect(component.showGrokValidator).toEqual(false);
    expect(component.showFieldSchema).toEqual(false);
    expect(component.showRawJson).toEqual(false);

    fixture.destroy();
  }));

  it('should handle onShowGrokPane', async(() => {
    spyOn(component, 'showPane');
    component.sensorParserConfig.sensorTopic = 'squid';

    component.onShowGrokPane();
    expect(component.patternLabel).toEqual('SQUID');
    expect(component.showPane).toHaveBeenCalledWith(component.pane.GROK);

    component.patternLabel = 'PATTERN_LABEL';

    component.onShowGrokPane();
    expect(component.patternLabel).toEqual('PATTERN_LABEL');

    fixture.destroy();
  }));

  it('should handle onRawJsonChanged', async(() => {
    spyOn(component.sensorFieldSchema, 'createFieldSchemaRows');

    component.onRawJsonChanged();

    expect(component.sensorFieldSchema.createFieldSchemaRows).toHaveBeenCalled();

    fixture.destroy();
  }));

  it('should handle onAdvancedConfigFormClose', async(() => {
    component.onAdvancedConfigFormClose();

    expect(component.showAdvancedParserConfiguration).toEqual(false);

    fixture.destroy();
  }));

});
