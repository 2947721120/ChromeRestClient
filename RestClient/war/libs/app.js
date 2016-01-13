'use strict';
/*******************************************************************************
 * Copyright 2012 Pawel Psztyc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
/* global chrome */
/**
 * Advanced Rest Client namespace
 */
var arc = arc || {};
/**
 * ARC app's namespace
 */
arc.app = arc.app || {};
/**
 * A namespace for the utilis functions
 */
arc.app.arc = {};
/**
 * Minimum supported version of Chrome.
 * The script will use manifest.minimum_chrome_version to determine minimum version.
 * It will use higher number to determine if Chrome is compatible.
 */
arc.app.arc.minSupportedVersion = 45;
/**
 * Get application's App ID
 */
arc.app.arc.getAppId = function(callback) {
  chrome.storage.local.get({
    'APP_ID': null
  }, function(result) {
    if (!result.APP_ID) {
      result.APP_ID = arc.app.utils.uuid();
      chrome.storage.local.set(result, function() {
        callback(result.APP_ID);
      });
    } else {
      callback(result.APP_ID);
    }
  });
};
/**
 * Check if current Chrome version is compatible with the app.
 */
arc.app.arc.checkCompatybility = function() {
  var version = arc.app.utils.getChromeVersion();
  var manifest = chrome.runtime.getManifest();
  var manMin = 0;
  /*jshint camelcase: false */
  if (manifest &&
    manifest.minimum_chrome_version) { // jscs:disable requireCamelCaseOrUpperCaseIdentifiers
    manMin =
      Number(manifest.minimum_chrome_version); //jscs:disable requireCamelCaseOrUpperCaseIdentifiers
  }
  /*jshint camelcase: true */
  var supported = Math.max(manMin, arc.app.arc.minSupportedVersion);
  if (Number(version.match(/(\d+)\./)[1]) < supported) {
    window.setTimeout(function() {
      document.querySelector('#incompatibleVersionDialog').open();
    }, 2000);
  }
};
