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
import {Component, OnInit, ViewChild} from '@angular/core';
import {FormGroup, Validators, FormControl} from '@angular/forms';
import {SensorParserConfig} from '../../model/sensor-parser-config';
import {SensorParserConfigService} from '../../service/sensor-parser-config.service';
import {Router, ActivatedRoute} from '@angular/router';
import {MetronAlerts} from '../../shared/metron-alerts';
import {SensorParserContext} from '../../model/sensor-parser-context';
import {SensorEnrichmentConfigService} from '../../service/sensor-enrichment-config.service';
import {SensorEnrichmentConfig} from '../../model/sensor-enrichment-config';
import {SensorFieldSchemaComponent} from '../sensor-field-schema/sensor-field-schema.component';
import {SensorRawJsonComponent} from '../sensor-raw-json/sensor-raw-json.component';
import {KafkaService} from '../../service/kafka.service';
import {SensorIndexingConfigService} from '../../service/sensor-indexing-config.service';
import {IndexingConfigurations} from '../../model/sensor-indexing-config';
import {RestError} from '../../model/rest-error';
import {HdfsService} from '../../service/hdfs.service';
import {GrokValidationService} from '../../service/grok-validation.service';

export enum Pane {
  GROK, RAWJSON, FIELDSCHEMA, THREATTRIAGE
}

export enum KafkaStatus {
  NO_TOPIC, NOT_EMITTING, EMITTING
}

@Component({
  selector: 'metron-config-sensor',
  templateUrl: 'sensor-parser-config.component.html',
  styleUrls: ['sensor-parser-config.component.scss']
})

export class SensorParserConfigComponent implements OnInit {

  sensorConfigForm: FormGroup;
  transformsValidationForm: FormGroup;

  sensorParserConfig: SensorParserConfig = new SensorParserConfig();
  sensorEnrichmentConfig: SensorEnrichmentConfig = new SensorEnrichmentConfig();
  indexingConfigurations: IndexingConfigurations = new IndexingConfigurations();

  showGrokValidator: boolean = false;
  showTransformsValidator: boolean = false;
  showAdvancedParserConfiguration: boolean = false;
  showRawJson: boolean = false;
  showFieldSchema: boolean = false;
  showThreatTriage: boolean = false;

  configValid = false;
  sensorNameValid = false;
  parserClassValid = false;
  grokStatementValid = false;
  availableParsers = {};
  availableParserNames = [];
  grokStatement = '';
  patternLabel = '';

  editMode: boolean = false;

  topicExists: boolean = false;

  transformsValidationResult: {map: any, keys: string[]} = {map: {}, keys: []};
  transformsValidation: SensorParserContext = new SensorParserContext();

  pane = Pane;
  openPane: Pane = null;

  kafkaStatus = KafkaStatus;
  currentKafkaStatus = null;

  @ViewChild(SensorFieldSchemaComponent) sensorFieldSchema: SensorFieldSchemaComponent;
  @ViewChild(SensorRawJsonComponent) sensorRawJson: SensorRawJsonComponent;

  constructor(private sensorParserConfigService: SensorParserConfigService, private metronAlerts: MetronAlerts,
              private sensorEnrichmentConfigService: SensorEnrichmentConfigService, private route: ActivatedRoute,
              private sensorIndexingConfigService: SensorIndexingConfigService, private grokValidationService: GrokValidationService,
              private router: Router, private kafkaService: KafkaService, private hdfsService: HdfsService) {
    this.sensorParserConfig.parserConfig = {};
  }


