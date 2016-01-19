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

/** 
 * A parser for headers.
 */

/**
 * Advanced Rest Client namespace
 */
var arc = arc || {};
/**
 * ARC app's namespace
 */
arc.app = arc.app || {};
/**
 * A namespace for headers parser.
 */
arc.app.headers = {};
/** 
 * Filter array of headers and return not duplicated array of the same headers. 
 * Duplicated headers should be appended to already found one using coma separator. 
 *
 * @param {Array} headers 
 *                Headers array to filter. All objects in headers array must have "name" 
 *                and "value" keys.
 * @return {Array} An array of filtered headers.
 */
arc.app.headers.filter = function(headers) {
  var _tmp = {};
  headers.forEach((header) => {
    if (header.name in _tmp) {
      if (header.value && header.value.trim() !== '') {
        _tmp[header.name] += ', ' + header.value;
      }
    } else {
      _tmp[header.name] = header.value;
    }
  });
  var result = [];
  for (let _key in _tmp) {
    result[result.length] = {
      'name': _key,
      'value': _tmp[_key]
    };
  }
  return result;
};
/**
 * Parse headers array to Raw HTTP headers string.
 *
 * @param {Array} headersArray List of objects with "name" and "value" properties.
 * @returns {String} A HTTP representation of the headers.
 */
arc.app.headers.toString = function(headersArray) {
  if (!(headersArray instanceof Array)) {
    throw new Error('Headers must be an instance of Array');
  }
  if (headersArray.length === 0) {
    return '';
  }
  headersArray = arc.app.headers.filter(headersArray);
  var result = '';
  headersArray.forEach((header) => {
    if (result !== '') {
      result += '\n';
    }
    let key = header.name;
    let value = header.value;
    if (key && key.trim() !== '') {
      result += key + ': ';
      if (value && value.trim() !== '') {
        result += value;
      }
    }
  });
  return result;
};
/**
 * Parse HTTP headers input from string to array of a key:value pairs objects.
 *
 * @param {String} headersString Raw HTTP headers input
 * @returns {Array} The array of key:value objects
 */
arc.app.headers.toJSON = function(headersString) {
  if (headersString === null || headersString.trim() === '') {
    return [];
  }
  if (typeof headersString !== 'string') {
    throw new Error('Headers must be an instance of String.');
  }
  const result = [];
  const headers = headersString.split(/[\r\n]/gim);

  for (let i = 0, len = headers.length; i < len; i++) {
    let line = headers[i].trim();
    if (line === '') {
      continue;
    }
    let _tmp = line.split(/[:\r\n]/i);
    if (_tmp.length > 0) {
      let obj = {
        name: _tmp[0],
        value: ''
      };
      if (_tmp.length > 1) {
        _tmp.shift();
        _tmp = _tmp.filter(function(element) {
          return element.trim() !== '';
        });
        obj.value = _tmp.join(', ').trim();
      }
      result[result.length] = obj;
    }
  }
  return result;
};
/**
 * Helper method for old system: combine headers list with encoding value.
 * Note that this function will update the original array.
 *
 * @param {Array} headers An array of headers
 * @param {String} encoding An encoding string from the old request.
 * @return {Boolean} True if encoding has been added to the array.
 */
arc.app.headers._oldCombine = function(headers, encoding) {
  if (!(headers instanceof Array)) {
    throw new Error('Headers must be an array');
  }
  encoding = String(encoding);
  var ct = headers.filter((item) => item.name.toLowerCase() === 'content-type');
  if (ct.length === 0) {
    headers.push({
      'name': 'Content-Type',
      'value': encoding.trim()
    });
    return true;
  }
  return false;
};
/**
 * Get the Content-Type value from the headers.
 *
 * @param {Array|String} headers Either HTTP headers string or list of headers.
 * @return {String|null} A content-type header value or null if not found
 */
arc.app.headers.getContentType = function(headers) {
  if (typeof headers === 'string') {
    headers = arc.app.headers.toJSON(headers);
  }
  headers = arc.app.headers.filter(headers);
  var ct = headers.filter((item) => item.name.toLowerCase() === 'content-type');
  return (ct.length === 0) ? null : ct[0].value;
};
