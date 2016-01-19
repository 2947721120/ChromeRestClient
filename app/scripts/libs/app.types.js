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
/* global HAR */

/**
 * A base types definitions for the app.
 */

/**
 * A base object for all types with helper methods.
 */
class BaseObject {

  /**
   * Check if [passed] object contains all required properties declared in [required] array.
   * This method will throw an [Error] when required parameter is missing. 
   *
   * @param {Array<String>} required An array of required parameters.
   * @param {Object} passed An object on which test properties
   */
  assertRequiredKeys(required, passed) {
    const errors = [];
    required.forEach((name) => {
      if (!(name in passed)) {
        errors.push(name);
      }
    });
    if (errors.length > 0) {
      throw new Error('Missing parameters: ' + errors.join(', ') + '.');
    }
  }

  /**
   * Override toJSON behaviour so it will eliminate
   * all _* properies and replace it with a proper ones.
   */
  toJSON() {
    var copy = Object.assign({}, this);
    var keys = Object.keys(copy);
    var under = keys.filter((key) => key.indexOf('_') === 0);
    under.forEach((key) => {
      let realKey = key.substr(1);
      copy[realKey] = copy[key];
      delete copy[key];
    });
    return copy;
  }

}

/** 
 * A base class for request object.
 * This object will be stored in the database.
 */
class RequestObject extends BaseObject {

  constructor(opts) {
    super();
    super.assertRequiredKeys(['url', 'method', 'type'], opts);

    if(opts.id) {
      /**
       * A database ID.
       * It can be null / undefined if the object wasn't saved yet.
       */
      this.id = opts.id;
    }

    /**
     * A HAR object containing request info.
     * To initialize this object use helper class HAR:
     * `new HAR.Log({ ... })` or just pass a JSON object according to HAR specification.
     *
     * @type {HAR}
     */
    this._har = opts._har ? (opts._har instanceof HAR.Log) ? opts._har :
      new HAR.Log(opts._har) : null;
    /**
     * Request URL. It's an index and part of keyPath in the database. Therefore it's required.
     *
     * @type {String}
     */
    this.url = opts.url;
    /**
     * Request HTTP method. 
     * It's an index and part of keyPath in the database. Therefore it's required.
     *
     * @type {String}
     */
    this.method = opts.method;
    /**
     * A type of the object. Sub classes can set it by default.
     * Used to distinguish what kind of request is this (saved, history)
     *
     * @type {String}
     */
    this.type = opts.type ? opts.type : null;

    if (opts.har) {
      this.har = opts.har;
    }
  }

  set har(har) {
    if (har instanceof HAR.Log) {
      this._har = har;
    } else {
      this._har = new HAR.Log(har);
    }
  }

  get har() {
    return this._har;
  }

  toJSON() {
    return super.toJSON();
  }
}
/** 
 * A class of saved requests objects.
 * It's just a shorthand class, enlivenment for:
 * ```
 *  let ro = new RequestObject(...);
 *  ro.type = 'saved';
 * ```
 */
// jshint unused:false
class SavedRequestObject extends RequestObject {
  constructor(opts) {
    opts.type = 'saved';
    super(opts);
  }
  toJSON() {
    return super.toJSON();
  }
}
/** 
 * A class of history requests objects.
 * It's just a shorthand class, enlivenment for:
 * ```
 *  let ro = new RequestObject(...);
 *  ro.type = 'history';
 * ```
 */
// jshint unused:false
class HistoryRequestObject extends RequestObject {
  constructor(opts) {
    opts.type = 'history';
    super(opts);
  }
  toJSON() {
    return super.toJSON();
  }
}
/**
 * A class representing an entity in the data store with information about 
 * Google Drive referenced items.
 */
// jshint unused:false
class DriveObject extends BaseObject {

  constructor(opts) {
    super();

    super.assertRequiredKeys(['driveId', 'requestId'], opts);
    /**
     * A Google Drive item id.
     *
     * @type {String}
     */
    this.driveId = opts.driveId;
    /**
     * RequestObject data store id.
     *
     * @type {Number}
     */
    this.requestId = opts.requestId;
  }
}
/**
 * A class representing an entity in the data store with information 
 * about export to app server.
 */
// jshint unused:false
class ServerExportedObject extends BaseObject {

  constructor(opts) {
    super();

    super.assertRequiredKeys(['serverId', 'requestId'], opts);
    /**
     * An id of the item on the server.
     *
     * @type {String}
     */
    this.serverId = opts.serverId;
    /**
     * RequestObject data store id.
     *
     * @type {String}
     */
    this.requestId = opts.requestId;
  }
}
/**
 * A class representing an URL.
 */
class UrlObject extends BaseObject {

  constructor(opts) {
    super();
    super.assertRequiredKeys(['url'], opts);
    /**
     * An URL to store.
     * It's a database key path so it must be unique.
     *
     * @type {String}
     */
    this.url = opts.url;
    /**
     * Last used time as a number of milliseconds
     *
     * @type {Number}
     */
    this.time = opts.time;
  }
}
/**
 * A class representing an entity in the URL history data store.
 */