  init(id: string): void {
    if (id !== 'new') {
      this.editMode = true;
      this.sensorParserConfigService.get(id).subscribe((results: SensorParserConfig) => {
        this.sensorParserConfig = results;
        this.sensorNameValid = true;
        this.getKafkaStatus();
        if (Object.keys(this.sensorParserConfig.parserConfig).length > 0) {
          this.showAdvancedParserConfiguration = true;
        }
        if (this.isGrokParser(this.sensorParserConfig)) {
          let path = this.sensorParserConfig.parserConfig['grokPath'];
          if (path) {
            this.hdfsService.read(path).subscribe(contents => {
              this.grokStatement = contents;
            }, (hdfsError: RestError) => {
              this.grokValidationService.getStatement(path).subscribe(contents => {
                this.grokStatement = contents;
              }, (grokError: RestError) => {
                this.metronAlerts.showErrorMessage('Could not find grok statement in HDFS or classpath at ' + path);
              });
            });
          }
          let patternLabel = this.sensorParserConfig.parserConfig['patternLabel'];
          if (patternLabel) {
            this.patternLabel = patternLabel;
          }
      }});

      this.sensorEnrichmentConfigService.get(id).subscribe((result: SensorEnrichmentConfig) => {
        this.sensorEnrichmentConfig = result;
      }, (error: RestError) => {
        if (error.responseCode !== 404) {
          this.metronAlerts.showErrorMessage(error.message);
        }
      });

      this.sensorIndexingConfigService.get(id).subscribe((result: IndexingConfigurations) => {
            this.indexingConfigurations = result;
      }, (error: RestError) => {
        if (error.responseCode !== 404) {
          this.metronAlerts.showErrorMessage(error.message);
        }
      });
    } else {
      this.sensorParserConfig = new SensorParserConfig();
      this.sensorParserConfig.parserClassName = 'org.apache.metron.parsers.GrokParser';
    }
  }

  ngOnInit() {
    this.route.params.subscribe(params => {
      let id = params['id'];
      this.init(id);
    });
    this.createForms();
    this.getAvailableParsers();
  }

  createSensorConfigForm(): FormGroup {
    let group: any = {};

    group['sensorTopic'] = new FormControl(this.sensorParserConfig.sensorTopic, Validators.required);
    group['parserClassName'] = new FormControl(this.sensorParserConfig.parserClassName, Validators.required);
    group['grokStatement'] = new FormControl(this.grokStatement);
    group['transforms'] = new FormControl(this.sensorParserConfig['transforms']);
    group['stellar'] = new FormControl(this.sensorParserConfig);
    group['threatTriage'] = new FormControl(this.sensorEnrichmentConfig);
    group['hdfsIndex'] = new FormControl(this.indexingConfigurations.hdfs.index, Validators.required);
    group['hdfsBatchSize'] = new FormControl(this.indexingConfigurations.hdfs.batchSize, Validators.required);
    group['hdfsEnabled'] = new FormControl(this.indexingConfigurations.hdfs.enabled, Validators.required);
    group['elasticsearchIndex'] = new FormControl(this.indexingConfigurations.elasticsearch.index, Validators.required);
    group['elasticsearchBatchSize'] = new FormControl(this.indexingConfigurations.elasticsearch.batchSize, Validators.required);
    group['elasticsearchEnabled'] = new FormControl(this.indexingConfigurations.elasticsearch.enabled, Validators.required);
    group['solrIndex'] = new FormControl(this.indexingConfigurations.solr.index, Validators.required);
    group['solrBatchSize'] = new FormControl(this.indexingConfigurations.solr.batchSize, Validators.required);
    group['solrEnabled'] = new FormControl(this.indexingConfigurations.solr.enabled, Validators.required);

    return new FormGroup(group);
  }

  createTransformsValidationForm(): FormGroup {
    let group: any = {};

    group['sampleData'] = new FormControl(this.transformsValidation.sampleData, Validators.required);
    group['sensorParserConfig'] = new FormControl(this.transformsValidation.sensorParserConfig, Validators.required);

    return new FormGroup(group);
  }

  createForms() {
    this.sensorConfigForm = this.createSensorConfigForm();
    this.transformsValidationForm = this.createTransformsValidationForm();
    if (Object.keys(this.sensorParserConfig.parserConfig).length > 0) {
      this.showAdvancedParserConfiguration = true;
    }
  }

  getAvailableParsers() {
    this.sensorParserConfigService.getAvailableParsers().subscribe(
      availableParsers => {
        this.availableParsers = availableParsers;
        this.availableParserNames = Object.keys(availableParsers);
      }
    );
  }

  getMessagePrefix(): string {
    return this.editMode ? 'Modified' : 'Created';
  }

  onSetSensorName(): void {
    this.sensorNameValid = this.sensorParserConfig.sensorTopic !== undefined &&
        this.sensorParserConfig.sensorTopic.length > 0;
    if (this.sensorNameValid) {
      this.getKafkaStatus();
    }
    this.isConfigValid();
  }

