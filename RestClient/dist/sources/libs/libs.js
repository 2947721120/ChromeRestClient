'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.analytics = arc.app.analytics || {};

arc.app.analytics._trackersConfig = [{
  'trackingId': 'UA-18021184-6',
  'cookieDomain': 'auto',
  'name': 'legacy'
}];

arc.app.analytics.init = function () {
  arc.app.analytics._loadLibrary();
  arc.app.analytics._initTranckers();
  arc.app.analytics._setCustomDimmensions();
  arc.app.analytics._setAppUid();
  arc.app.analytics._setChromeChannel();
};

arc.app.analytics._loadLibrary = function () {
  (function (i, s, o, g, r, a, m) {
    i['GoogleAnalyticsObject'] = r;
    i[r] = i[r] || function () {
      (i[r].q = i[r].q || []).push(arguments);
    }, i[r].l = 1 * new Date();
    a = s.createElement(o), m = s.getElementsByTagName(o)[0];
    a.async = 1;
    a.src = g;
    m.parentNode.insertBefore(a, m);
  })(window, document, 'script', 'https://www.google-analytics.com/analytics.js', 'ga');
};

arc.app.analytics._initTranckers = function () {
  arc.app.analytics._trackersConfig.forEach(function (item) {
    ga('create', item);
    if (!item.name) {
      return;
    }
    ga(item.name + '.set', 'checkProtocolTask', null);
  });
};

arc.app.analytics._getTrackerNames = function () {
  var names = arc.app.analytics._trackersConfig.map(function (item) {
    if (!!item.name) {
      return item.name;
    }
  });
  return names.filter(function (item) {
    return !!item;
  });
};

arc.app.analytics._setCustomDimmensions = function () {
  var names = arc.app.analytics._getTrackerNames();
  var appVersion = chrome && chrome.runtime && chrome.runtime.getManifest ? chrome.runtime.getManifest().version : 'Unknown';
  var chromeVer = arc.app.utils.getChromeVersion();
  names.forEach(function (name) {
    ga(name + '.set', 'dimension2', appVersion);
    ga(name + '.set', 'dimension1', chromeVer);
  });
};

arc.app.analytics._setAppUid = function () {
  var setUid = function setUid(uuid) {
    var names = arc.app.analytics._getTrackerNames();
    names.forEach(function (name) {
      ga(name + '.set', 'userId', uuid);
    });
  };
  if (chrome && chrome.storage && chrome.storage.sync) {
    chrome.storage.sync.get({
      'appuid': null
    }, function (data) {
      if (data.appuid) {
        setUid(data.appuid);
      } else {
        data.appuid = arc.app.utils.uuid();
        chrome.storage.sync.set(data, function () {
          setUid(data.appuid);
        });
      }
    });
  }
};

arc.app.analytics._setChromeChannel = function () {
  if (!window.navigator.onLine) {
    return;
  }
  arc.app.analytics._loadCSV().then(function (obj) {
    if (!(obj instanceof Array)) {
      return;
    }
    var version = arc.app.utils.getChromeVersion();
    for (var i = 0, size = obj[1].length; i < size; i++) {
      var item = obj[1][i];

      if (item.current_version === version || item.previous_version === version) {
        var channel = item.channel;
        var names = arc.app.analytics._getTrackerNames();
        for (var j = 0, namesSize = names.length; j < namesSize; j++) {
          var name = names[j];
          ga(name + '.set', 'dimension3', channel);
        }
        return;
      }
    }
  }).catch(function (cause) {});
};

arc.app.analytics._loadCSV = function () {
  var init = {
    mode: 'no-cors',
    cache: 'force-cache'
  };
  return fetch('https://omahaproxy.appspot.com/all?csv=1', init).then(function (response) {
    return response.text();
  }).then(function (data) {
    return data.split('\n');
  }).then(function (lines) {
    var keys = lines[0].split(',');
    var data = [];
    for (var i = 1; i < lines.length; ++i) {
      var line = lines[i];
      if (!line.length) {
        continue;
      }
      var columns = line.split(',');
      var row = {};
      for (var j = 0; j < columns.length; ++j) {
        row[keys[j]] = columns[j];
      }
      data.push(row);
    }
    return [keys, data];
  });
};

arc.app.analytics.sendEvent = function (category, action, label, value) {
  var names = arc.app.analytics._getTrackerNames();
  var config = {
    hitType: 'event',
    eventCategory: category,
    eventAction: action,
    eventLabel: label
  };
  if (typeof value !== 'undefined') {
    config.eventValue = value;
  }
  names.forEach(function (name) {
    ga(name + '.send', config);
  });
};

arc.app.analytics.sendScreen = function (screenName) {
  var names = arc.app.analytics._getTrackerNames();
  names.forEach(function (name) {
    ga(name + '.send', 'pageview', screenName);
  });
};

arc.app.analytics.sendException = function (exception, isFatal) {
  var names = arc.app.analytics._getTrackerNames();
  var value = {
    'exDescription': '' + exception
  };
  if (typeof isFatal !== 'undefined') {
    value.exFatal = isFatal;
  }
  names.forEach(function (name) {
    ga(name + '.send', 'exception', value);
  });
};
'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.db = arc.app.db || {};

arc.app.db.idb = {};

arc.app.db.idb.open = function () {
  return new Promise(function (resolve, reject) {
    var db = new Dexie('arc');
    db.version(1).stores({
      headers: '&[key+type]',
      statuses: '&key',
      historyUrls: '&url',
      historySockets: '&url',
      requestObject: '++id,url,method,[url+method]',
      driveObjects: '[requestId+requestId],driveId,requestId',
      serverExportObjects: '[serverId+requestId],serverId,requestId',
      projectObjects: '++id,*requestIds'
    });
    db.on('ready', function () {
      arc.app.db.idb._db = db;
      arc.app.db.idb.appVer = chrome.runtime.getManifest().version;
      return db.statuses.count(function (count) {
        if (count === 0) {
          console.log('The statuses database is empty. Populating it...');
          return new Dexie.Promise(function (resolve, reject) {
            arc.app.db.idb.downloadDefinitions().then(resolve, reject);
          }).then(function (defs) {
            return db.transaction('rw', db.statuses, db.headers, function () {
              var codes = defs.codes;
              defs.requests.forEach(function (item) {
                return item.type = 'request';
              });
              defs.responses.forEach(function (item) {
                return item.type = 'response';
              });
              var headers = defs.requests.concat(defs.responses);
              codes.forEach(function (item) {
                db.statuses.add(item);
              });
              headers.forEach(function (item) {
                db.headers.add(item);
              });
            });
          }).then(function () {
            console.log('The database has been populated with data.');
          });
        }
      }).then(function () {
        return new Dexie.Promise(function (resolve) {
          var upgrade = {
            upgraded: {
              indexeddb: false
            }
          };
          chrome.storage.local.get(upgrade, function (upgrade) {
            if (upgrade.upgraded.indexeddb) {
              arc.app.db._adapter = 'indexeddb';
            }
            resolve(upgrade.upgraded.indexeddb);
          });
        });
      }).then(arc.app.db.idb._getSQLdata).then(arc.app.db.idb._converSqlIdb).then(arc.app.db.idb._storeUpgrade).then(function (result) {
        if (result === null) {
          return;
        }
        console.info('Database has been upgraded from WebSQL to IndexedDB.');
        arc.app.db._adapter = 'indexeddb';
        var upgrade = {
          upgraded: {
            indexeddb: true
          }
        };
        chrome.storage.local.set(upgrade, function () {
          console.info('Upgrade finished.');
        });
      });
    });
    db.open().then(function () {
      resolve(db);
    }).catch(function (error) {
      reject(error);
    });
  });
};
arc.app.db.idb.downloadDefinitions = function () {
  return fetch('/assets/definitions.json').then(function (response) {
    return response.json();
  });
};