// jshint unused:false
class HistoryUrlObject extends UrlObject {
  constructor(opts) {
    super(opts);
  }
}
/**
 * A class representing an entity in the socket urls history data store.
 */
// jshint unused:false
class HistorySocketObject extends UrlObject {
  constructor(opts) {
    super(opts);
  }
}

class OrderedList extends BaseObject {

  constructor(opts) {

      super();

      /**
       * Project order on projects list
       *
       * @type {Number}
       */
      this.order = opts.order || 0;
    }
    /**
     * Change the order of the element.
     * This method will change the order of the element depending on arguments.
     * Note that this method don't care on which list item is. Program should perform this operation
     * only on elements that are already on the list.
     * Object that is changing position can also be changed using this method so it's safe to use 
     * it on all list elements.
     * For example if you want to move element from position 7 to position 2 the function should 
     * look as follows:
     * ```
     *  let list = [OrderedList1, OrderedList2, OrderedList3, OrderedList4, OrderedList5, 
     *    OrderedList6, OrderedList7];
     *  list.forEach((item) => {
     *    item.changeOrder('up', 2, list[6], list[6].order);
     *  });
     *  // now list is:
     *  // [OrderedList1, OrderedList2, OrderedList7, OrderedList3, OrderedList4, OrderedList5, 
     *     OrderedList6]
     * ```
     * Note: positions (order) are zero-based.
     *
     * @param {String} dir 
     *                 The direction of the change. Set to `up` if the element should go up 
     *                 on the list or `down` (default) otherwise.
     * @param {Number} change 
     *                 A target position of the moved element. If the element should finally 
     *                 be at position 2 this value should be 2.
     * @param {OrderedList} moved 
     *                      A moved OrderedList object as a reference to compare if current object 
     *                      is moved object.
     * @param {Number} movedOrder 
     *                 A base position of the moved element. If moved element previously was 
     *                 at position 7 this value should be 7.
     */
  changeOrder(dir, change, moved, movedOrder) {
    if (dir === 'up') {
      if (this.order === 0) {
        throw new Error('Can\'t move list element below 0 in this direction.');
      }
      this.moveUp(change, moved, movedOrder);
    } else {
      this.moveDown(change, moved, movedOrder);
    }
  }

  /**
   * Decrement order value so the element will go up on the list.
   */
  moveUp(change, moved, movedOrder) {
      if (this.order < change) {
        return;
      }
      if (this.order > movedOrder) {
        return;
      }
      if (this === moved) {
        this.order = change;
        return;
      }
      --this.order;
    }
    /**
     * Increment order value so the element will go down on the list.
     */
  moveDown(change, moved, movedOrder) {
    if (this.order > change) {
      return;
    }
    if (this.order < movedOrder) {
      return;
    }
    if (this === moved) {
      this.order = change;
      return;
    }
    ++this.order;
  }
}

/**
 * A class representing and entity in the Projects data store.
 */
// jshint unused:false
class ProjectObject extends OrderedList {

  constructor(opts) {
    super(opts);

    if (!opts.requestIds) {
      opts.requestIds = [];
    }
    if (!(opts.requestIds instanceof Array)) {
      throw new Error('`requestIds` property must be an array of ids of request objects');
    }
    if(opts.id) {
      /**
       * A database ID.
       * It can be null / undefined if the object wasn't saved yet.
       */
      this.id = opts.id;
    }
    /**
     * A list of all endpoints (RequestObjects) referenced to this project.
     *
     * @type {Array}
     */
    this.requestIds = opts.requestIds;
    /**
     * Project name
     *
     * @type {String}
     */
    this.name = opts.name;
    /**
     * Project creation time.
     *
     * @type {Date}
     */
    this.created = opts.time ? new Date(opts.time) : opts.created ? opts.created : undefined;
  }

  /**
   * Append request id to the list of ids.
   *
   * @param {Number} requestIds An ID of the request object.
   */
  addRequest(requestIds) {
    if (!requestIds) {
      throw new Error('Request ID must be set.');
    }
    this.requestIds.push(requestIds);
  }
}
/**
 * A class representing data export object.
 * This object will be used to export data to file as a structure wrapper.
 */
class FileExport extends BaseObject {
  constructor(opts) {
    super();
    this.kind = 'ARC#requestsDataExport';
    this.createdAt = new Date();
    this.version = arc.app.utils.appVer();

    if(!(opts.requests instanceof Array)) {
      console.warn('The opts.requests is not an array. Overriding');
      opts.requests = [];
    }
    if(!(opts.projects instanceof Array)) {
      console.warn('The opts.projects is not an array. Overriding');
      opts.projects = [];
    }
    opts.requests.forEach((item) => item.kind = 'ARC#requestsRequestObject');
    opts.projects.forEach((item) => item.kind = 'ARC#requestsProject');

    this.requests = opts.requests;
    this.projects = opts.projects;
  }
}