  onParserTypeChange(): void {
    this.parserClassValid = this.sensorParserConfig.parserClassName !== undefined &&
        this.sensorParserConfig.parserClassName.length > 0;
    if (this.parserClassValid) {
      if (this.isGrokParser(this.sensorParserConfig)) {
      } else {
        this.hidePane(Pane.GROK);
      }
    }
    this.isConfigValid();
  }

  onGrokStatementChange(): void {
    this.grokStatementValid = this.grokStatement !== undefined &&
        this.grokStatement.length > 0;
    this.isConfigValid();
  }

  isConfigValid() {
    let isGrokParser = this.isGrokParser(this.sensorParserConfig);
    this.configValid = this.sensorNameValid && this.parserClassValid && (!isGrokParser || this.grokStatementValid);
  }

  getKafkaStatus() {
    if (!this.sensorParserConfig.sensorTopic || this.sensorParserConfig.sensorTopic.length === 0) {
      this.currentKafkaStatus = null;
      return;
    }

    this.kafkaService.get(this.sensorParserConfig.sensorTopic).subscribe(kafkaTopic => {
      this.kafkaService.sample(this.sensorParserConfig.sensorTopic).subscribe((sampleData: string) => {
        this.currentKafkaStatus = (sampleData && sampleData.length > 0) ? KafkaStatus.EMITTING : KafkaStatus.NOT_EMITTING;
      },
      error => {
        this.currentKafkaStatus = KafkaStatus.NOT_EMITTING;
      });
    },
    error => {
      this.currentKafkaStatus = KafkaStatus.NO_TOPIC;
    });

  }

  getTransforms(): string {
    let count = 0;
    if (this.sensorParserConfig.fieldTransformations) {
      for (let tranforms of this.sensorParserConfig.fieldTransformations) {
        if (tranforms.output) {
          count += tranforms.output.length;
        }
      }
    }

    return count + ' Transformations Applied';
  }

  goBack() {
    this.router.navigateByUrl('/sensors');
    return false;
  }

  onSaveGrokStatement(grokStatement: string) {
    this.grokStatement = grokStatement;
    let grokPath = this.sensorParserConfig.parserConfig['grokPath'];
    if (!grokPath || grokPath.indexOf('/patterns') === 0) {
      this.sensorParserConfig.parserConfig['grokPath'] = '/apps/metron/patterns/' + this.sensorParserConfig.sensorTopic;
    }
  }

  onSavePatternLabel(patternLabel: string) {
    this.patternLabel = patternLabel;
    this.sensorParserConfig.parserConfig['patternLabel'] = patternLabel;
  }

  onSave() {
    let sensorParserConfigSave: SensorParserConfig = new SensorParserConfig();
    sensorParserConfigSave.parserConfig = {};
    sensorParserConfigSave.sensorTopic = this.sensorParserConfig.sensorTopic;
    sensorParserConfigSave.parserClassName = this.sensorParserConfig.parserClassName;
    sensorParserConfigSave.parserConfig = this.sensorParserConfig.parserConfig;
    sensorParserConfigSave.fieldTransformations = this.sensorParserConfig.fieldTransformations;

    if (!this.indexingConfigurations.hdfs.index) {
      this.indexingConfigurations.hdfs.index = this.sensorParserConfig.sensorTopic;
    }
    if (!this.indexingConfigurations.elasticsearch.index) {
      this.indexingConfigurations.elasticsearch.index = this.sensorParserConfig.sensorTopic;
    }
    if (!this.indexingConfigurations.solr.index) {
      this.indexingConfigurations.solr.index = this.sensorParserConfig.sensorTopic;
    }
    this.sensorParserConfigService.post(sensorParserConfigSave).subscribe(
      sensorParserConfig => {
        if (this.isGrokParser(sensorParserConfig)) {
            this.hdfsService.post(this.sensorParserConfig.parserConfig['grokPath'], this.grokStatement).subscribe(
                response => {}, (error: RestError) => this.metronAlerts.showErrorMessage(error.message));
        }
        this.sensorEnrichmentConfigService.post(sensorParserConfig.sensorTopic, this.sensorEnrichmentConfig).subscribe(
            (sensorEnrichmentConfig: SensorEnrichmentConfig) => {
        }, (error: RestError) => {
              let msg = ' Sensor parser config but unable to save enrichment configuration: ';
              this.metronAlerts.showErrorMessage(this.getMessagePrefix() + msg + error.message);
        });
        this.sensorIndexingConfigService.post(sensorParserConfig.sensorTopic, this.indexingConfigurations).subscribe(
            (indexingConfigurations: IndexingConfigurations) => {
        }, (error: RestError) => {
              let msg = ' Sensor parser config but unable to save indexing configuration: ';
              this.metronAlerts.showErrorMessage(this.getMessagePrefix() + msg + error.message);
        });
        this.metronAlerts.showSuccessMessage(this.getMessagePrefix() + ' Sensor ' + sensorParserConfig.sensorTopic);
        this.sensorParserConfigService.dataChangedSource.next([sensorParserConfigSave]);
        this.goBack();
      }, (error: RestError) => {
        this.metronAlerts.showErrorMessage('Unable to save sensor config: ' + error.message);
      });
  }