arc.app.db.idb.getStatusCode = function (code) {
  return new Promise(function (resolve, reject) {
    arc.app.db.idb.open().then(function (db) {
      db.statuses.get(code).then(resolve).catch(reject).finally(function () {
        db.close();
      });
    }).catch(function (e) {
      return reject(e);
    });
  });
};

arc.app.db.idb._upgradeWebSQL = function () {
  return new Dexie.Promise(function (resolve, reject) {
    if (!arc.app.db.websql) {
      resolve();
      reject();
      return;
    }

    return Dexie.Promise.all([arc.app.db.idb._upgradeWebSLurlHistory(), arc.app.db.idb._upgradeWebSLSocketUrlHistory()]);
  });
};

arc.app.db.idb._getSQLdata = function (dontUpgrade) {
  if (dontUpgrade) {
    return null;
  }

  var data = {};
  return new Dexie.Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'SELECT * FROM urls WHERE 1';
        tx.executeSql(sql, [], function (tx, result) {
          data.urls = Array.from(result.rows);
          sql = 'SELECT * FROM websocket_data WHERE 1';
          tx.executeSql(sql, [], function (tx, result) {
            data.websocketData = Array.from(result.rows);
            sql = 'SELECT * FROM history WHERE 1';
            tx.executeSql(sql, [], function (tx, result) {
              data.history = Array.from(result.rows);
              sql = 'SELECT * FROM projects WHERE 1';
              tx.executeSql(sql, [], function (tx, result) {
                data.projects = Array.from(result.rows);
                sql = 'SELECT * FROM request_data WHERE 1';
                tx.executeSql(sql, [], function (tx, result) {
                  data.requestData = Array.from(result.rows);
                  sql = 'SELECT * FROM exported WHERE 1';
                  tx.executeSql(sql, [], function (tx, result) {
                    data.exported = Array.from(result.rows);
                    resolve(data);
                  }, function (tx, error) {
                    return reject(error);
                  });
                }, function (tx, error) {
                  return reject(error);
                });
              }, function (tx, error) {
                return reject(error);
              });
            }, function (tx, error) {
              return reject(error);
            });
          }, function (tx, error) {
            return reject(error);
          });
        }, function (tx, error) {
          return reject(error);
        });
      });
    });
  });
};

arc.app.db.createRequestKey = function (method, url) {
  var args = Array.from(arguments);
  if (args.length !== 2) {
    throw new Error('Number of arguments requires is 2 but ' + args.length + ' has been provided');
  }
  return method + ':' + url;
};

arc.app.db.idb._converSqlIdb = function (data) {
  var _this = this;

  if (!data) {
    return null;
  }
  var requests = [];
  var urlHistory = [];
  var socketHistory = [];
  var exportedSize = data.exported.length;
  data.requestData.forEach(function (item) {
    var obj = arc.app.db.idb._createHARfromSql.call(_this, item);
    obj.type = 'saved';

    if (item.project) {
      obj.project = item.project;
    }
    for (var i = 0; i < exportedSize; i++) {
      if (data.exported[i].reference_id === item.id) {
        obj.exported = i;
        break;
      }
    }
    requests.push(obj);
  });
  data.history.forEach(function (item) {
    var obj = arc.app.db.idb._createHARfromSql.call(_this, item);
    requests.push(obj);
  });
  data.urls.forEach(function (item) {
    var obj = new HistoryUrlObject({
      url: item.url,
      time: item.time
    });
    urlHistory.push(obj);
  });
  data.websocketData.forEach(function (item) {
    var obj = new HistorySocketObject({
      url: item.url,
      time: item.time
    });
    socketHistory.push(obj);
  });

  return {
    indexeddb: {
      requests: requests,
      urlHistory: urlHistory,
      socketHistory: socketHistory
    },
    websql: data
  };
};

arc.app.db.idb._storeUpgrade = function (data) {
  if (!data) {
    return null;
  }
  var db = arc.app.db.idb._db;
  console.info('Upgrading webSQL to IndexedDb');
  return db.transaction('rw', db.historyUrls, db.historySockets, db.requestObject, db.serverExportObjects, db.projectObjects, function () {
    console.info('Entered transaction. Ready to save data.');
    console.info('Inserting URL history');
    data.indexeddb.urlHistory.forEach(function (item) {
      db.historyUrls.put(item);
    });
    console.info('Inserting Socket URL history');
    data.indexeddb.socketHistory.forEach(function (item) {
      db.historySockets.put(item);
    });
    var projects = {};
    var exported = [];
    var promises = [];

    var insertRequest = function insertRequest(db, item) {
      var referencedProjectId = item.project;
      var referencedExported = item.exported;
      delete item.project;
      delete item.exported;
      return db.requestObject.add(item).then(function (requestId) {
        if (referencedProjectId) {
          if (!(referencedProjectId in projects)) {
            var _projects = data.websql.projects.filter(function (project) {
              return project.id === referencedProjectId;
            });
            if (_projects.length === 1) {
              var project = new ProjectObject({
                time: _projects[0].time,
                name: _projects[0].name,
                requestIds: [requestId]
              });
              projects[referencedProjectId] = project;
            } else if (_projects.length > 1) {
              console.warn('Projects filtered array has more than one element ' + 'and it should not happen.');
            }
          } else {
            projects[referencedProjectId].addRequest(requestId);
          }
        }
        if (referencedExported) {
          var exportData = data.websql.exported[referencedExported];
          var exportObject = new ServerExportedObject({
            serverId: exportData.gaeKey,
            requestId: requestId
          });
          exported.push(exportObject);
        }
      });
    };

    console.info('Inserting requests');
    data.indexeddb.requests.forEach(function (item) {
      promises.push(insertRequest(db, item));
    });

    return Promise.all(promises).then(function () {
      console.info('Exported items to be inserted: %d, projects items to be inserted: %d', exported.length, Object.keys(projects).length);
      if (Object.keys(projects).length > 0) {
        console.info('Inserting projects');
        Object.keys(projects).forEach(function (projectKey) {
          db.projectObjects.add(projects[projectKey]);
        });
      }
      if (exported.length > 0) {
        console.info('Inserting exported');
        exported.forEach(function (item) {
          db.serverExportObjects.add(item);
        });
      }
    });
  });
};

arc.app.db.idb._createHARfromSql = function (item) {
  var creator = new HAR.Creator({
    name: 'Advanced REST client',
    version: arc.app.db.idb.appVer,
    comment: 'Created during WebSQL update to IndexedDB'
  });
  var browser = new HAR.Browser({
    name: 'Chrome',
    version: 'unknown'
  });
  var log = new HAR.Log({
    'comment': 'Imported from WebSQL implementation',
    'version': 1.2,
    'creator': creator,
    'browser': browser
  });
  var requestHeaders = arc.app.db.headers.toJSON(item.headers);
  var request = new HAR.Request({
    url: item.url,
    httpVersion: 'HTTP/1.1',
    method: item.method
  });
  if (['GET', 'HEAD'].indexOf(item.method) === -1) {
    arc.app.db.headers._oldCombine(requestHeaders, item.encoding);
    var contentType = arc.app.db.headers.getContentType(requestHeaders) || 'application/x-www-form-urlencoded';
    var post = new HAR.PostData({
      mimeType: contentType,
      text: item.payload
    });
    request.postData = post;
  }
  request.headers = requestHeaders;
  var page = new HAR.Page({
    id: arc.app.db.createRequestKey(item.method, item.url),
    title: item.name,
    startedDateTime: new Date(item.time),
    pageTimings: {}
  });
  var entry = new HAR.Entry({
    startedDateTime: new Date(item.time),
    request: request,
    response: {
      status: '0',
      statusText: 'No response'
    }
  });
  entry.setPage(page);
  log.addPage(page);
  log.addEntry(entry, page.id);

  var obj = new RequestObject({
    'har': log,
    'url': item.url,
    'method': item.method,
    'type': 'history'
  });
  return obj;
};

