
    Polymer({
        is: 'arc-menu',
        properties: {
            route: String,
            baseUrl: String,
            _historyObserver: {
                type: Function,
                value: function() {
                    return this._onStorageChange.bind(this);
                }
            },
            projects: Array,
            noHistory: {
                type: Boolean,
                value: false
            }
        },
        ready: function() {
            try {
                this._observeHistoryEnabled();
                this._updateHistoryStatus();
            } catch (e) {
                console.error('Error occurred constructing the arc-menu', e);
                ga('legacy.send', 'exception', {
                    'exDescription': 'arc-menu::ready::' + e.message,
                    'exFatal': false
                });
            }
        },
        attached: function() {
            //this._restoreProjects();
            console.info('arc-menu has been attached');
        },
        
        _itemTap: function(e) {
            e = Polymer.dom(e);
            if (e.rootTarget.dataset.place) {
                page(e.rootTarget.dataset.place);
            }
        },
        _restoreProjects: function() {
            try {
                arc.app.db.websql.listProjects()
                    .then(function(list) {
                        this.projects = list;
                    }.bind(this));
            } catch (e) {
                console.error('Error setting up storage listener', e);
                ga('legacy.send', 'exception', {
                    'exDescription': 'arc-menu::_restoreProjects::' + e.message,
                    'exFatal': false
                });
            }
        },
        _observeHistoryEnabled: function() {
            try {
                chrome.storage.onChanged.addListener(this._historyObserver);
            } catch (e) {
                console.error('Error setting up storage listener', e);
                ga('legacy.send', 'exception', {
                    'exDescription': 'arc-menu::_observeHistoryEnabled::' + e.message,
                    'exFatal': false
                });
            }
        },
        computeSort: function(projects) {
            return function(a, b) {
                if (a.name > b.name) {
                    return 1;
                }
                if (a.name < b.name) {
                    return -1;
                }
                if (a.name === b.name) {
                    return 0;
                }
            };
        },
        _computeEnpointParameter: function(id) {
            return this.baseUrl + 'project/' + id;
        },
        /** 
         * Update project name in the UI.
         *
         * @param {Number} projectId A project id from the database
         * @param {String} projectName Project new name
         */
        updateProjectName: function(projectId, projectName) {
            if (this.project === null) {
                console.warn('Trying to update a project name when project list is empty. ' +
                    'Try insert new project first.');
                return;
            }
            var context = this;
            this.projects.forEach(function(project, i) {
                if (project.id === projectId) {
                    context.set('projects.' + i + '.name', projectName);
                }
            });
        },
        /**
         * Add newly created project to the list.
         *
         * @param {Number} projectId Database id for the project
         */
        appendProject: function(projectId) {
            arc.app.db.websql.getProject(projectId)
                .then(function(project) {
                    if (project === null) {
                        console.warn('No project found for given ID ', projectId);
                        return;
                    }
                    this.push('projects', project[0]);
                }.bind(this));
        },
        /**
         * Remove project from the UI.
         */
        removeProject: function(projectId) {
            if (this.project === null) {
                console.warn('Trying to remove a project when project list is empty. ' +
                    'Try insert new project first.');
                return;
            }
            var context = this;
            this.projects.forEach(function(project, i) {
                if (project.id === projectId) {
                    context.splice('projects', i, 1);
                }
            });
        },

        _updateHistoryStatus: function() {
            try {
                chrome.storage.sync.get({
                    HISTORY_ENABLED: true
                }, function(result) {
                    if (!result.HISTORY_ENABLED) {
                        this.noHistory = true;
                    } else {
                        this.noHistory = false;
                    }
                });
            } catch (e) {
                console.error('Error setting up storage listener', e);
                ga('legacy.send', 'exception', {
                    'exDescription': 'arc-menu::_updateHistoryStatus::' + e.message,
                    'exFatal': false
                });
            }
        },

        _onStorageChange: function(change) {
            var keys = Object.keys(change);
            if (keys.indexOf('HISTORY_ENABLED') !== -1) {
                if (!change.HISTORY_ENABLED.newValue) {
                    this.noHistory = true;
                } else {
                    this.noHistory = false;
                }
            }
        }
    });
    