  isGrokParser(sensorParserConfig: SensorParserConfig): boolean {
    if (sensorParserConfig && sensorParserConfig.parserClassName) {
      return sensorParserConfig.parserClassName === 'org.apache.metron.parsers.GrokParser';
    }
    return false;
  }

  getTransformationCount(): number {
    let stellarTransformations = this.sensorParserConfig.fieldTransformations.filter(fieldTransformer =>
      fieldTransformer.transformation === 'STELLAR');
    if (stellarTransformations.length > 0 && stellarTransformations[0].config) {
      return Object.keys(stellarTransformations[0].config).length;
    } else {
      return 0;
    }
  }

  getEnrichmentCount(): number {
    let count = 0;
    if (this.sensorEnrichmentConfig.enrichment.fieldMap) {
      for (let enrichment in this.sensorEnrichmentConfig.enrichment.fieldMap) {
        if (enrichment !== 'hbaseEnrichment' && enrichment !== 'stellar') {
          count += this.sensorEnrichmentConfig.enrichment.fieldMap[enrichment].length;
        }
      }
    }
    if (this.sensorEnrichmentConfig.enrichment.fieldToTypeMap) {
      for (let fieldName in this.sensorEnrichmentConfig.enrichment.fieldToTypeMap) {
        if (this.sensorEnrichmentConfig.enrichment.fieldToTypeMap.hasOwnProperty(fieldName)) {
          count += this.sensorEnrichmentConfig.enrichment.fieldToTypeMap[fieldName].length;
        }
      }
    }
    return count;
  }

  getThreatIntelCount(): number {
    let count = 0;
    if (this.sensorEnrichmentConfig.threatIntel.fieldToTypeMap) {
      for (let fieldName in this.sensorEnrichmentConfig.threatIntel.fieldToTypeMap) {
        if (this.sensorEnrichmentConfig.threatIntel.fieldToTypeMap.hasOwnProperty(fieldName)) {
          count += this.sensorEnrichmentConfig.threatIntel.fieldToTypeMap[fieldName].length;
        }
      }
    }
    return count;
  }

  getRuleCount(): number {
    let count = 0;
    if (this.sensorEnrichmentConfig.threatIntel.triageConfig.riskLevelRules) {
      count = Object.keys(this.sensorEnrichmentConfig.threatIntel.triageConfig.riskLevelRules).length;
    }
    return count;
  }

  onShowGrokPane() {
    if (!this.patternLabel) {
      this.patternLabel = this.sensorParserConfig.sensorTopic.toUpperCase();
    }
    this.showPane(this.pane.GROK);
  }

  showPane(pane: Pane) {
    this.setPaneVisibility(pane, true);
  }

  hidePane(pane: Pane) {
    this.setPaneVisibility(pane, false);
  }

  setPaneVisibility(pane: Pane, visibilty: boolean) {
    this.showGrokValidator = (pane === Pane.GROK) ? visibilty : false;
    this.showFieldSchema = (pane === Pane.FIELDSCHEMA) ? visibilty : false;
    this.showRawJson = (pane ===  Pane.RAWJSON) ? visibilty : false;
    this.showThreatTriage = (pane ===  Pane.THREATTRIAGE) ? visibilty : false;
  }

  onRawJsonChanged(): void {
    this.sensorFieldSchema.createFieldSchemaRows();
  }

  onAdvancedConfigFormClose(): void {
    this.showAdvancedParserConfiguration = false;
  }
}