arc.app.db.idb._upgradeWebSLurlHistory = function () {
  return new Dexie.Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'SELECT * FROM urls WHERE 1';
        tx.executeSql(sql, [], function (tx, result) {
          if (result.rows.length === 0) {
            resolve();
            return;
          }
          var data = Array.from(result.rows);
          arc.app.db.idb.open().then(function (db) {
            db.transaction('rw', db.historyUrls, function (historyUrls) {
              data.forEach(function (item) {
                historyUrls.add(item);
              });
            }).catch(reject).finally(function () {
              db.close();
              resolve();
            });
          }).catch(function (e) {
            return reject(e);
          });
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      return reject(e);
    });
  });
};

arc.app.db.idb._upgradeWebSLSocketUrlHistory = function () {
  return new Dexie.Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'SELECT * FROM websocket_data WHERE 1';
        tx.executeSql(sql, [], function (tx, result) {
          if (result.rows.length === 0) {
            resolve();
            return;
          }
          var data = Array.from(result.rows);
          arc.app.db.idb.open().then(function (db) {
            db.transaction('rw', db.historySockets, function (historySockets) {
              data.forEach(function (item) {
                historySockets.add(item);
              });
            }).catch(reject).finally(function () {
              db.close();
              resolve();
            });
          }).catch(function (e) {
            return reject(e);
          });
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      return reject(e);
    });
  });
};

arc.app.db.idb.getHeaderByName = function (name, type) {
  return new Promise(function (resolve, reject) {
    arc.app.db.idb.open().then(function (db) {
      var result = null;
      db.headers.where('[key+type]').equals([name, type]).each(function (header) {
        result = header;
      }).catch(reject).finally(function () {
        db.close();
        resolve(result);
      });
    }).catch(function (e) {
      return reject(e);
    });
  });
};

arc.app.db.idb.addUrlHistory = function (url, time) {
  return new Promise(function (resolve, reject) {
    arc.app.db.idb.open().then(function (db) {
      db.transaction('rw', db.historyUrls, function (historyUrls) {
        historyUrls.add({
          'url': url,
          'time': time
        });
      }).then(function () {
        resolve();
      }).catch(reject).finally(function () {
        db.close();
      });
    }).catch(function (e) {
      return reject(e);
    });
  });
};

arc.app.db.idb.updateUrlHistory = function (url, time) {
  return arc.app.db.idb.open().then(function (db) {
    return db.historyUrls.get(url).then(function (hurl) {
      if (!hurl) {
        hurl = {
          'url': url,
          'time': time
        };
      } else {
        hurl.time = time;
      }
      return db.transaction('rw', db.historyUrls, function (historyUrls) {
        return historyUrls.put(hurl);
      }).then(function () {
        return db.historyUrls.get(url);
      });
    }).finally(function () {
      db.close();
    });
  });
};

arc.app.db.idb.getHistoryUrls = function (query) {
  return arc.app.db.idb.open().then(function (db) {
    return db.historyUrls.where('url').startsWithIgnoreCase(query).toArray().finally(function () {
      db.close();
    });
  });
};
'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.db = arc.app.db || {};

arc.app.db._adapter = arc.app.db._adapter || 'websql';

arc.app.db.websql = {};

arc.app.db.websql._db = null;

arc.app.db.websql._dbVersion = '';

arc.app.db.websql.onerror = function (e) {
  console.error('app::db:error');
  console.log(e.message);
};

arc.app.db.websql.open = function () {
  return new Promise(function (resolve, reject) {
    if (arc.app.db.websql._db) {
      resolve(arc.app.db.websql._db);
      return;
    }
    arc.app.db.websql._db = openDatabase('restClient', arc.app.db.websql._dbVersion, 'Rest service database', 10000000);
    arc.app.db.websql._dbUpgrade(arc.app.db.websql._db).then(function () {
      resolve(arc.app.db.websql._db);
    }).catch(function (cause) {
      reject(cause);
    });
  });
};

arc.app.db.websql._dbUpgrade = function (db) {
  return new Promise(function (resolve, reject) {
    db.transaction(function (tx) {
      var sql = 'CREATE TABLE IF NOT EXISTS exported (' + 'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' + 'reference_id INTEGER NOT NULL, ' + 'gaeKey TEXT, ' + 'type TEXT default \'form\')';
      tx.executeSql(sql, []);

      sql = 'CREATE TABLE IF NOT EXISTS form_encoding (' + 'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' + 'encoding TEXT NOT NULL)';
      tx.executeSql(sql, []);

      sql = 'CREATE TABLE IF NOT EXISTS headers (' + 'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' + 'name TEXT NOT NULL, desc TEXT, example TEXT, type TEXT)';
      tx.executeSql(sql, []);

      sql = 'CREATE TABLE IF NOT EXISTS history (' + 'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' + 'url TEXT NOT NULL, method TEXT NOT NULL, ' + 'encoding TEXT NULL, headers TEXT NULL, ' + 'payload TEXT NULL, time INTEGER)';
      tx.executeSql(sql, []);

      sql = 'CREATE TABLE IF NOT EXISTS projects (' + 'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' + 'name TEXT NOT NULL, ' + 'time INTEGER)';
      tx.executeSql(sql, []);

      sql = 'CREATE TABLE IF NOT EXISTS request_data (' + 'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' + 'project INTEGER DEFAULT 0, name TEXT NOT NULL, ' + 'url TEXT NOT NULL, method TEXT NOT NULL, ' + 'encoding TEXT NULL, headers TEXT NULL, ' + 'payload TEXT NULL, skipProtocol INTEGER DEFAULT 0, ' + 'skipServer INTEGER DEFAULT 0, ' + 'skipParams INTEGER DEFAULT 0, ' + 'skipHistory INTEGER DEFAULT 0, ' + 'skipMethod INTEGER DEFAULT 0, ' + 'skipPayload INTEGER DEFAULT 0, ' + 'skipHeaders INTEGER DEFAULT 0, ' + 'skipPath INTEGER DEFAULT 0, time INTEGER)';
      tx.executeSql(sql, []);

      sql = 'CREATE TABLE IF NOT EXISTS statuses (' + 'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' + 'code INTEGER NOT NULL, label TEXT, desc TEXT)';
      tx.executeSql(sql, []);

      sql = 'CREATE TABLE IF NOT EXISTS urls (' + 'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' + 'time INTEGER, ' + 'url TEXT NOT NULL)';
      tx.executeSql(sql, []);

      sql = 'CREATE TABLE IF NOT EXISTS websocket_data (' + 'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' + 'url TEXT NOT NULL, ' + 'time INTEGER)';
      tx.executeSql(sql, []);
    }, function (tx, error) {
      reject(error);
    }, function () {
      resolve();
    });
  });
};

