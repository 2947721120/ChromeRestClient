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
/* global console */
/**
 * Advanced Rest Client namespace
 */
var arc = arc || {};
/**
 * ARC app's namespace
 */
arc.app = arc.app || {};
/**
 * A namespace for app database services
 */
arc.app.db = arc.app.db || {};
/**
 * A namespace for WebSQL store
 */
arc.app.db.websql = {};
/**
 * A handler to current connection t
o the database.
 * @type IDBDatabase
 */
arc.app.db.websql._db = null;
/**
 * Current database schema version.
 * @type Number
 */
arc.app.db.websql._dbVersion = '';
/**
 * Generic error handler.
 *
 * @param {Error} e
 */
arc.app.db.websql.onerror = function(e) {
  console.error('app::db:error');
  console.log(e.message);
};
/**
 * Open the database.
 *
 * @returns {Promise} The promise when ready.
 */
arc.app.db.websql.open = function() {
  return new Promise(function(resolve, reject) {
    if (arc.app.db.websql._db) {
      resolve(arc.app.db.websql._db);
      return;
    }
    arc.app.db.websql._db = openDatabase('restClient', arc.app.db.websql._dbVersion,
      'Rest service database', 10000000);
    arc.app.db.websql._dbUpgrade(arc.app.db.websql._db)
      .then(function() {
        resolve(arc.app.db.websql._db);
      })
      .catch(function(cause) {
        reject(cause);
      });
  });
};
/**
 * Called when database version change.
 * 
 * This function will create new database structure.
 *
 * @param {Database} db
 * @returns {undefined}
 */
arc.app.db.websql._dbUpgrade = function(db) {
  return new Promise(function(resolve, reject) {
    db.transaction(function(tx) {
      // exported to the app's server references
      let sql = 'CREATE TABLE IF NOT EXISTS exported (' +
        'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' +
        'reference_id INTEGER NOT NULL, ' +
        'gaeKey TEXT, ' +
        'type TEXT default \'form\')';
      tx.executeSql(sql, []);

      // list of user defined form encodings
      sql = 'CREATE TABLE IF NOT EXISTS form_encoding (' +
        'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' +
        'encoding TEXT NOT NULL)';
      tx.executeSql(sql, []);

      // list HTTP headers
      sql = 'CREATE TABLE IF NOT EXISTS headers (' +
        'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' +
        'name TEXT NOT NULL, desc TEXT, example TEXT, type TEXT)';
      tx.executeSql(sql, []);

      //  requests history table
      sql = 'CREATE TABLE IF NOT EXISTS history (' +
        'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' +
        'url TEXT NOT NULL, method TEXT NOT NULL, ' +
        'encoding TEXT NULL, headers TEXT NULL, ' +
        'payload TEXT NULL, time INTEGER)';
      tx.executeSql(sql, []);

      //  projects definition table
      sql = 'CREATE TABLE IF NOT EXISTS projects (' +
        'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' +
        'name TEXT NOT NULL, ' +
        'time INTEGER)';
      tx.executeSql(sql, []);

      // Saved requests table
      sql = 'CREATE TABLE IF NOT EXISTS request_data (' +
        'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' +
        'project INTEGER DEFAULT 0, name TEXT NOT NULL, ' +
        'url TEXT NOT NULL, method TEXT NOT NULL, ' +
        'encoding TEXT NULL, headers TEXT NULL, ' +
        'payload TEXT NULL, skipProtocol INTEGER DEFAULT 0, ' +
        'skipServer INTEGER DEFAULT 0, ' +
        'skipParams INTEGER DEFAULT 0, ' +
        'skipHistory INTEGER DEFAULT 0, ' +
        'skipMethod INTEGER DEFAULT 0, ' +
        'skipPayload INTEGER DEFAULT 0, ' +
        'skipHeaders INTEGER DEFAULT 0, ' +
        'skipPath INTEGER DEFAULT 0, time INTEGER)';
      tx.executeSql(sql, []);

      // Status codes definitions
      sql = 'CREATE TABLE IF NOT EXISTS statuses (' +
        'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' +
        'code INTEGER NOT NULL, label TEXT, desc TEXT)';
      tx.executeSql(sql, []);

      // Used URL (for autocomplete) table
      sql = 'CREATE TABLE IF NOT EXISTS urls (' +
        'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' +
        'time INTEGER, ' +
        'url TEXT NOT NULL)';
      tx.executeSql(sql, []);

      // Web socket data table
      sql = 'CREATE TABLE IF NOT EXISTS websocket_data (' +
        'id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ' +
        'url TEXT NOT NULL, ' +
        'time INTEGER)';
      tx.executeSql(sql, []);
    }, function(tx, error) {
      reject(error);
    }, function() {
      resolve();
    });
  });
};
/**
 * Insert status codes definitions into database.
 * This function should be called once, in background page, when the app is installed for 
 * the first time.
 *
 * @param {Array} codesArray A list of objects to be inserted into the database.
 *                StatusCode: {
 *                  'label' (String) - code label
 *                  'key' (Number) - Status code
 *                  'desc' (String) - a description of the status code.
 *                }
 */