arc.app.db.websql.insertStatusCodes = function (codesArray) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        codesArray.forEach(function (item) {
          var sql = 'INSERT INTO statuses (code,label,desc) VALUES (?,?,?)';
          tx.executeSql(sql, [item.key, item.label, item.desc]);
        });
      }, function (tx, error) {
        reject(error);
      }, function () {
        resolve();
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.insertHeadersDefinitions = function (headers) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        headers.forEach(function (item) {
          var sql = 'INSERT INTO headers (name,desc,example,type) VALUES (?,?,?,?)';
          tx.executeSql(sql, [item.key, item.desc, item.example, item.type]);
        });
      }, function (tx, error) {
        reject(error);
      }, function () {
        resolve();
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.getStatusCode = function (code) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'SELECT * FROM statuses WHERE code = ?';
        tx.executeSql(sql, [code], function (tx, result) {
          if (result.rows.length === 0) {
            resolve(null);
          } else {
            resolve(result.rows.item(0));
          }
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.getHeaderByName = function (name, type) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'SELECT * FROM headers WHERE name LIKE (?) AND type=?';
        tx.executeSql(sql, [name, type], function (tx, result) {
          if (result.rows.length === 0) {
            resolve(null);
          } else {
            resolve(Array.from(result.rows));
          }
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.addUrlHistory = function (url, time) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'INSERT INTO urls (url,time) VALUES (?,?)';
        tx.executeSql(sql, [url, time], function (tx, result) {
          resolve(result);
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.updateUrlHistory = function (id, time) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'UPDATE urls SET time = ? WHERE ID = ?';
        tx.executeSql(sql, [time, id], function (tx, result) {
          resolve(result);
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.getHistoryUrls = function (query) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'SELECT url FROM urls WHERE url LIKE ? ORDER BY time';
        tx.executeSql(sql, [query], function (tx, result) {
          if (result.rows.length === 0) {
            resolve(null);
          } else {
            resolve(Array.from(result.rows));
          }
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.addProject = function (name, time) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'INSERT INTO projects (name, time) VALUES (?,?)';
        tx.executeSql(sql, [name, time], function (tx, result) {
          resolve(result);
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.importProjects = function (projectsArray) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        projectsArray.forEach(function (item) {
          var sql = 'INSERT INTO projects (name, time) VALUES (?,?)';
          tx.executeSql(sql, [item.name, item.time]);
        });
      }, function (tx, error) {
        reject(error);
      }, function () {
        resolve();
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.updateProject = function (name, time, id) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'UPDATE projects SET name = ?, time = ? WHERE ID = ?';
        tx.executeSql(sql, [name, time, id], function (tx, result) {
          resolve(result);
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.listProjects = function () {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'SELECT * FROM projects WHERE 1';
        tx.executeSql(sql, [], function (tx, result) {
          if (result.rows.length === 0) {
            resolve(null);
          } else {
            resolve(Array.from(result.rows));
          }
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};

arc.app.db.websql.getProject = function (id) {
  return new Promise(function (resolve, reject) {
    arc.app.db.websql.open().then(function (db) {
      db.transaction(function (tx) {
        var sql = 'SELECT * FROM projects WHERE ID = ?';
        tx.executeSql(sql, [id], function (tx, result) {
          if (result.rows.length === 0) {
            resolve(null);
          } else {
            resolve(Array.from(result.rows));
          }
        }, function (tx, error) {
          reject(error);
        });
      });
    }).catch(function (e) {
      reject(e);
    });
  });
};
'use strict';

var APP_ID = '10525470235';

var API_KEY_PICKER = 'AIzaSyACzi_VRqOHzLj_Lf7IdJgQAO3jaw5SMNU';

var boundary = 'ARCFormBoundary49nr1hyovoq1tt9';
var delimiter = '\r\n--' + boundary + '\r\n';
var closeDelim = '\r\n--' + boundary + '--';

var appMimeType = 'application/restclient+data';

var appFileExtension = 'arc';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.drive = {};

arc.app.drive.isExtension = !!chrome.runtime.getBackgroundPage;

arc.app.drive._callbacks = new Set();

arc.app.drive._authCallbacks = new Set();

arc.app.drive._initialized = false;

arc.app.drive.picker = {};

arc.app.drive.picker.initialized = false;

arc.app.drive.picker._callbacks = new Set();

arc.app.drive.appendLoader = function () {
  var wrapper = document.createElement('div');
  var loader = document.createElement('div');
  loader.classList.add('loaderImage');
  wrapper.classList.add('actionDomLoader');
  wrapper.appendChild(loader);
  document.body.appendChild(wrapper);
};

arc.app.drive.removeLoader = function () {
  var loader = document.querySelector('.actionDomLoader');
  if (loader) {
    loader.parentNode.removeChild(loader);
  }
};

arc.app.drive.loadApi = function (callback) {
  if (arc.app.drive._initialized) {
    callback.call(this);
    return;
  }
  arc.app.drive.appendLoader();
  arc.app.drive._callbacks.add(callback);

  var script = document.createElement('script');
  script.src = 'https://apis.google.com/js/client.js?onload=handleDriveClientLoad';
  script.type = 'text/javascript';
  script.async = true;
  document.getElementsByTagName('head')[0].appendChild(script);
};

arc.app.drive.loadDriveApi = function () {
  gapi.client.load('drive', 'v2', function () {
    arc.app.drive._initialized = true;
    arc.app.drive.removeLoader();

    var _iteratorNormalCompletion = true;
    var _didIteratorError = false;
    var _iteratorError = undefined;

    try {
      for (var _iterator = arc.app.drive._callbacks[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
        var clb = _step.value;

        clb.call(arc);
        arc.app.drive._callbacks.delete(clb);
      }
    } catch (err) {
      _didIteratorError = true;
      _iteratorError = err;
    } finally {
      try {
        if (!_iteratorNormalCompletion && _iterator.return) {
          _iterator.return();
        }
      } finally {
        if (_didIteratorError) {
          throw _iteratorError;
        }
      }
    }
  });
};

arc.app.drive.checkDriveAuth = function (callback) {
  arc.app.drive.auth(callback);
};

arc.app.drive.auth = function (callback) {
  chrome.identity.getAuthToken({ 'interactive': true }, callback);
};

arc.app.drive._setAccessToken = function (authResult) {
  if (authResult && authResult.access_token) {
    gapi.auth.setToken({
      'access_token': authResult.access_token
    });
  }
};

arc.app.drive.insertFile = function (parentId, filename, content, callback) {
  try {
    if (typeof content !== 'string') {
      content = JSON.stringify(content);
    }

    var metadata = {
      'title': filename + '.' + appFileExtension,
      'mimeType': appMimeType,
      'parents': [{
        'id': parentId
      }]
    };
    var metaString = JSON.stringify(metadata);
    var payload = btoa(content);
    var multipartRequestBody = delimiter + 'Content-Type: application/json\r\n\r\n' + metaString;
    multipartRequestBody += delimiter + 'Content-Type: ' + appMimeType + '\r\n';
    multipartRequestBody += 'Content-Transfer-Encoding: base64\r\n\r\n' + payload + closeDelim;
    var request = gapi.client.request({
      'path': '/upload/drive/v2/files',
      'method': 'POST',
      'params': {
        'uploadType': 'multipart'
      },
      'headers': {
        'Content-Type': 'multipart/mixed; boundary="' + boundary + '"'
      },
      'body': multipartRequestBody
    });
    request.execute(callback);
  } catch (e) {
    callback({
      'error': e
    });
  }
};

arc.app.drive.updateFile = function (fileId, content, callback) {
  try {
    if (typeof content !== 'string') {
      content = JSON.stringify(content);
    }
    var metadata = {
      'mimeType': appMimeType
    };
    var metaString = JSON.stringify(metadata);
    var payload = btoa(content);
    var multipartRequestBody = delimiter + 'Content-Type: application/json\r\n\r\n' + metaString;
    multipartRequestBody += delimiter + 'Content-Type: ' + appMimeType + '\r\n';
    multipartRequestBody += 'Content-Transfer-Encoding: base64\r\n\r\n' + payload + closeDelim;
    var request = gapi.client.request({
      'path': '/upload/drive/v2/files/' + fileId,
      'method': 'PUT',
      'params': {
        'uploadType': 'multipart'
      },
      'headers': {
        'Content-Type': 'multipart/mixed; boundary="' + boundary + '"'
      },
      'body': multipartRequestBody
    });
    request.execute(callback);
  } catch (e) {
    callback({
      'error': e
    });
  }
};

arc.app.drive.getFileMeta = function (fileId, callback) {
  var request = gapi.client.request({
    'path': '/drive/v2/files/' + fileId,
    'method': 'GET',
    'params': {
      'fields': 'downloadUrl,title,etag'
    }
  });
  request.execute(function (resp) {
    callback(resp);
  });
};

arc.app.drive.getFile = function (downloadUrl, callback) {
  var accessToken = gapi.auth.getToken().access_token;
  if (!accessToken) {
    console.warn('Access token is not available. Call rejected');
    callback.call(this, {
      'error': 'Not authorized'
    });
    return;
  }
  var xhr = new XMLHttpRequest();
  xhr.open('GET', downloadUrl);
  xhr.setRequestHeader('Authorization', 'Bearer ' + accessToken);
  xhr.onload = function () {
    callback.call(arc, xhr.responseText);
  };
  xhr.onerror = function (e) {
    callback.call(arc, e);
  };
  xhr.send();
};
arc.app.drive.listFiles = function (mimeType, nextPageToken, query, callback) {
  var accessToken = gapi.auth.getToken().access_token;
  if (!accessToken) {
    console.warn('Access token is not available. Call rejected');
    callback.call(this, {
      'error': 'Not authorized'
    });
    return;
  }
  var q = 'mimeType="' + mimeType + '" and trashed = false';
  if (query) {
    q += ' and title contains \'' + query + '\'';
  }
  var params = {
    'q': q,
    'maxResults': 50,
    'fields': 'items(createdDate,iconLink,id,title),nextLink,nextPageToken'
  };
  if (nextPageToken !== null) {
    params.pageToken = nextPageToken;
  }

  if (accessToken) {
    gapi.auth.setToken({
      'access_token': accessToken
    });
  }
  try {
    var request = gapi.client.request({
      'path': '/drive/v2/files',
      'method': 'GET',
      'params': params
    });
    request.execute(callback);
  } catch (e) {
    callback.call(this, {
      'error': e
    });
  }
};

arc.app.drive.picker.load = function (callback) {
  if (arc.app.drive.picker.initialized) {
    callback.call(window);
    return;
  }
  arc.app.drive.appendLoader();
  arc.app.drive.picker._callbacks.add(callback);

  var script = document.createElement('script');
  script.src = 'https://apis.google.com/js/api.js?onload=handlePickerLoad';
  script.type = 'text/javascript';
  script.async = true;
  document.getElementsByTagName('head')[0].appendChild(script);
};

arc.app.drive.picker.loadHandler = function () {
  arc.app.drive.picker.initialized = true;
  arc.app.drive.removeLoader();
  var _iteratorNormalCompletion2 = true;
  var _didIteratorError2 = false;
  var _iteratorError2 = undefined;

  try {
    for (var _iterator2 = arc.app.drive.picker._callbacks[Symbol.iterator](), _step2; !(_iteratorNormalCompletion2 = (_step2 = _iterator2.next()).done); _iteratorNormalCompletion2 = true) {
      var clb = _step2.value;

      clb.call(arc);
      arc.app.drive.picker._callbacks.delete(clb);
    }
  } catch (err) {
    _didIteratorError2 = true;
    _iteratorError2 = err;
  } finally {
    try {
      if (!_iteratorNormalCompletion2 && _iterator2.return) {
        _iterator2.return();
      }
    } finally {
      if (_didIteratorError2) {
        throw _iteratorError2;
      }
    }
  }
};

arc.app.drive.picker._constructPicker = function (authToken, callback, views) {
  var pickerBuilder = new google.picker.PickerBuilder().setDeveloperKey(API_KEY_PICKER).setOAuthToken(authToken).setCallback(callback).setAppId(APP_ID).disableFeature(google.picker.Feature.MULTISELECT_ENABLED);
  views.forEach(function (view) {
    pickerBuilder.addView(view);
  });
  return pickerBuilder;
};

arc.app.drive.picker.getAppFile = function (authToken, callback) {
  var filesView = new google.picker.View(google.picker.ViewId.DOCS);
  filesView.setMimeTypes('application/restclient+data');

  var fn = function fn(data) {
    if (data[google.picker.Response.ACTION] === google.picker.Action.PICKED || data[google.picker.Response.ACTION] === google.picker.Action.CANCEL) {
      callback.call(arc, data);
    }
  };
  var pickerBuilder = arc.app.drive.picker._constructPicker(authToken, fn, [filesView]);
  var picker = pickerBuilder.build();
  picker.setVisible(true);
};

arc.app.drive.picker.getFolder = function (authToken, callback) {
  var foldersView = new google.picker.DocsView(google.picker.ViewId.FOLDERS);
  foldersView.setIncludeFolders(true);
  foldersView.setSelectFolderEnabled(true);

  var fn = function fn(data) {
    if (data[google.picker.Response.ACTION] === google.picker.Action.PICKED || data[google.picker.Response.ACTION] === google.picker.Action.CANCEL) {
      callback.call(arc, data);
    }
  };
  var pickerBuilder = arc.app.drive.picker._constructPicker(authToken, fn, [foldersView]);
  pickerBuilder.setTitle('Select a folder');
  var picker = pickerBuilder.build();
  picker.setVisible(true);
};

window.handleDriveClientLoad = function () {
  arc.app.drive.loadDriveApi();
};

window.handlePickerLoad = function () {
  gapi.load('picker', {
    'callback': arc.app.drive.picker.loadHandler
  });
};
'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.db = arc.app.db || {};

arc.app.db.headers = {};

arc.app.db.headers.filter = function (headers) {
  var _tmp = {};
  headers.forEach(function (header) {
    if (header.name in _tmp) {
      if (header.value && header.value.trim() !== '') {
        _tmp[header.name] += ', ' + header.value;
      }
    } else {
      _tmp[header.name] = header.value;
    }
  });
  var result = [];
  for (var _key in _tmp) {
    result[result.length] = {
      'name': _key,
      'value': _tmp[_key]
    };
  }
  return result;
};

arc.app.db.headers.toString = function (headersArray) {
  if (!(headersArray instanceof Array)) {
    throw new Error('Headers must be an instance of Array');
  }
  if (headersArray.length === 0) {
    return '';
  }
  headersArray = arc.app.db.headers.filter(headersArray);
  var result = '';
  headersArray.forEach(function (header) {
    if (result !== '') {
      result += '\n';
    }
    var key = header.name;
    var value = header.value;
    if (key && key.trim() !== '') {
      result += key + ': ';
      if (value && value.trim() !== '') {
        result += value;
      }
    }
  });
  return result;
};

arc.app.db.headers.toJSON = function (headersString) {
  if (headersString === null || headersString.trim() === '') {
    return [];
  }
  if (typeof headersString !== 'string') {
    throw new Error('Headers must be an instance of String.');
  }
  var result = [];
  var headers = headersString.split(/[\r\n]/gim);

  for (var i = 0, len = headers.length; i < len; i++) {
    var line = headers[i].trim();
    if (line === '') {
      continue;
    }
    var _tmp = line.split(/[:\r\n]/i);
    if (_tmp.length > 0) {
      var obj = {
        name: _tmp[0],
        value: ''
      };
      if (_tmp.length > 1) {
        _tmp.shift();
        _tmp = _tmp.filter(function (element) {
          return element.trim() !== '';
        });
        obj.value = _tmp.join(', ').trim();
      }
      result[result.length] = obj;
    }
  }
  return result;
};

arc.app.db.headers._oldCombine = function (headers, encoding) {
  if (!(headers instanceof Array)) {
    throw new Error('Headers must be an array');
  }
  encoding = String(encoding);
  var ct = headers.filter(function (item) {
    return item.name.toLowerCase() === 'content-type';
  });
  if (ct.length === 0) {
    headers.push({
      'name': 'Content-Type',
      'value': encoding.trim()
    });
    return true;
  }
  return false;
};

arc.app.db.headers.getContentType = function (headers) {
  if (typeof headers === 'string') {
    headers = arc.app.db.headers.toJSON(headers);
  }
  headers = arc.app.db.headers.filter(headers);
  var ct = headers.filter(function (item) {
    return item.name.toLowerCase() === 'content-type';
  });
  return ct.length === 0 ? null : ct[0].value;
};
'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.arc = {};

arc.app.arc.minSupportedVersion = 45;

arc.app.arc.getAppId = function (callback) {
  chrome.storage.local.get({
    'APP_ID': null
  }, function (result) {
    if (!result.APP_ID) {
      result.APP_ID = arc.app.utils.uuid();
      chrome.storage.local.set(result, function () {
        callback(result.APP_ID);
      });
    } else {
      callback(result.APP_ID);
    }
  });
};

arc.app.arc.checkCompatybility = function () {
  var version = arc.app.utils.getChromeVersion();
  var manifest = chrome.runtime.getManifest();
  var manMin = 0;

  if (manifest && manifest.minimum_chrome_version) {
    manMin = Number(manifest.minimum_chrome_version);
  }

  var supported = Math.max(manMin, arc.app.arc.minSupportedVersion);
  if (Number(version.match(/(\d+)\./)[1]) < supported) {
    window.setTimeout(function () {
      document.querySelector('#incompatibleVersionDialog').open();
    }, 2000);
  }
};
'use strict';

if (!Array.from) {
  Array.from = function () {
    var toStr = Object.prototype.toString;
    var isCallable = function isCallable(fn) {
      return typeof fn === 'function' || toStr.call(fn) === '[object Function]';
    };
    var toInteger = function toInteger(value) {
      var number = Number(value);
      if (isNaN(number)) {
        return 0;
      }
      if (number === 0 || !isFinite(number)) {
        return number;
      }
      return (number > 0 ? 1 : -1) * Math.floor(Math.abs(number));
    };
    var maxSafeInteger = Math.pow(2, 53) - 1;
    var toLength = function toLength(value) {
      var len = toInteger(value);
      return Math.min(Math.max(len, 0), maxSafeInteger);
    };

    return function from(arrayLike) {
      var C = this;

      var items = Object(arrayLike);

      if (arrayLike === null) {
        throw new TypeError('Array.from requires an array-like object - not null or undefined');
      }

      var mapFn = arguments.length > 1 ? arguments[1] : void undefined;
      var T;
      if (typeof mapFn !== 'undefined') {
        if (!isCallable(mapFn)) {
          throw new TypeError('Array.from: when provided, the second argument must be a function');
        }

        if (arguments.length > 2) {
          T = arguments[2];
        }
      }

      var len = toLength(items.length);

      var A = isCallable(C) ? Object(new C(len)) : new Array(len);

      var k = 0;

      var kValue;
      while (k < len) {
        kValue = items[k];
        if (mapFn) {
          A[k] = typeof T === 'undefined' ? mapFn(kValue, k) : mapFn.call(T, kValue, k);
        } else {
          A[k] = kValue;
        }
        k += 1;
      }

      A.length = len;

      return A;
    };
  }();
}
'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.server = arc.app.server || {};

arc.app.server.request = {};

arc.app.server.request.init = function () {
  var root = 'https://chromerestclient.appspot.com/';
  arc.app.server.request.SERVICE_URL = root + 'ext-channel';
  arc.app.server.request.PING_URL = root + 'ping/session';
  arc.app.server.request.AUTH_URL = root + 'auth';
  arc.app.server.request.ASSETS_URL = root + 'static/';
  arc.app.server.request.MESSAGES_URL = root + 'messages/';

  arc.app.server.request.SUGGESTIONS_LISTING = arc.app.server.request.SERVICE_URL + '/list/';
  arc.app.server.request.GET_IMPORT_DATA = arc.app.server.request.SERVICE_URL + '/get';
  arc.app.server.request.EXPORT_DATA = arc.app.server.request.SERVICE_URL + '/put';
};

arc.app.server.request.buildRequest = function (url, method, body) {
  var init = {
    method: method || 'GET',
    credentials: 'include',
    cache: 'no-cache'
  };
  var headers = new Headers();
  headers.append('X-Chrome-Extension', 'ChromeRestClient');
  if (body) {
    init.body = body;
    headers.append('Content-Type', 'application/x-www-form-urlencoded');
  }
  init.headers = headers;
  return fetch(url, init);
};

arc.app.server.request.pingRequest = function () {
  return arc.app.server.request.buildRequest(arc.app.server.request.PING_URL).then(function (response) {
    return response.json();
  }).catch(function (error) {
    return {
      'error': true,
      'message': 'Error to load response from server. Session state unknown. (' + error.message + ')'
    };
  });
};

arc.app.server.request.messagesRequest = function (since) {
  return arc.app.server.request.buildRequest(arc.app.server.request.MESSAGES_URL + since).then(function (response) {
    return response.json();
  }).then(function (json) {
    var data = [];
    json.forEach(function (item) {
      data.push({
        actionUrl: item.actionUrl,
        message: item.message
      });
    });
    return data;
  }).catch(function (error) {
    return {
      'error': true,
      'message': 'Can\'t get messages list from the server. (' + error.message + ')'
    };
  });
};

arc.app.server.request.importSuggestionsRequest = function (uid) {
  uid = uid || 'me';
  return arc.app.server.request.buildRequest(arc.app.server.request.SUGGESTIONS_LISTING + uid).then(function (response) {
    return response.json();
  }).catch(function (error) {
    return {
      'error': true,
      'message': 'Can\'t get messages list from the server. (' + error.message + ')'
    };
  });
};

arc.app.server.request.getImportData = function (uids) {
  var data = '';
  uids.forEach(function (uid) {
    return data += 'k%5B%5D=' + uid + '&';
  });
  data = data.substring(0, data.length - 1);
  return arc.app.server.request.buildRequest(arc.app.server.request.GET_IMPORT_DATA, 'POST', data).then(function (response) {
    return response.json();
  }).catch(function (error) {
    return {
      'error': true,
      'message': 'Can\'t get messages list from the server. (' + error.message + ')'
    };
  });
};

arc.app.server.request.init();
'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.server = arc.app.server || {};

arc.app.server.applicationUserId = null;

arc.app.server.hasSession = function (callback) {
  arc.app.server.request.pingRequest().then(function (json) {
    if (json.error) {
      return {
        'error': true,
        'message': json.error
      };
    }
    var data = {
      state: 2,
      uid: null };
    if ('hasSession' in json) {
      data.state = json.hasSession ? 1 : 0;
      if (json.hasSession) {
        data.uid = json.userId;
      }
    }
    return data;
  }).then(callback);
};

arc.app.server._authTab = null;

arc.app.server.auth = function () {
  var returnPath = '';
  if (arc.app.utils.isProdMode()) {
    returnPath = 'chrome-extension://' + chrome.runtime.id + '/auth.html#auth';
  } else {
    returnPath = 'http://127.0.0.1:8888/auth.html#auth';
  }
  var url = arc.app.server.request.AUTH_URL;
  url += '/signin?ret=';
  var regexp = /%20/g;
  url = url + encodeURIComponent(returnPath).replace(regexp, '+');
  chrome.tabs.create({
    url: url
  }, function (tab) {
    arc.app.server._authTab = tab.id;
  });
};

arc.app.server.authStateMayChanged = function (changes) {
  if (Object.keys(changes).indexOf('applogin') !== -1) {
    arc.app.server.hasSession(function (session) {
      if (window.arcGwtCallbacks && 'sessionchange' in window.arcGwtCallbacks) {
        window.arcGwtCallbacks.sessionchange(session);
      }
      if (arc.app.server._authTab) {
        chrome.tabs.remove(arc.app.server._authTab);
        arc.app.server._authTab = null;
      }
    });
  }
};

arc.app.server.getMessages = function (since, callback) {
  arc.app.server.request.messagesRequest(since).then(callback);
};

arc.app.server.getImportSuggestions = function (uid, callback) {
  arc.app.server.request.importSuggestionsRequest(uid).then(function (result) {
    callback(result);
  });
};

arc.app.server.getImportData = function (uids, callback) {
  arc.app.server.request.getImportData(uids).then(function (result) {
    callback(result);
  });
};

chrome.storage.onChanged.addListener(arc.app.server.authStateMayChanged);
'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.settings = arc.app.settings || {};

arc.app.settings.getConfig = function () {
  return new Promise(function (resolve) {
    var values = {
      'DEBUG_ENABLED': false,
      'HISTORY_ENABLED': true,
      'MAGICVARS_ENABLED': true,
      'CMH_ENABLED': true,
      'CMP_ENABLED': true
    };
    try {
      chrome.storage.sync.get(values, function (result) {
        resolve(result);
      });
    } catch (e) {
      console.error('arc.app.settings.getConfig', e);
      resolve(values);
    }
  });
};
arc.app.settings.saveConfig = function (key, value) {
  return new Promise(function (resolve) {
    var o = {};
    o[key] = value;
    chrome.storage.sync.set(o, resolve);
  });
};
arc.app.settings.observe = function (callback) {
  chrome.storage.onChanged.addListener(function (changes, area) {
    callback(changes, area);
  });
};
'use strict';

var _get = function get(object, property, receiver) { if (object === null) object = Function.prototype; var desc = Object.getOwnPropertyDescriptor(object, property); if (desc === undefined) { var parent = Object.getPrototypeOf(object); if (parent === null) { return undefined; } else { return get(parent, property, receiver); } } else if ("value" in desc) { return desc.value; } else { var getter = desc.get; if (getter === undefined) { return undefined; } return getter.call(receiver); } };

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var BaseObject = function () {
  function BaseObject() {
    _classCallCheck(this, BaseObject);
  }

  _createClass(BaseObject, [{
    key: 'assertRequiredKeys',
    value: function assertRequiredKeys(required, passed) {
      var errors = [];
      required.forEach(function (name) {
        if (!(name in passed)) {
          errors.push(name);
        }
      });
      if (errors.length > 0) {
        throw new Error('Missing parameters: ' + errors.join(', ') + '.');
      }
    }
  }]);

  return BaseObject;
}();

var RequestObject = function (_BaseObject) {
  _inherits(RequestObject, _BaseObject);

  function RequestObject(opts) {
    _classCallCheck(this, RequestObject);

    var _this = _possibleConstructorReturn(this, Object.getPrototypeOf(RequestObject).call(this));

    _get(Object.getPrototypeOf(RequestObject.prototype), 'assertRequiredKeys', _this).call(_this, ['url', 'method', 'type'], opts);

    _this._har = null;

    _this.url = opts.url;

    _this.method = opts.method;

    _this.type = opts.type ? opts.type : null;

    _this.har = opts.har ? opts.har : null;
    return _this;
  }

  _createClass(RequestObject, [{
    key: 'har',
    set: function set(har) {
      if (har instanceof HAR.Log) {
        this._har = har;
      } else {
        this._har = new HAR.Log(har);
      }
    },
    get: function get() {
      return this._har;
    }
  }]);

  return RequestObject;
}(BaseObject);

var SavedRequestObject = function (_RequestObject) {
  _inherits(SavedRequestObject, _RequestObject);

  function SavedRequestObject(opts) {
    _classCallCheck(this, SavedRequestObject);

    opts.type = 'saved';
    return _possibleConstructorReturn(this, Object.getPrototypeOf(SavedRequestObject).call(this, opts));
  }

  return SavedRequestObject;
}(RequestObject);

var HistoryRequestObject = function (_RequestObject2) {
  _inherits(HistoryRequestObject, _RequestObject2);

  function HistoryRequestObject(opts) {
    _classCallCheck(this, HistoryRequestObject);

    opts.type = 'history';
    return _possibleConstructorReturn(this, Object.getPrototypeOf(HistoryRequestObject).call(this, opts));
  }

  return HistoryRequestObject;
}(RequestObject);

var DriveObject = function (_BaseObject2) {
  _inherits(DriveObject, _BaseObject2);

  function DriveObject(opts) {
    _classCallCheck(this, DriveObject);

    var _this4 = _possibleConstructorReturn(this, Object.getPrototypeOf(DriveObject).call(this));

    _get(Object.getPrototypeOf(DriveObject.prototype), 'assertRequiredKeys', _this4).call(_this4, ['driveId', 'requestId'], opts);

    _this4.driveId = opts.driveId;

    _this4.requestId = opts.requestId;
    return _this4;
  }

  return DriveObject;
}(BaseObject);

var ServerExportedObject = function (_BaseObject3) {
  _inherits(ServerExportedObject, _BaseObject3);

  function ServerExportedObject(opts) {
    _classCallCheck(this, ServerExportedObject);

    var _this5 = _possibleConstructorReturn(this, Object.getPrototypeOf(ServerExportedObject).call(this));

    _get(Object.getPrototypeOf(ServerExportedObject.prototype), 'assertRequiredKeys', _this5).call(_this5, ['serverId', 'requestId'], opts);

    _this5.serverId = opts.serverId;

    _this5.requestId = opts.requestId;
    return _this5;
  }

  return ServerExportedObject;
}(BaseObject);

var UrlObject = function (_BaseObject4) {
  _inherits(UrlObject, _BaseObject4);

  function UrlObject(opts) {
    _classCallCheck(this, UrlObject);

    var _this6 = _possibleConstructorReturn(this, Object.getPrototypeOf(UrlObject).call(this));

    _get(Object.getPrototypeOf(UrlObject.prototype), 'assertRequiredKeys', _this6).call(_this6, ['url'], opts);

    _this6.url = opts.url;

    _this6.time = opts.time;
    return _this6;
  }

  return UrlObject;
}(BaseObject);

var HistoryUrlObject = function (_UrlObject) {
  _inherits(HistoryUrlObject, _UrlObject);

  function HistoryUrlObject(opts) {
    _classCallCheck(this, HistoryUrlObject);

    return _possibleConstructorReturn(this, Object.getPrototypeOf(HistoryUrlObject).call(this, opts));
  }

  return HistoryUrlObject;
}(UrlObject);

var HistorySocketObject = function (_UrlObject2) {
  _inherits(HistorySocketObject, _UrlObject2);

  function HistorySocketObject(opts) {
    _classCallCheck(this, HistorySocketObject);

    return _possibleConstructorReturn(this, Object.getPrototypeOf(HistorySocketObject).call(this, opts));
  }

  return HistorySocketObject;
}(UrlObject);

var OrderedList = function (_BaseObject5) {
  _inherits(OrderedList, _BaseObject5);

  function OrderedList(opts) {
    _classCallCheck(this, OrderedList);

    var _this9 = _possibleConstructorReturn(this, Object.getPrototypeOf(OrderedList).call(this));

    _this9.order = opts.order || 0;
    return _this9;
  }

  _createClass(OrderedList, [{
    key: 'changeOrder',
    value: function changeOrder(dir, change, moved, movedOrder) {
      if (dir === 'up') {
        if (this.order === 0) {
          throw new Error('Can\'t move list element below 0 in this direction.');
        }
        this.moveUp(change, moved, movedOrder);
      } else {
        this.moveDown(change, moved, movedOrder);
      }
    }
  }, {
    key: 'moveUp',
    value: function moveUp(change, moved, movedOrder) {
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
  }, {
    key: 'moveDown',
    value: function moveDown(change, moved, movedOrder) {
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
  }]);

  return OrderedList;
}(BaseObject);

var ProjectObject = function (_OrderedList) {
  _inherits(ProjectObject, _OrderedList);

  function ProjectObject(opts) {
    _classCallCheck(this, ProjectObject);

    var _this10 = _possibleConstructorReturn(this, Object.getPrototypeOf(ProjectObject).call(this, opts));

    if (!opts.requestIds) {
      opts.requestIds = [];
    }
    if (!(opts.requestIds instanceof Array)) {
      throw new Error('`requestIds` property must be an array of ids of request objects');
    }

    _this10.requestIds = opts.requestIds;

    _this10.name = opts.name;

    _this10.created = opts.time ? new Date(opts.time) : opts.created ? opts.created : undefined;
    return _this10;
  }

  _createClass(ProjectObject, [{
    key: 'addRequest',
    value: function addRequest(requestIds) {
      if (!requestIds) {
        throw new Error('Request ID must be set.');
      }
      this.requestIds.push(requestIds);
    }
  }]);

  return ProjectObject;
}(OrderedList);
'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.utils = {};

arc.app.utils.uuid = function () {
  var lut = [];
  for (var i = 0; i < 256; i++) {
    lut[i] = (i < 16 ? '0' : '') + i.toString(16);
  }
  return function () {
    var d0 = Math.random() * 0xffffffff | 0;
    var d1 = Math.random() * 0xffffffff | 0;
    var d2 = Math.random() * 0xffffffff | 0;
    var d3 = Math.random() * 0xffffffff | 0;
    return lut[d0 & 0xff] + lut[d0 >> 8 & 0xff] + lut[d0 >> 16 & 0xff] + lut[d0 >> 24 & 0xff] + '-' + lut[d1 & 0xff] + lut[d1 >> 8 & 0xff] + '-' + lut[d1 >> 16 & 0x0f | 0x40] + lut[d1 >> 24 & 0xff] + '-' + lut[d2 & 0x3f | 0x80] + lut[d2 >> 8 & 0xff] + '-' + lut[d2 >> 16 & 0xff] + lut[d2 >> 24 & 0xff] + lut[d3 & 0xff] + lut[d3 >> 8 & 0xff] + lut[d3 >> 16 & 0xff] + lut[d3 >> 24 & 0xff];
  };
};

arc.app.utils.isProdMode = function () {
  return location.hostname !== '127.0.0.1';
};

arc.app.utils.autoLink = function (input) {
  var r = new RegExp('(https?:\\/\\/([^" >]*))', 'gim');
  return input.replace(r, '<a target="_blank" class="auto-link" href="$1">$1</a>');
};

arc.app.utils.encodeHtml = function (input) {
  if (typeof input !== 'string') {
    return input;
  }
  return input.replace(/</g, '&lt;').replace(/>/g, '&gt;');
};

arc.app.utils.getChromeVersion = function () {
  var raw = navigator.userAgent.match(/Chrom[e|ium]\/([0-9\.]+)/);
  return raw ? raw[1] : '(not set)';
};
'use strict';

var arc = arc || {};

arc.app = arc.app || {};

arc.app.xhr = arc.app.xhr || {};

arc.app.xhr.requests = new Map();

arc.app.xhr.create = function (har) {
  if (!(har && har.log && har.log.entries && har.log.entries.length > 0)) {
    throw new Error('The HAR object does not contain request information.');
  }

  var size = arc.app.xhr.requests.size();
  size++;
  arc.app.xhr.requests.set(size, har);
  return size;
};

arc.app.xhr.run = function (id) {
  return new Promise(function (resolve, reject) {
    arc.app.xhr._getRequestInfo(id).catch(function (error) {
      reject({
        'message': error.message
      });
    }).then(arc.app.xhr._createRequest).then(function (request) {
      return fetch(request);
    }).catch(function (error) {
      reject({
        'message': error.message
      });
    }).then(arc.app.xhr._readResponse).then(function (responseHar) {
      var har = arc.app.xhr.requests.get(id);
      arc.app.xhr.requests.delete(id);
      var refId = har.log.pages[har.log.pages.length - 1].id;
      for (var i = 0, len = har.log.entries.length; i < len; i++) {
        if (har.log.entries[i].pageref === refId && !har.log.entries[i].response) {
          har.log.entries[i].response = responseHar;
          break;
        }
      }
      resolve(har);
    });
  });
};

arc.app.xhr._getRequestInfo = function (id) {
  return new Promise(function (resolve, reject) {
    var har = arc.app.xhr.requests.get(id);
    if (!har) {
      reject({
        'message': 'There were no request object for given ID.'
      });
      return;
    }
    var refId = har.log.pages[har.log.pages.length - 1].id;
    if (!refId) {
      reject({
        'message': 'HAR object do not contain information about the request.'
      });
      return;
    }
    var filtered = har.log.entries.filter(function (item) {
      return item.pageref === refId;
    });
    if (!filtered.length) {
      reject({
        'message': 'HAR object do not contain information about the request.'
      });
      return;
    }
    resolve(filtered[0].request);
  });
};

arc.app.xhr._createRequest = function (request) {
  var headers = new Headers();
  var method = request.method || 'GET';
  if (request.headers && request.headers.length) {
    request.headers.forEach(function (header) {
      headers.append(header.name, header.value);
    });
  }
  var init = {
    'method': method,
    'cache': 'no-cache'
  };
  if (['GET', 'HEAD'].indexOf(method) === -1 && request.postData) {
    var post = request.postData;
    if (post.params) {
      var fd = FormData();
      post.params.forEach(function (item) {
        if (item.fileName) {
          fd.append(item.name, item.value, item.fileName);
        } else {
          fd.append(item.name, item.value);
        }
      });
      init.body = fd;
      headers.set(post.mimeType || 'multipart/form-data');
    } else if (post.text) {
      init.body = post.text;
      headers.set(post.mimeType || 'application/x-www-form-urlencoded');
    }
  }
  init.headers = headers;
  return new Request(request.url, init);
};

arc.app.xhr._readResponse = function (response) {
  return new Promise(function (resolve, reject) {
    var headers = [];
    var headersStr = '';
    var contentType = null;
    var _iteratorNormalCompletion = true;
    var _didIteratorError = false;
    var _iteratorError = undefined;

    try {
      for (var _iterator = response.headers.entries()[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
        var pair = _step.value;

        headers.push({
          'name': pair[0],
          'value': pair[1]
        });
        headersStr += pair[0] + ': ' + pair[1] + '\r\n';
        if (pair[0].toLowerCase() === 'content-type') {
          contentType = pair[1].toLowerCase();
        }
      }
    } catch (err) {
      _didIteratorError = true;
      _iteratorError = err;
    } finally {
      try {
        if (!_iteratorNormalCompletion && _iterator.return) {
          _iterator.return();
        }
      } finally {
        if (_didIteratorError) {
          throw _iteratorError;
        }
      }
    }

    var har = {
      'status': response.status,
      'statusText': response.statusText,
      'httpVersion': 'HTTP/1.1',
      'cookies': [],
      'headers': headers,
      'content': {
        'size': -1,
        'compression': 0,
        'mimeType': contentType,
        'text': ''
      },
      'redirectURL': '',
      'headersSize': arc.app.xhr.byteLength(headersStr),
      'bodySize': -1
    };
    if (response.type === 'error') {
      reject({
        'har': har,
        'message': 'Network error'
      });
    } else if (response.ok && contentType) {
      if (contentType.indexOf('image') !== -1) {
        response.blob().then(function (blob) {
          har.content.size = blob.size;
          var reader = new window.FileReader();
          reader.onloadend = function () {
            har.content.text = reader.result;
            resolve(har);
          };
          reader.onerror = function (e) {
            reject({
              'har': har,
              'message': e.message
            });
          };
          reader.readAsDataURL(blob);
        });
      } else {
        response.text().then(function (text) {
          var input = Unibabel.strToUtf8Arr(text);
          var b64 = Unibabel.arrToBase64(input);
          har.content.text = btoa(b64);
          har.content.size = arc.app.xhr.byteLength(text);
          resolve(har);
        });
      }
    } else {
      resolve(har);
    }
  });
};

arc.app.xhr.byteLength = function (str) {
  var s = str.length;
  for (var i = str.length - 1; i >= 0; i--) {
    var code = str.charCodeAt(i);
    if (code > 0x7f && code <= 0x7ff) {
      s++;
    } else if (code > 0x7ff && code <= 0xffff) {
      s += 2;
    }
    if (code >= 0xDC00 && code <= 0xDFFF) {
      i--;
    }
  }
  return s;
};