arc.app.db.websql.insertStatusCodes = function(codesArray) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          codesArray.forEach(function(item) {
            let sql = 'INSERT INTO statuses (code,label,desc) VALUES (?,?,?)';
            tx.executeSql(sql, [item.key, item.label, item.desc]);
          });
        }, function(tx, error) {
          reject(error);
        }, function() {
          resolve();
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Insert HTTP headers definitions into database.
 * This function should be called once, in background page, when the app is installed for 
 * the first time.
 *
 * @param {Array} headers A list of objects to be inserted into the database.
 *      StatusCode: {
 *        'key' (String) - Name of the header
 *        'example' (String) - Header usage example
 *        'desc' (String) - a description of the header
 *        'type' (String) - either `request` or `response` determining where the header may appear.
 *      }
 */
arc.app.db.websql.insertHeadersDefinitions = function(headers) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          headers.forEach(function(item) {
            let sql = 'INSERT INTO headers (name,desc,example,type) VALUES (?,?,?,?)';
            tx.executeSql(sql, [item.key, item.desc, item.example, item.type]);
          });
        }, function(tx, error) {
          reject(error);
        }, function() {
          resolve();
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Get status code definition by it's code
 */
arc.app.db.websql.getStatusCode = function(code) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'SELECT * FROM statuses WHERE code = ?';
          tx.executeSql(sql, [code], function(tx, result) {
            if (result.rows.length === 0) {
              resolve([]);
            } else {
              resolve(result.rows.item(0));
            }
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Get header from the storage by it's name and type
 */
arc.app.db.websql.getHeaderByName = function(name, type) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'SELECT * FROM headers WHERE name LIKE (?) AND type=?';
          tx.executeSql(sql, [name, type], function(tx, result) {
            if (result.rows.length === 0) {
              resolve([]);
            } else {
              resolve(result.rows.item(0));
            }
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Add new URL history value to the `urls` table.
 */
arc.app.db.websql.addUrlHistory = function(url, time) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'INSERT INTO urls (url,time) VALUES (?,?)';
          tx.executeSql(sql, [url, time], function(tx, result) {
            resolve(result.insertId);
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Update a value in a `urls` table.
 */
arc.app.db.websql.updateUrlHistory = function(id, time) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'UPDATE urls SET time = ? WHERE ID = ?';
          tx.executeSql(sql, [time, id], function(tx, result) {
            resolve();
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Get url values from the `urls` table matching `query`.
 */
arc.app.db.websql.getHistoryUrls = function(query) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'SELECT url FROM urls WHERE url LIKE ? ORDER BY time';
          tx.executeSql(sql, [query], function(tx, result) {
            if (result.rows.length === 0) {
              resolve([]);
            } else {
              resolve(Array.from(result.rows));
            }
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Add new project data to the `projects` table.
 */
arc.app.db.websql.addProject = function(name, time) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'INSERT INTO projects (name, time) VALUES (?,?)';
          tx.executeSql(sql, [name, time], function(tx, result) {
            resolve(result.insertId);
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Insert list of projects data into the `projects` table.
 *
 * @param {Array} projectsArray A list of objects to be inserted into the database.
 *      ProjectObject: {
 *        'name' (String) - Project name
 *        'time' (Number) - Creation time.
 *      }
 */
arc.app.db.websql.importProjects = function(projectsArray) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          projectsArray.forEach(function(item) {
            let sql = 'INSERT INTO projects (name, time) VALUES (?,?)';
            tx.executeSql(sql, [item.name, item.time]);
          });
        }, function(tx, error) {
          reject(error);
        }, function() {
          resolve();
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
arc.app.db.websql.importProjects2 = function(projectsArray) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {

        let size = projectsArray.length;
        let left = projectsArray.length;
        let results = [];
        let callOnEnd = function() {
          if (left <= 0) {
            resolve(results);
          }
        };
        let success = function(result) {
          left--;
          results.add(result.insertId);
          callOnEnd();
        };
        let error = function() {
          left--;
          callOnEnd();
        };

        db.transaction(function(tx) {
          for (let i = 0; i < size; i++) {
            let item = projectsArray[i];
            let sql = 'INSERT INTO projects (name, time) VALUES (?,?)';
            tx.executeSql(sql, [item.name, item.time], success, error);
          }
        }, function(tx, error) {
          reject(error);
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Update project data in `projects` table.
 */
arc.app.db.websql.updateProject = function(name, time, id) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'UPDATE projects SET name = ?, time = ? WHERE ID = ?';
          tx.executeSql(sql, [name, time, id], function() {
            resolve();
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * List entries from the `projects` table.
 * This function will result null if projects table is empty.
 */
arc.app.db.websql.listProjects = function() {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'SELECT * FROM projects WHERE 1';
          tx.executeSql(sql, [], function(tx, result) {
            if (result.rows.length === 0) {
              resolve([]);
            } else {
              resolve(Array.from(result.rows));
            }
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Get project data from the store.
 * This function will result null if entry for given [id] is not found.
 */
arc.app.db.websql.getProject = function(id) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'SELECT * FROM projects WHERE ID = ?';
          tx.executeSql(sql, [id], function(tx, result) {
            if (result.rows.length === 0) {
              resolve([]);
            } else {
              resolve(Array.from(result.rows)[0]);
            }
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Get project data from the store.
 * This function will result null if entry for given [id] is not found.
 */
arc.app.db.websql.deleteProject = function(id) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'DELETE FROM projects WHERE ID = ?';
          tx.executeSql(sql, [id], function() {
            resolve();
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
arc.app.db.websql.insertHistoryObject = function(data) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'INSERT INTO history (url, method, headers, payload, time) ' +
            'VALUES (?,?,?,?,?)';
          tx.executeSql(sql, [data.url, data.method, data.headers, data.payload, data.time],
            function(tx, result) {
              resolve(result.insertId);
            },
            function(tx, error) {
              reject(error);
            });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
arc.app.db.websql.getHistoryObject = function(id) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'SELECT * FROM history WHERE ID=?';
          tx.executeSql(sql, [id], function(tx, result) {
            if (result.rows.length === 0) {
              resolve(null);
            } else {
              resolve(Array.from(result.rows)[0]);
            }
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Get all data from history table.
 */
arc.app.db.websql.getAllHistoryObjects = function() {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'SELECT * FROM history WHERE 1';
          tx.executeSql(sql, [], function(tx, result) {
            if (result.rows.length === 0) {
              resolve([]);
            } else {
              resolve(Array.from(result.rows));
            }
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Delete from HistoryObject store.
 *
 * @param {Number} id The record ID.
 */
arc.app.db.websql.removeHistoryObject = function(id) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'DELETE FROM history WHERE ID=?';
          tx.executeSql(sql, [id], function() {
            resolve();
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Remove all data history store.
 */
arc.app.db.websql.truncateHistoryTable = function() {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'DELETE FROM history';
          tx.executeSql(sql, [], function() {
            resolve();
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Make a query to the history table.
 * In this function it is possible to query for url data.
 *
 * @param {Object} opts
 *  limit {Number} Number of results
 *  offset {Number} Starting point
 *  query {String} Optional, a url to query for
 */
arc.app.db.websql.queryHistoryTable = function(opts) {
  var sqlArgs = [];
  var sql = 'SELECT * FROM history WHERE ';
  if (opts.query) {
    sql += 'url LIKE ?';
    sqlArgs.push(opts.query);
  } else {
    sql += '1';
  }
  sql += ' ORDER BY time DESC  LIMIT ?, ?';
  sqlArgs.push(opts.offset);
  sqlArgs.push(opts.limit);
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          tx.executeSql(sql, sqlArgs, function(tx, result) {
            if (result.rows.length === 0) {
              resolve([]);
            } else {
              resolve(Array.from(result.rows));
            }
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Get history items by it's URL and HTTP method values.
 *
 * @param {String} url And URL to query for
 * @param {String} method A HTTP method to query for.
 */
arc.app.db.websql.getHistoryItems = function(url, method) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'SELECT * FROM history WHERE url=? AND method=? ORDER BY time DESC';
          tx.executeSql(sql, [url, method], function(tx, result) {
            if (result.rows.length === 0) {
              resolve([]);
            } else {
              resolve(Array.from(result.rows));
            }
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * Update a time of the history item.
 *
 * @param {Number} id A record database ID
 * @param {Number} time A time as the same as {Date#getTime()}
 */
arc.app.db.websql.updateHistoryTime = function(id, time) {
  return new Promise(function(resolve, reject) {
    arc.app.db.websql.open()
      .then(function(db) {
        db.transaction(function(tx) {
          let sql = 'UPDATE history SET time = ? WHERE ID = ?';
          tx.executeSql(sql, [time, id], function() {
            resolve();
          }, function(tx, error) {
            reject(error);
          });
        });
      })
      .catch(function(e) {
        reject(e);
      });
  });
};
/**
 * In dev mode there is no direct connection to the database initialized in the background page.
 * This function must be called in Development environment to initialize WebSQL.
 */
arc.app.db.websql.initDev = function() {
  if (arc.app.utils && !arc.app.utils.isProdMode()) {
    arc.app.db.websql.open()
      .then(function() {
        console.log('%cDEVMODE::Database has been initialized', 'color: #33691E');
      })
      .catch(function(e) {
        console.error('DEVMODE::Error initializing the database', e);
      });
  }
};

arc.app.db.websql.initDev();
